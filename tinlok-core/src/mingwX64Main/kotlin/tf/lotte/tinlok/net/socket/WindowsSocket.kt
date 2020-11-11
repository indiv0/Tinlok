/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.net.socket

import platform.posix.SOCKET
import platform.windows.FIONBIO
import tf.lotte.tinlok.Unsafe
import tf.lotte.tinlok.io.async.SelectionKey
import tf.lotte.tinlok.net.*
import tf.lotte.tinlok.system.BlockingResult
import tf.lotte.tinlok.system.Syscall
import tf.lotte.tinlok.util.AtomicBoolean
import tf.lotte.tinlok.util.ClosedException

/**
 * A Winsock-based socket.
 */
public class WindowsSocket<I: ConnectionInfo>
public constructor(
    override val family: AddressFamily,
    override val type: SocketType,
    override val protocol: IPProtocol,
    private val handle: SOCKET,
    private val creator: ConnectionInfoCreator<I>,
) : Socket<I> {
    /** If this socket is still open. */
    override val isOpen: AtomicBoolean = AtomicBoolean(true)

    private fun checkOpen() {
        if (!isOpen) throw ClosedException("socket is closed")
    }

    /** If this socket is non-blocking. */
    private val isNonBlocking = AtomicBoolean(false)

    /** If this socket is non-blocking. */
    @OptIn(ExperimentalUnsignedTypes::class, Unsafe::class)
    override var nonBlocking: Boolean
        get() {
            checkOpen()

            return isNonBlocking.value
        }
        set(value) {
            checkOpen()

            Syscall.ioctlsocket(handle, FIONBIO.toInt(), if (value) 1u else 0u)
            isNonBlocking.value = value
        }

    // These two need a special C wrapper because the Windows type is
    /**
     * Sets the [option] on this BSD socket to [value].
     */
    override fun <T> setOption(option: BsdSocketOption<T>, value: T) {
        TODO("Not yet implemented")
    }

    /**
     * Gets the [option] on this BSD socket.
     */
    override fun <T> getOption(option: BsdSocketOption<T>): T {
        TODO("Not yet implemented")
    }

    /**
     * Connects a socket to a remote endpoint. For blocking sockets, the [timeout] parameter
     * controls how long to wait for the connection to complete. For non-blocking sockets, the
     * parameter has no effect.
     *
     * If the socket is non-blocking, this returns if the socket was connected immediately or if it
     * requires a poll() operation to notify when connected.
     */
    @OptIn(Unsafe::class)
    override fun connect(addr: I, timeout: Int): Boolean {
        checkOpen()

        return if (nonBlocking) {
            val res = Syscall.connect(handle, addr)
            res.isSuccess
        } else {
            Syscall.__connect_timeout(handle, addr, timeout)
            true
        }
    }

    /**
     * Binds this socket to the specified [addr].
     */
    @OptIn(Unsafe::class)
    override fun bind(addr: I) {
        checkOpen()

        Syscall.bind(handle, addr)
    }

    /**
     * Marks this socket for listening with the specified [backlog].
     */
    @OptIn(Unsafe::class)
    override fun listen(backlog: Int) {
        checkOpen()

        Syscall.listen(handle, backlog)
    }

    /**
     * Accepts a new client connection, returning the newly connected [Socket]. Returns null if this
     * is a non-blocking socket and no connections are available.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    @Unsafe
    override fun accept(): Socket<I>? {
        checkOpen()

        val conn = Syscall.accept(handle)
        return if (conn.isSuccess) {
            // BlockingResult wraps the new socket value so we simply unwrap it into the ulong
            WindowsSocket(family, type, protocol, conn.count.toULong(), creator)
        } else {
            null
        }
    }

    /**
     * Receives up to [size] bytes from this socket into [buf], starting at [offset], using the
     * specified [flags].
     */
    @OptIn(Unsafe::class)
    override fun recv(buf: ByteArray, size: Int, offset: Int, flags: Int): BlockingResult {
        checkOpen()

        return Syscall.recv(handle, buf, size, offset, flags)
    }

    /**
     * Receives up to [size] bytes from this socket into [buf], starting at [offset], using the
     * specified [flags]. This returns a [RecvFrom] which wraps both the [BlockingResult] for the
     * bytes read, and the address read from. A null return is the same as a -1 BlockingResult.
     *
     * .. warning::
     *
     *     This will raise an error on connection-oriented sockets; it is designed to be used on
     *     connectionless protocols.
     */
    @OptIn(Unsafe::class)
    override fun recvfrom(buf: ByteArray, size: Int, offset: Int, flags: Int): RecvFrom<I>? {
        checkOpen()

        return Syscall.recvfrom(handle, buf, size, offset, flags, creator)
    }

    /**
     * Sends up to [size] bytes from [buf] into this socket, starting at [offset], using the
     * specified [flags].
     */
    @OptIn(Unsafe::class)
    override fun send(buf: ByteArray, size: Int, offset: Int, flags: Int): BlockingResult {
        checkOpen()

        return Syscall.send(handle, buf, size, offset, flags)
    }

    /**
     * Sends up to [size] bytes from [buf] into this socket, starting at [offset], using the
     * specified [flags], to the specified [addr].
     *
     * .. warning::
     *
     *     The ``addr`` parameter is ignored on connection-oriented sockets.
     */
    @OptIn(Unsafe::class)
    override fun sendto(
        buf: ByteArray, size: Int, offset: Int, flags: Int, addr: I,
    ): BlockingResult {
        checkOpen()

        return Syscall.sendto(handle, buf, size, offset, flags, addr)
    }

    /**
     * Shuts down this socket either at one end or both ends.
     */
    @OptIn(Unsafe::class)
    override fun shutdown(how: ShutdownOption) {
        checkOpen()

        Syscall.shutdown(handle, how)
    }

    /**
     * Gets the selection key for this selectable.
     */
    override fun key(): SelectionKey {
        checkOpen()

        TODO("Not yet implemented")
    }

    /**
     * Closes this resource.
     *
     * This method is idempotent; subsequent calls will have no effects.
     */
    @OptIn(Unsafe::class)
    override fun close() {
        checkOpen()

        Syscall.closesocket(handle)
    }

}