/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of KSTE.
 *
 * KSTE is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.kste.io

/**
 * Represents a [Readable] that you can also read regular strings off of.
 */
public interface StringReadable : Readable {
    /**
     * Reads a String off of the stream, up to [bytes] count.
     */
    public fun readStringUpTo(bytes: Long): String? {
        return readUpTo(bytes)?.decode()
    }

    /**
     * Reads a String off of the stream, up to [bytes] count.
     */
    public fun readStringUpTo(bytes: Int): String? = readStringUpTo(bytes.toLong())
}

// TODO: readline()
