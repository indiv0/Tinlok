/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.io

import tf.veriny.tinlok.Unsafe
import tf.veriny.tinlok.util.Closeable
import tf.veriny.tinlok.util.ClosingScope
import tf.veriny.tinlok.util.use
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Listens for incoming connections.
 */
public interface Listener<T : Any> : Closeable {
    /**
     * Accepts a new incoming connection.
     */
    @Unsafe
    @Throws(OSException::class)
    public fun unsafeAccept(): T
}

/**
 * Accepts a new incoming connection, and passes it to the specified [block].
 */
@OptIn(Unsafe::class, ExperimentalContracts::class)
public inline fun <T : Closeable, R> Listener<T>.accept(
    block: (T) -> R,
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val item = unsafeAccept()
    return item.use(block)
}

/**
 * Accepts a new incoming connection, and adds it to the specified [scope].
 */
@OptIn(Unsafe::class)
public fun <T : Closeable> Listener<T>.accept(
    scope: ClosingScope,
): T {
    val item = unsafeAccept()
    scope.add(item)
    return item
}
