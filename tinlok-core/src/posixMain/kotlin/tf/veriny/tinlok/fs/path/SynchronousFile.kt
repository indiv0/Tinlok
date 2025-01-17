/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.fs.path

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import tf.veriny.tinlok.Unsafe
import tf.veriny.tinlok.fs.SynchronousFile
import tf.veriny.tinlok.io.Buffer
import tf.veriny.tinlok.io.checkCapacityRead
import tf.veriny.tinlok.io.checkCapacityWrite
import tf.veriny.tinlok.system.FILE
import tf.veriny.tinlok.system.Syscall
import tf.veriny.tinlok.system.ensureNonBlock
import tf.veriny.tinlok.util.AtomicBoolean
import tf.veriny.tinlok.util.AtomicSafeCloseable

/**
 * Common cross-platform implementation of a synchronous file, pointing to a filesystem file.
 */
public class SynchronousFilesystemFile(
    override val path: Path,
    public val handle: FILE,
) : SynchronousFile, AtomicSafeCloseable() {
    /** If this file is still open. */
    override val isOpen: AtomicBoolean get() = _isOpen

    /**
     * Closes this file.
     *
     * This method is idempotent; subsequent calls will have no effects.
     */
    @OptIn(Unsafe::class)
    override fun closeImpl() {
        Syscall.__close_file(handle)
    }

    /**
     * Gets the current cursor position.
     */
    @OptIn(Unsafe::class)
    override fun cursor(): Long {
        checkOpen()

        return Syscall.__get_file_cursor(handle)
    }

    /**
     * Changes the current cursor position of this file.
     */
    @OptIn(Unsafe::class)
    override fun seekAbsolute(position: Long) {
        checkOpen()

        Syscall.__set_file_cursor(handle, position)
    }

    /**
     * Changes the current cursor position of this file, relative to the previous position.
     */
    @OptIn(Unsafe::class)
    override fun seekRelative(position: Long) {
        checkOpen()

        val prev = cursor()
        val next = prev + position

        Syscall.__set_file_cursor(handle, next)
    }

    /**
     * Reads no more than [size] amount of bytes into [ba], starting at [offset], returning the
     * number of bytes actually written.
     */
    @OptIn(Unsafe::class)
    override fun readInto(ba: ByteArray, size: Int, offset: Int): Int {
        checkOpen()

        val result = ba.usePinned {
            Syscall.__read_file(handle, it.addressOf(offset), size).ensureNonBlock()
        }
        return result.toInt()
    }

    /**
     * Reads [size] amount of bytes into the specified [buffer], returning the number of bytes
     * actually read.
     */
    @OptIn(Unsafe::class)
    override fun readInto(buffer: Buffer, size: Int): Int {
        checkOpen()
        // ensure we can actually write into a buffer of that size
        buffer.checkCapacityWrite(size)

        return if (!buffer.supportsAddress()) {
            val ba = ByteArray(size)
            val amount = readInto(ba, size)
            buffer.writeFrom(ba, amount, 0)
            amount
        } else {
            buffer.address(0) {
                Syscall.__read_file(handle, it, size)
            }.ensureNonBlock().toInt()
        }
    }

    /**
     * Attempts to write all [size] bytes of the ByteArray [ba] to this object, starting from
     * [offset], returning the number of bytes actually written before reaching EOF.
     *
     * This method will attempt retries to write all of the specified bytes.
     */
    @OptIn(Unsafe::class)
    override fun writeFrom(ba: ByteArray, size: Int, offset: Int): Int {
        checkOpen()

        val result = ba.usePinned {
            Syscall.__write_file_with_retry(handle, it.addressOf(offset), size).ensureNonBlock()
        }

        return result.toInt()
    }

    /**
     * Attempts to write [size] bytes of [buffer] from the cursor onwards to this object,
     * returning the number of bytes actually written before reaching EOF.
     *
     * This method will attempt retries to write all of the specified bytes.
     */
    @OptIn(Unsafe::class)
    override fun writeFrom(buffer: Buffer, size: Int): Int {
        checkOpen()
        buffer.checkCapacityRead(size)

        return if (!buffer.supportsAddress()) {
            // copy path...
            val arr = buffer.readArray(size)
            writeFrom(arr, size, 0)
        } else {
            // fast path!
            val result = buffer.address(0) {
                Syscall.__write_file_with_retry(handle, it, size)
            }.ensureNonBlock()
            buffer.cursor += result
            result.toInt()
        }
    }
}
