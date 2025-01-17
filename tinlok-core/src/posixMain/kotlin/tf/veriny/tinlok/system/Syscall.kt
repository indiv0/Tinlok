/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.system

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import tf.veriny.tinlok.Unsafe

/**
 * Namespace object for all libc bindings.
 *
 * In the common package, only a small subset of functions are exposed.
 */
public expect object Syscall {
    // Filesystem
    /**
     * Reads [size] bytes from the file [fd] to [address], returning the number of bytes written.
     *
     * This method will NOT attempt to retry.
     */
    @Unsafe
    public fun __read_file(fd: FILE, address: CPointer<ByteVar>, size: Int): BlockingResult

    /**
     * Writes [size] bytes from [address] to the file [fd], returning the number of bytes written.
     *
     * This method will NOT attempt to retry.
     */
    @Unsafe
    public fun __write_file(fd: FILE, address: CPointer<ByteVar>, size: Int): BlockingResult

    /**
     * Gets the current cursor for a file (the seek point).
     */
    @Unsafe
    public fun __get_file_cursor(fd: FILE): Long

    /**
     * Sets the current absolute cursor for a file.
     */
    public fun __set_file_cursor(fd: FILE, point: Long)

    /**
     * Closes a file.
     */
    public fun __close_file(fd: FILE)

    // requires scary pointer arithmetic!
    /**
     * Writes [size] bytes from [address] to the file [fd], returning the number of bytes written.
     *
     * This method will attempt to retry until the full [size] bytes are written. Use a
     * platform-specific method if you wish to avoid that.
     */
    @Unsafe
    public fun __write_file_with_retry(
        fd: FILE, address: CPointer<ByteVar>, size: Int,
    ): BlockingResult

    /**
     * Looks up address information.
     */
    @Unsafe
    public fun getaddrinfo(
        node: String?, service: String?,
        family: Int, type: Int, protocol: Int, flags: Int,
    ): List<AddrInfo>
}
