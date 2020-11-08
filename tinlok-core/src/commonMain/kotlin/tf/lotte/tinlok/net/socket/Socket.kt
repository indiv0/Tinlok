/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.net.socket

import tf.lotte.tinlok.util.Closeable

/**
 * Base interface for all socket classes.
 */
public interface Socket : Closeable {
    /**
     * Sets the standard socket [option] on this socket.
     *
     * Subinterfaces may add extra overloaded methods for this function.
     */
    public fun <T> setSocketOption(option: BsdSocketOption<T>, value: T)

    /**
     * Gets a standard socket [option] on this socket.
     */
    public fun <T> getSocketOption(option: BsdSocketOption<T>): T
}
