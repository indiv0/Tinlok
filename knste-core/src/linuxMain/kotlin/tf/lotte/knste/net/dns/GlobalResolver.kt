/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of KNSTE.
 *
 * KNSTE is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.knste.net.dns

import kotlinx.cinterop.*
import platform.posix.*
import tf.lotte.knste.net.*
import tf.lotte.knste.system.Syscall
import tf.lotte.knste.util.Unsafe
import tf.lotte.knste.util.toByteArray

@ExperimentalUnsignedTypes
public actual object GlobalResolver : AddressResolver {
    @Unsafe
    private fun unrollAddrInfo(first: addrinfo): List<SocketAddress> = try {
        // unroll addrinfos into a list so we can do safe continues
        val cAddresses = run {
            var lastInfo = first
            val infos = mutableListOf<addrinfo>()
            while (true) {
                infos.add(lastInfo)
                if (lastInfo.ai_next == null) break
                lastInfo = lastInfo.ai_next!!.pointed
            }
            infos
        }

        // now we create our own SocketAddress instances
        val addresses = ArrayList<SocketAddress>(cAddresses.size)
        for (info in cAddresses) {
            // lookup the values in our enum
            val family = AddressFamily.values()
                .find { it.number == info.ai_family } ?: continue
            val type = SocketKind.values()
                .find { it.number == info.ai_socktype } ?: continue
            val protocol = IPProtocol.values()
                .find { it.number == info.ai_protocol } ?: continue

            // addresses with a nullptr IP are skipped because ???
            val sockaddr = info.ai_addr?.pointed ?: continue
            val ip: IPAddress
            val port: Int

            when (family) {
                AddressFamily.AF_INET -> {
                    val real = sockaddr.reinterpret<sockaddr_in>()
                    val ba = real.sin_addr.s_addr.toByteArray()
                    ip = IPv4Address(ba)

                    // TODO: Replace this with pure-Kotlin functions, not ntohs
                    port = ntohs(real.sin_port).toInt()
                }
                AddressFamily.AF_INET6 -> {
                    val real = sockaddr.reinterpret<sockaddr_in6>()
                    // XX: Kotlin in6_addr has no fields!
                    val addrPtr = real.sin6_addr.arrayMemberAt<ByteVar>(0L)
                    val addr = addrPtr.readBytes(16)
                    ip = IPv6Address(addr)

                    port = ntohs(real.sin6_port).toInt()
                }
                else -> continue  // unknown or unsupported
            }

            val finalAddr = when (type) {
                SocketKind.SOCK_STREAM -> {
                    TcpSocketAddress(ip, port)
                }
                SocketKind.SOCK_DGRAM -> {
                    DatagramSocketAddress(ip, port)
                }
                else -> continue  // raw sockets
            }
            addresses.add(finalAddr)
        }

        addresses
    } finally {
        Syscall.freeaddrinfo(first.ptr)
    }

    @Suppress("NAME_SHADOWING")
    @Unsafe
    override fun getaddrinfo(
        host: String?, service: Int,
        family: AddressFamily,
        type: SocketKind, protocol: IPProtocol,
        flags: Int
    ): List<SocketAddress> {
        val strService = if (service == 0) null else service.toString()
        return memScoped {
            val firstAddr = Syscall.getaddrinfo(
                this, host, strService,
                family.number, type.number,
                protocol = protocol.number,
                flags = flags
            )

            unrollAddrInfo(firstAddr)
        }
    }
}
