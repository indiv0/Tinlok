/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.net

import tf.lotte.tinlok.Unsafe
import tf.lotte.tinlok.net.dns.AddressResolver
import tf.lotte.tinlok.net.dns.GlobalResolver
import tf.lotte.tinlok.net.tcp.TcpConnectionInfo
import tf.lotte.tinlok.net.tcp.TcpSocketAddress
import tf.lotte.tinlok.net.udp.UdpConnectionInfo
import tf.lotte.tinlok.net.udp.UdpSocketAddress

/**
 * Resolves a [host] and [port] combination into a [TcpSocketAddress].
 *
 * @param resolver: The [AddressResolver] to resolve the address with.
 */
@OptIn(Unsafe::class)
public fun TcpSocketAddress.Companion.resolve(
    host: String, port: Int, resolver: AddressResolver = GlobalResolver,
): TcpSocketAddress {
    val connections = resolver.getaddrinfo(
        host = host, service = port,
        family = AddressFamily.AF_UNSPEC,
        type = SocketType.SOCK_STREAM,
        protocol = IPProtocol.IPPROTO_TCP
    ).filterIsInstance<TcpConnectionInfo>()
    return TcpSocketAddress(connections.toSet(), host)
}

/**
 * Resolves a [host] and [port] combination into a [UdpSocketAddress].
 *
 * @param resolver: The [AddressResolver] to resolve the address with.
 */
@OptIn(Unsafe::class)
public fun UdpSocketAddress.Companion.resolve(
    host: String, port: Int, resolver: AddressResolver = GlobalResolver,
): UdpSocketAddress {
    val connections = resolver.getaddrinfo(
        host = host, service = port,
        family = AddressFamily.AF_UNSPEC,
        type = SocketType.SOCK_DGRAM,
        protocol = IPProtocol.IPPROTO_UDP
    ).filterIsInstance<UdpConnectionInfo>()
    return UdpSocketAddress(connections.toSet(), host)
}
