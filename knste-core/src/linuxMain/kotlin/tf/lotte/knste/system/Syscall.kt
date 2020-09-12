/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of KNSTE.
 *
 * KNSTE is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package tf.lotte.knste.system

import kotlinx.cinterop.*
import platform.posix.*
import tf.lotte.knste.ByteString
import tf.lotte.knste.exc.FileAlreadyExistsException
import tf.lotte.knste.exc.FileNotFoundException
import tf.lotte.knste.exc.IOException
import tf.lotte.knste.exc.OSException
import tf.lotte.knste.net.*
import tf.lotte.knste.util.Unsafe
import tf.lotte.knste.util.toInt
import kotlin.experimental.ExperimentalTypeInference

internal typealias FD = Int

// TODO: Probably want to make some of these enums.
// TODO: Some EINVAL probably can be IllegalArgumentException, rather than IOException.

/**
 * Namespace object for all the libc calls.
 *
 * This is preferred over regular libc calls as it throws exceptions appropriately. This object
 * is very foot-gunny, but assertions are provided for basic sanity checks.
 *
 * Public extensions (ones not a direct mapping to libc) are prefixed with two underscores.
 */
@OptIn(ExperimentalTypeInference::class, ExperimentalUnsignedTypes::class)
public object Syscall {
    public const val ERROR: Int = -1
    public const val LONG_ERROR: Long = -1L

    private inline val Int.isError: Boolean get() = this == ERROR
    private inline val Long.isError: Boolean get() = this == LONG_ERROR
    private inline val CPointer<*>?.isError: Boolean get() = this == null

    // See: https://www.python.org/dev/peps/pep-0475/#rationale
    // Not completely the same, but similar justification.

    /**
     * Retries a function that uses C error handling semantics if EINTR is returned.
     */
    @Unsafe
    public inline fun retry(block: () -> Int): Int {
        while (true) {
            val result = block()
            if (result == ERROR && errno == EINTR) continue
            return result
        }
    }

    /**
     * Retry, but for Long.
     */
    @Unsafe
    @OverloadResolutionByLambdaReturnType  // magic!
    public inline fun retry(block: () -> Long): Long {
        while (true) {
            val result = block()
            if (result == LONG_ERROR && errno == EINTR) continue
            return result
        }
    }

    /**
     * Gets the current errno strerror().
     */
    @Unsafe
    public fun strerror(): String {
        return strerror(errno)?.toKString() ?: "Unknown error"
    }

    // == File opening/closing == //
    // region File opening/closing

    @Unsafe
    private fun openShared(path: String, fd: Int): Int {
        if (fd.isError) {
            throw when (errno) {
                EEXIST -> FileAlreadyExistsException(path)
                ENOENT -> FileNotFoundException(path)
                EACCES -> TODO("EACCES")
                else -> OSException(errno, message = strerror())
            }
        }

        return fd
    }

    @Unsafe
    public fun open(path: String, mode: Int): FD {
        val fd = retry { platform.posix.open(path, mode) }
        return openShared(path, fd)
    }

    /**
     * Opens a new file descriptor for the path [path].
     */
    @Unsafe
    public fun open(path: String, mode: Int, permissions: Int): FD {
        val fd = retry { platform.posix.open(path, mode, permissions) }
        return openShared(path, fd)
    }

    /**
     * Closes a file descriptor.
     */
    @Unsafe
    public fun close(fd: FD) {
        val res = platform.posix.close(fd)
        if (res.isError) {
            throw OSException(errno, message = strerror())
        }
    }

    /**
     * Closes all of the specified file descriptors. This is guaranteed to call close(2) on all,
     * but will only raise an error for the first failed fd.
     *
     * This is an extension to libc.
     */
    @Unsafe
    public fun __closeall(vararg fds: FD) {
        var isErrored = false
        var lastErrno = 0
        for (fd in fds) {
            val res = platform.posix.close(fd)
            if (res.isError && !isErrored) {
                isErrored = true
                lastErrno = errno
            }
        }

        if (isErrored) {
            throw OSException(errno = lastErrno, message = strerror())
        }
    }

    // endregion

    // == Generic Linux I/O == //
    // region Linux I/O
    /** The maximum size for most I/O functions. */
    public const val IO_MAX: Int = 0x7ffff000

    /**
     * Reads up to [count] bytes from file descriptor [fd] into the buffer [buf].
     */
    @Unsafe
    public fun read(fd: FD, buf: ByteArray, count: Int): Long {
        assert(count <= IO_MAX) { "Count is too high!" }
        assert(buf.size >= count) { "Buffer is too small!" }

        val readCount = buf.usePinned {
            retry { read(fd, it.addressOf(0), count.toULong()) }
        }

        if (readCount.isError) {
            // TODO: EAGAIN
            throw when (errno) {
                EIO -> IOException(strerror())
                else -> OSException(errno, message = strerror())
            }
        }

        return readCount
    }

    /**
     * Writes up to [size] bytes to the specified file descriptor, returning the number of bytes
     * actually written.
     *
     * This handles EINTR transparently, continuing a write if interrupted.
     */
    @Unsafe
    public fun write(fd: FD, from: ByteArray, size: Int): Long {
        assert(size <= IO_MAX) { "Count is too high!" }
        assert(from.size >= size) { "Buffer is too small!" }

        // number of bytes we have successfully written as returned from write()
        var totalWritten = 0

        // head spinny logic
        from.usePinned {
            while (true) {
                val written = write(
                    fd, it.addressOf(totalWritten),
                    (size - totalWritten).toULong()
                )

                // eintr means it didn't write anything, so we can transparently retry
                if (written.isError && errno != EINTR) {
                    throw OSException(errno, message = strerror())
                }

                // make sure we actually write all of the bytes we want to write
                // this will never be more than INT_MAX, so we're fine
                totalWritten += written.toInt()
                if (totalWritten >= size) {
                    break
                }
            }
        }

        return totalWritten.toLong()
    }

    /**
     * Writes [size] bytes from the FD [from] into the FD [to] starting from the offset [offset].
     */
    @Unsafe
    public fun sendfile(to: FD, from: FD, size: ULong, offset: Long = 0): ULong = memScoped {
        var totalWritten = offset.toULong()
        // retry loop to ensure we write ALL of the data
        while (true) {
            // i don't know if this ptr needs to be pinned, but i'm doing it to be safe.
            val longValue = totalWritten.toLong()  // off_t
            val written = longValue.usePinned {
                platform.linux.sendfile(
                    to, from,
                    ptrTo(it),
                    (size - totalWritten)
                )
            }

            if (written.isError && errno != EINTR) {
                throw OSException(errno, message = strerror())
            }

            // always safe conversion
            totalWritten += written.toULong()
            if (totalWritten >= size) {
                break
            }
        }

        return totalWritten
    }

    /**
     * Performs a seek operation on the file descriptor [fd].
     */
    @Unsafe
    public fun lseek(fd: FD, position: Long, whence: Int): Long {
        val res = platform.posix.lseek(fd, position, whence)
        if (res.isError) {
            throw OSException(errno, message = strerror())
        }

        return res
    }

    // endregion

    // == File Polling == //
    // region File Polling
    /**
     * Gets statistics about a file.
     */
    @Unsafe
    public fun stat(
        alloc: NativePlacement, path: String, followSymlinks: Boolean
    ): stat {
        val pathStat = alloc.alloc<stat>()

        val res =
            if (followSymlinks) stat(path, pathStat.ptr)
            else lstat(path, pathStat.ptr)

        if (res.isError) {
            throw when (errno) {
                ENOENT -> FileNotFoundException(path)
                else -> OSException(errno, message = strerror())
            }
        }

        return pathStat
    }

    /**
     * Gets access information about a file.
     */
    @Unsafe
    public fun access(path: String, mode: Int): Boolean {
        val result = platform.posix.access(path, mode)
        if (result.isError) {
            if (errno == EACCES) return false
            if (errno == ENOENT && mode == F_OK) return false
            else throw when (errno) {
                ENOENT -> FileNotFoundException(path)
                EROFS -> IOException("Filesystem is read-only")  // TODO: Dedicated error?
                else -> OSException(errno, message = strerror())
            }
        }

        return true
    }

    /**
     * Opens a directory for file listing.
     */
    @Suppress("FoldInitializerAndIfToElvis")
    @Unsafe
    public fun opendir(path: String): CPointer<DIR> {
        val dirfd = platform.posix.opendir(path)
        if (dirfd == null) {
            throw when (errno) {
                ENOENT -> FileNotFoundException(path)
                else -> OSException(errno, message = strerror())
            }
        }

        return dirfd
    }

    /**
     * Reads a new entry from an opened directory. Returns null on the last entry.
     */
    @Unsafe
    public fun readdir(dirfd: CValuesRef<DIR>): CPointer<dirent>? {
        return platform.posix.readdir(dirfd)
    }

    /**
     * Closes an opened directory.
     */
    @Unsafe
    public fun closedir(dirfd: CValuesRef<DIR>) {
        val res = platform.posix.closedir(dirfd)
        if (res.isError) {
            throw OSException(errno, message = strerror())
        }
    }

    // endregion

    // == Filesystem access == //
    /**
     * Creates a new filesystem directory.
     */
    @Unsafe
    public fun mkdir(path: String, mode: UInt, existOk: Boolean) {
        val result = mkdir(path, mode)
        if (result.isError) {
            if (errno == EEXIST && existOk) return
            else throw when (errno) {
                EEXIST -> FileAlreadyExistsException(path)
                ENOENT -> FileNotFoundException(path)
                else -> OSException(errno, message = strerror())
            }
        }
    }

    /**
     * Removes a filesystem directory.
     */
    @Unsafe
    public fun rmdir(path: String) {
        val result = platform.posix.rmdir(path)
        if (result.isError) {
            throw when (errno) {
                ENOENT -> FileNotFoundException(path)
                else -> OSException(errno, message = strerror())
            }
        }
    }

    /**
     * Unlinks a symbolic file or deletes a file.
     */
    @Unsafe
    public fun unlink(path: String) {
        val result = platform.posix.unlink(path)
        if (result.isError) {
            throw when (errno) {
                ENOENT -> FileNotFoundException(path)
                else -> OSException(errno, message = strerror())
            }
        }
    }

    /**
     * Fully resolves a path into an absolute path.
     */
    @Suppress("FoldInitializerAndIfToElvis")
    @Unsafe
    public fun realpath(alloc: NativePlacement, path: String): ByteString {
        val buffer = alloc.allocArray<ByteVar>(PATH_MAX)
        val res = realpath(path, buffer)
        if (res == null) {
            throw when (errno) {
                ENOENT -> FileNotFoundException(path)
                else -> OSException(errno, message = strerror())
            }
        }

        val ba = res.readZeroTerminated(PATH_MAX)
        return ByteString.fromUncopied(ba)
    }

    /**
     * Renames a file or directory.
     */
    @Unsafe
    public fun rename(from: String, to: String) {
        val res = platform.posix.rename(from, to)
        // TODO: figure out error for ENOENT...
        if (res == ERROR) {
            throw when (errno) {
                EEXIST, ENOTEMPTY -> FileAlreadyExistsException(to)
                else -> OSException(errno, message = strerror())
            }
        }
    }

    // == Networking == //
    /**
     * Calls getaddrinfo(). You are responsible for calling freeaddrinfo() afterwards.
     */
    @Unsafe
    public fun getaddrinfo(
        alloc: NativePlacement,
        node: String?, service: String?,
        family: Int, type: Int, protocol: Int, flags: Int
    ): addrinfo {
        val hints = alloc.alloc<addrinfo>()
        val res = alloc.allocPointerTo<addrinfo>()
        hints.usePinned {
            memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
        }

        hints.ai_flags = flags
        if (node == null) {
            hints.ai_flags = hints.ai_flags or AI_PASSIVE
        }
        hints.ai_socktype = type
        hints.ai_family = family
        hints.ai_protocol = protocol

        val code = getaddrinfo(node, service, hints.ptr, res.ptr)
        if (code.isError) {
            throw OSException(errno = code)
        }

        // safe (non-null) if this didn't error
        return res.pointed!!
    }

    /**
     * Frees an [addrinfo] object.
     */
    @Unsafe
    public fun freeaddrinfo(addrinfo: CPointer<addrinfo>) {
        platform.posix.freeaddrinfo(addrinfo)
    }

    /**
     * Creates a new socket and returns the file descriptor.
     */
    @Unsafe
    public fun socket(family: AddressFamily, kind: SocketKind, protocol: IPProtocol): FD {
        val sock = socket(family.number, kind.number, protocol.number)
        if (sock.isError) {
            throw when (errno) {
                EACCES -> TODO("EACCES")
                else -> OSException(errno, message = strerror())
            }
        }

        return sock
    }

    /**
     * Connects a socket over IPv6.
     */
    @Unsafe
    public fun __connect_ipv6(sock: FD, ip: IPv6Address, port: Int): Int = memScoped {
        val ipRepresentation = ip.rawRepresentation
        // runtime safety check!!
        // this is the most unsafe code in the entire library as of writing
        require(ipRepresentation.size == 16) {
            "IPv6 address was too big, refusing to clobber memory"
        }

        val struct = alloc<sockaddr_in6> {
            sin6_family = AF_INET6.toUShort()  // ?
            // have to manually write to the array contained within
            sin6_addr.arrayMemberAt<ByteVar>(0L).unsafeClobber(ipRepresentation)
            sin6_port = port.toUShort()
        }

        connect(sock, struct.ptr.reinterpret(), sizeOf<sockaddr_in6>().toUInt())
    }

    /**
     * Connects a socket over IPv4.
     */
    @Unsafe
    public fun __connect_ipv4(sock: FD, ip: IPv4Address, port: Int): Int = memScoped {
        val ipRepresentation = ip.rawRepresentation
        val struct = alloc<sockaddr_in> {
            sin_family = AF_INET.toUShort()
            sin_addr.s_addr = ipRepresentation.toInt().toUInt()
            sin_port = port.toUShort()
        }

        connect(sock, struct.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt())
    }

    /**
     * Connects a socket to an address.
     */
    @Unsafe
    public fun connect(sock: FD, address: SocketAddress) {
        val res = when (address.family) {
            AddressFamily.AF_INET6 -> {
                __connect_ipv6(sock, address.ipAddress as IPv6Address, address.port)
            }
            AddressFamily.AF_INET -> {
                __connect_ipv4(sock, address.ipAddress as IPv4Address, address.port)
            }
            else -> TODO()
        }
        if (res.isError) {
            throw when (errno) {
                EINPROGRESS -> TODO("EINPROGRESS")
                else -> OSException(errno, message = strerror())
            }
        }
    }

    // == Misc == //
    // region Misc

    /**
     * Gets a [passwd] entry for the specified uid.
     */
    @Unsafe
    public fun getpwuid_r(alloc: NativePlacement, uid: UInt): passwd? {
        val passwd = alloc.alloc<passwd>()
        val starResult = alloc.allocPointerTo<passwd>()

        var bufSize = sysconf(_SC_GETPW_R_SIZE_MAX)
        if (bufSize == -1L) bufSize = 16384
        val buffer = alloc.allocArray<ByteVar>(bufSize)

        @Suppress("UNUSED_VARIABLE")
        val res = getpwuid_r(uid, passwd.ptr, buffer, bufSize.toULong(), starResult.ptr)
        if (starResult.value == null) {
            throw OSException(errno, message = strerror())
        }

        return passwd
    }

    // endregion
}