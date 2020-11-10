/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.net.socket

import platform.posix.O_NONBLOCK
import tf.lotte.tinlok.Unsafe
import tf.lotte.tinlok.io.FdSelectionKey
import tf.lotte.tinlok.io.OSException
import tf.lotte.tinlok.io.async.Selectable
import tf.lotte.tinlok.net.*
import tf.lotte.tinlok.system.*
import tf.lotte.tinlok.util.*

/**
 * The Linux-specific implementation of the [Socket] interface.
 */
public class LinuxSocket<I: ConnectionInfo>
public constructor(
    override val family: AddressFamily,
    override val type: SocketType,
    override val protocol: IPProtocol,
    public override val fd: FD,
    /** A connection info creator for going from sockaddr -> ConnectionInfo. */
    private val creator: ConnectionInfoCreator<I>
) : Socket<I>, Closeable, Selectable {
    public companion object {
        /**
         * Opens a new unconnected socket, using the specified [family], [type], and [protocol]
         */
        @Unsafe
        public fun <I: ConnectionInfo> open(
            family: AddressFamily, type: SocketType, protocol: IPProtocol,
            creator: ConnectionInfoCreator<I>,
        ): LinuxSocket<I> {
            val fd = Syscall.socket(family, type, protocol)
            return LinuxSocket(family, type, protocol, fd, creator)
        }
    }

    /** If the FD for this socket is still open. */
    public override val isOpen: AtomicBoolean = AtomicBoolean(true)

    private fun checkOpen() {
        if (!isOpen) throw ClosedException("underlying fd is closed")
    }

    /**
     * Closes this socket.
     *
     * This method is idempotent; subsequent calls will have no effects.
     */
    @OptIn(Unsafe::class)
    override fun close() {
        if (!isOpen.compareAndSet(true, false)) return

        Syscall.close(fd)
    }

    /** If this file descriptor is non-blocking. */
    public override var nonBlocking: Boolean
        get() {
            val flags = fcntl(FcntlParam.F_GETFL)
            return flagged(flags, O_NONBLOCK)
        }
        set(value: Boolean) {
            var flags = fcntl(FcntlParam.F_GETFL)
            flags = if (value) flags.or(O_NONBLOCK) else flags.and(O_NONBLOCK.inv())
            fcntl(FcntlParam.F_SETFL, flags)
        }

    /** Performs a File CoNTroL call with no parameters. */
    @OptIn(Unsafe::class)
    @Throws(ClosedException::class, OSException::class)
    public override fun fcntl(param: FcntlParam<*>): Int {
        checkOpen()
        return Syscall.fcntl(fd, param)
    }

    /** Performs a File CoNTroL call with one int parameter. */
    @OptIn(Unsafe::class)
    @Throws(ClosedException::class, OSException::class)
    public override fun fcntl(param: FcntlParam<Int>, arg: Int): Int {
        checkOpen()

        return Syscall.fcntl(fd, param, arg)
    }

    /**
     * Sets the [option] on this BSD socket to [value].
     */
    @OptIn(Unsafe::class)
    override fun <T> setOption(option: BsdSocketOption<T>, value: T) {
        checkOpen()

        return Syscall.setsockopt(fd, option, value)
    }

    /**
     * Gets the [option] on this BSD socket.
     */
    @OptIn(Unsafe::class)
    override fun <T> getOption(option: BsdSocketOption<T>): T {
        checkOpen()

        return Syscall.getsockopt(fd, option)
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
            val res = Syscall.connect(fd, addr)
            res.isSuccess
        } else {
            Syscall.__connect_blocking(fd, addr, timeout = timeout)
            true
        }
    }

    /**
     * Binds this socket to the specified [addr].
     */
    @OptIn(Unsafe::class)
    override fun bind(addr: I) {
        checkOpen()

        Syscall.bind(fd, addr)
    }

    /**
     * Marks this socket for listening with the specified [backlog].
     */
    @OptIn(Unsafe::class)
    override fun listen(backlog: Int) {
        checkOpen()

        Syscall.listen(fd, backlog)
    }

    /**
     * Accepts a new client connection, returning the newly connected [Socket]. Returns null if this
     * is a non-blocking socket and no connections are available.
     */
    @Unsafe
    override fun accept(): Socket<I>? {
        checkOpen()

        val sock = Syscall.accept(fd)
        if (sock.isSuccess) {
            return LinuxSocket(family, type, protocol, sock.count.toInt(), creator)
        }
        return null
    }

    /**
     * Receives up to [size] bytes from this socket into [buf], starting at [offset], using the
     * specified [flags].
     */
    @OptIn(Unsafe::class)
    override fun recv(buf: ByteArray, size: Int, offset: Int, flags: Int): BlockingResult {
        checkOpen()

        return Syscall.recv(fd, buf, size, offset, flags)
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

        return Syscall.recvfrom(fd, buf, size, offset, flags, creator)
    }

    /**
     * Sends up to [size] bytes from [buf] into this socket, starting at [offset], using the
     * specified [flags].
     */
    @OptIn(Unsafe::class)
    override fun send(buf: ByteArray, size: Int, offset: Int, flags: Int): BlockingResult {
        checkOpen()

        return Syscall.send(fd, buf, size, offset, flags)
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

        return Syscall.sendto(fd, buf, size, offset, flags, addr)
    }

    /**
     * Shuts down this socket either at one end or both ends.
     */
    @OptIn(Unsafe::class)
    override fun shutdown(how: ShutdownOption) {
        checkOpen()

        return Syscall.shutdown(fd, how)
    }

    /**
     * Gets the selection key for this selectable.
     */
    override fun key(): FdSelectionKey {
        return FdSelectionKey(fd)
    }
}
