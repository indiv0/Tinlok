/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.net.udp

import tf.veriny.tinlok.net.IPProtocol
import tf.veriny.tinlok.net.SocketAddress

/**
 * A socket address for UDP sockets.
 */
public class UdpSocketAddress(
    private val connections: Set<UdpConnectionInfo>,
    override val hostname: String?,
) : SocketAddress<UdpConnectionInfo>(), Set<UdpConnectionInfo> by connections {
    public companion object {
        /**
         * Creates a new [UdpSocketAddress] from a singular [UdpConnectionInfo].
         */
        public fun of(info: UdpConnectionInfo, host: String? = null): UdpSocketAddress {
            return UdpSocketAddress(setOf(info), host ?: info.ip.toString())
        }
    }

    override val protocol: IPProtocol = IPProtocol.IPPROTO_UDP

    override fun hashCode(): Int {
        return connections.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is UdpSocketAddress) return false
        if (!connections.containsAll(other.connections)) return false

        return true
    }
}
