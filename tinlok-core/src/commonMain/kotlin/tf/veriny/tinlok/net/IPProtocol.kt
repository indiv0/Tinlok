/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.net

/**
 * Enumeration of valid IP protocols.
 */
public enum class IPProtocol(public val number: Int) {
    /** Kernel's choice */
    IPPROTO_IP(tf.veriny.tinlok.net.socket.IPPROTO_IP),

    /** ICMP protocol */
    IPPROTO_ICMP(tf.veriny.tinlok.net.socket.IPPROTO_ICMP),

    /** TCP protocol */
    IPPROTO_TCP(tf.veriny.tinlok.net.socket.IPPROTO_TCP),

    /** UDP protocol */
    IPPROTO_UDP(tf.veriny.tinlok.net.socket.IPPROTO_UDP);
}
