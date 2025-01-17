/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.net.tcp

import tf.veriny.tinlok.Unsafe
import tf.veriny.tinlok.net.IPProtocol
import tf.veriny.tinlok.net.socket.BooleanSocketOption
import tf.veriny.tinlok.net.socket.BsdSocketOption

/**
 * Namespace for TCP socket options.
 */
@OptIn(Unsafe::class)
public object TcpSocketOptions {
    private val IPPROTO_TCP = IPProtocol.IPPROTO_TCP.number

    /**
     * Disables Nagle's algorithm which delays small writes.
     */
    public val TCP_NODELAY: BsdSocketOption<Boolean> =
        BooleanSocketOption(
            bsdOptionValue = 1  /* TCP_NODELAY */,
            level = IPPROTO_TCP,
            name = "TCP_NODELAY"
        )

    /**
     * Enables quick acknowledgement, where ACKs are sent immediately instead of delayed.
     */
    public val TCP_QUICKACK: BsdSocketOption<Boolean> =
        BooleanSocketOption(
            bsdOptionValue = 12  /* TCP_QUICKACK */,
            level = IPPROTO_TCP,
            name = "TCP_QUICKACK"
        )
}
