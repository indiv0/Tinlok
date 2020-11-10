/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.io.async

import tf.lotte.tinlok.io.ReadWrite

/**
 * Asynchronous version of [ReadWrite].
 */
public interface AsyncReadWrite : AsyncReadable, AsyncWriteable