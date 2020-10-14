/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

@file:Suppress("RedundantVisibilityModifier")

package tf.lotte.tinlok.crypto

import kotlinx.cinterop.*
import tf.lotte.tinlok.exc.ClosedException
import tf.lotte.tinlok.interop.libmonocypher.*
import tf.lotte.tinlok.types.bytestring.ByteString
import tf.lotte.tinlok.util.Closeable

/**
 * A class that takes in data and will eventually produce a hash using the Blake2b algorithm.
 */
@OptIn(ExperimentalUnsignedTypes::class)
public actual class Blake2b internal actual constructor(key: UByteArray) : Closeable {
    public actual companion object {
        // output blake2b hash size
        public const val HASH_SIZE: Int = 64
    }

    private val arena = Arena()
    private val context: crypto_blake2b_ctx = arena.alloc<crypto_blake2b_ctx>()
    private var closed: Boolean = false

    init {
        if (key.isNotEmpty()) {
            require(key.size >= 16) { "Key must be at least 16 bytes!" }
            require(key.size <= 64) { "Key must be no larger than 64 bytes!" }

            key.usePinned {
                crypto_blake2b_general_init(
                    context.ptr, HASH_SIZE.toULong(),
                    it.addressOf(0), key.size.toULong()
                ) }
        } else {
            crypto_blake2b_general_init(context.ptr, HASH_SIZE.toULong(), null, 0UL)
        }
    }

    /**
     * Feeds some data into this object.
     */
    public actual fun feed(data: ByteString) {
        if (closed) throw ClosedException("This hasher is closed")

        // zero-element byte arrays throw on addressOf(0)
        // and obviously it wouldn't do anything.
        if (data.isEmpty()) return
        val real = data.unwrap().toUByteArray()

        real.usePinned {
            crypto_blake2b_update(context.ptr, it.addressOf(0), data.size.toULong())
        }
    }

    /**
     * Performs the Blake2b hashing algorithm over all previously consumed data and returns a
     * final hash.
     */
    public actual fun hash(): Blake2bHash {
        if (closed) throw ClosedException("This hasher is closed")

        val hash = UByteArray(HASH_SIZE)
        hash.usePinned {
            crypto_blake2b_final(context.ptr, it.addressOf(0))
        }

        close()
        return Blake2bHash(ByteString.fromUncopied(hash.toByteArray()))
    }

    public override fun close() {
        if (closed) return
        crypto_wipe(context.ptr, sizeOf<crypto_blake2b_ctx>().toULong())
        arena.clear()
        closed = true
    }

}