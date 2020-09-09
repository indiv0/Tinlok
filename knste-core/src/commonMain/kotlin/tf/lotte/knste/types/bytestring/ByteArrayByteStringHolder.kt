/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of KNSTE.
 *
 * KNSTE is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.knste.types.bytestring

/**
 * Simple implementation of a [ByteStringHolder].
 */
public class ByteArrayByteStringHolder(private val ba: ByteArray) : ByteStringHolder {
    override val size: Int by ba::size

    override fun get(idx: Int): Byte = ba[idx]
    override fun contains(other: Byte): Boolean = ba.contains(other)

    override fun decode(): String {
        return ba.decodeToString()
    }

    override fun iterator(): Iterator<Byte> {
        return ba.iterator()
    }

    override fun unwrap(): ByteArray {
        return ba
    }

    override fun concatenate(other: ByteArray): ByteStringHolder {
        return ByteArrayByteStringHolder(ba + other)
    }
}
