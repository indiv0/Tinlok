/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.crypto

import external.monocypher.crypto_verify64
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/**
 * Verifies if two UByteArray's with a length of 64 are the same.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public actual fun crypto_verify64(first: UByteArray, second: UByteArray): Boolean {
    require(first.size == 64) { "First array is not 64 bytes long!" }
    require(second.size == 64) { "Second array is not 64 bytes long!" }

    first.usePinned { a ->
        second.usePinned { b ->
            return (crypto_verify64(a.addressOf(0), b.addressOf(0))) == 0
        }
    }
}
