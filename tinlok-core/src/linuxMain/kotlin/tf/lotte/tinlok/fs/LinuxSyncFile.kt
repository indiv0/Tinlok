/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.fs

import platform.posix.*
import tf.lotte.cc.Unsafe
import tf.lotte.cc.exc.ClosedException
import tf.lotte.cc.types.ByteString
import tf.lotte.tinlok.fs.StandardOpenModes.*
import tf.lotte.tinlok.fs.path.LinuxPath
import tf.lotte.tinlok.system.FD
import tf.lotte.tinlok.system.SeekWhence
import tf.lotte.tinlok.system.Syscall
import tf.lotte.tinlok.util.AtomicBoolean

/**
 * Linux implementation of a synchronous filesystem file.
 */
@OptIn(Unsafe::class)
internal class LinuxSyncFile(
    override val path: LinuxPath,
    openModes: Set<FileOpenMode>,
    permission: PosixFilePermission = PosixFilePermission.DEFAULT_FILE
) : FilesystemFile {
    /** The underlying file descriptor for this file. */
    private val fd: FD

    init {

        // initial read/write mode check
        var mode = when {
            openModes.containsAll(listOf(READ, WRITE)) -> O_RDWR
            openModes.contains(READ) -> O_RDONLY
            openModes.contains(WRITE) -> O_WRONLY
            openModes.contains(APPEND) -> O_WRONLY or O_APPEND
            else -> 0
        }

        if (openModes.contains(CREATE)) {
            mode = mode or O_CREAT
        } else if (openModes.contains(CREATE_NEW)) {
            mode = mode or O_CREAT or O_EXCL
        }

        // only pass permission bit if O_CREAT is passed
        fd = if ((mode and O_CREAT) == 0) {
            Syscall.open(path.unsafeToString(), mode)
        } else {
            Syscall.open(path.unsafeToString(), mode, permission.bit)
        }
    }

    // manually handled

    // == closing == //
    override val isOpen = AtomicBoolean(true)

    @OptIn(Unsafe::class)
    override fun close() {
        if (!isOpen.value) return

        isOpen.value = false
        Syscall.close(fd)
    }

    @OptIn(Unsafe::class)
    override fun cursor(): Long {
        if (!isOpen.value) throw ClosedException("This file is closed")
        return Syscall.lseek(fd, 0L, SeekWhence.CURRENT)
    }

    @OptIn(Unsafe::class)
    override fun seekAbsolute(position: Long) {
        if (!isOpen.value) throw ClosedException("This file is closed")
        Syscall.lseek(fd, position, SeekWhence.START)
    }

    @OptIn(Unsafe::class)
    override fun seekRelative(position: Long) {
        if (!isOpen.value) throw ClosedException("This file is closed")
        Syscall.lseek(fd, position, SeekWhence.CURRENT)
    }

    override fun readInto(buf: ByteArray, offset: Int, size: Int): Int {
        if (!isOpen.value) throw ClosedException("This file is closed")
        return Syscall.read(fd, buf, size, offset).toInt()
    }

    @OptIn(Unsafe::class)
    override fun readAll(): ByteString {
        if (!isOpen.value) throw ClosedException("This file is closed")

        // get the correct size for symlinks
        val realPath = path.toAbsolutePath(strict = true)
        val size = realPath.size() - cursor()
        if (size >= Syscall.IO_MAX) {
            throw UnsupportedOperationException("File is too big to read in one go currently")
        }

        val sizeInt = size.toInt()
        val buf = ByteArray(sizeInt)
        val cnt = Syscall.read(fd, buf, sizeInt)

        // TODO: We can avoid this mess on the sad path by reading in chunks
        return ByteString.fromUncopied(if (cnt == buf.size.toLong()) {
            buf
        } else {
            // ugh
            if (cnt > buf.size) error("What the fuck?")
            else {
                // gotta make a copy...
                // not ideal!
                buf.copyOfRange(0, cnt.toInt())
            }
        })
    }

    override fun writeAllFrom(buf: ByteArray): Int {
        if (!isOpen.value) throw ClosedException("This file is closed")
        return Syscall.write(fd, buf, buf.size).toInt()
    }
}
