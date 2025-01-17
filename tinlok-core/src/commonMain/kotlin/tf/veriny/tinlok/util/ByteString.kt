/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.util

import tf.veriny.tinlok.Unsafe


/**
 * Represents an immutable string of singular bytes.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public class ByteString
private constructor(
    private val backing: ByteStringHolder,
) : Iterable<Byte>, Collection<Byte> {
    public companion object {
        /**
         * Creates a new [ByteString] from a [ByteArray].
         */
        public fun fromByteArray(ba: ByteArray): ByteString =
            ByteString(ByteArrayByteStringHolder(ba.copyOf()))

        /**
         * Creates a new [ByteString] from a [String].
         */
        public fun fromString(string: String): ByteString {
            return ByteString(ByteArrayByteStringHolder(string.encodeToByteArray()))
        }

        /**
         * Creates a new [ByteString] from a [ByteArray], without copying it. This is unsafe as
         * it can break immutability.
         */
        @Unsafe
        public fun fromUncopied(ba: ByteArray): ByteString =
            ByteString(ByteArrayByteStringHolder(ba))

        /**
         * Creates a new [ByteString] from a raw [ByteStringHolder].
         */
        @Suppress("unused")
        public fun fromRawHolder(holder: ByteStringHolder): ByteString = ByteString(holder)
    }

    /** The size of this ByteString. */
    public override val size: Int
        get() = backing.size

    /**
     * Decodes this [ByteString] to a UTF-8 [String].
     */
    public fun decode(): String {
        return backing.decode()
    }

    /**
     * Gets a single byte from this ByteString.
     */
    public operator fun get(idx: Int): Byte {
        return backing[idx]
    }

    /**
     * Concatenates two [ByteString] instances, returning a new [ByteString].
     */
    @OptIn(Unsafe::class)
    public operator fun plus(other: ByteString): ByteString {
        return ByteString(backing.concatenate(other.unwrap()))
    }

    /**
     * Checks if this [ByteString] contains a specific byte [element].
     */
    public override operator fun contains(element: Byte): Boolean {
        return backing.contains(element)
    }

    /**
     * Checks if this ByteString contains all of the bytes in [elements].
     */
    public override fun containsAll(elements: Collection<Byte>): Boolean {
        return elements.all { this.contains(it) }
    }

    /**
     * Checks if this ByteString is empty.
     */
    public override fun isEmpty(): Boolean {
        return size == 0
    }

    /**
     * Checks if this ByteString starts with a the byte [other].
     */
    public fun startsWith(other: Byte): Boolean {
        return get(0) == other
    }

    /**
     * Checks if this ByteString starts with a different ByteString.
     */
    public fun startsWith(other: ByteString): Boolean {
        if (other.size > size) return false

        for (idx in other.indices) {
            val ours = get(idx)
            val theirs = other[idx]
            if (ours != theirs) return false
        }

        return true
    }

    /**
     * Unwraps this [ByteString], getting the underlying array. This is unsafe as it can break
     * immutability.
     */
    @Unsafe
    public fun unwrap(): ByteArray {
        return backing.unwrap()
    }

    /**
     * Unwraps this [ByteString], returning a copy of the underlying array.
     */
    public fun unwrapCopy(): ByteArray {
        return backing.unwrap().copyOf()
    }

    /**
     * Creates a new [UByteArray] from the contents of this [ByteString], copying directly from
     * the internal storage to the new array.
     */
    public fun toUByteArray(): UByteArray {
        return backing.unwrap().toUByteArray()
    }

    /**
     * Creates a new [ByteArray] with a null terminator, for passing to C functions that expect a
     * C string.
     */
    public fun toNullTerminated(): ByteArray {
        val copy = unwrapCopy()
        // already null-terminated
        if (copy.last() == 0.toByte()) return copy

        val newSize = size + 1
        val realArr = ByteArray(newSize)
        copy.copyInto(realArr)
        return realArr
    }

    override fun iterator(): Iterator<Byte> {
        return backing.iterator()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ByteString) return false
        return backing == other.backing
    }

    override fun hashCode(): Int {
        return backing.hashCode()
    }

    /**
     * Gets the escaped String for this [ByteString].
     */
    public fun escapedString(): String {
        return joinToString("") {
            if (it in 32..126) it.toChar().toString()
            else "\\x" + it.toUByte().toString(16).padStart(2, '0')
        }
    }

    override fun toString(): String {
        val s = this.joinToString("") {
            if (it in 32..126) it.toChar().toString()
            else "\\x" + it.toUByte().toString(16).padStart(2, '0')
        }
        return "b(\"$s\")"
    }
}
