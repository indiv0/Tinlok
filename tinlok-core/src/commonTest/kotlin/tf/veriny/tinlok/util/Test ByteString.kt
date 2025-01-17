/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

// TEMPORARY
@file:Suppress("ClassName")

package tf.veriny.tinlok.util

import kotlin.test.*

/**
 * Tests [ByteString] functionality.
 */
class `Test ByteString` {
    @Test
    fun `Test ByteString split basic`() {
        val bs = b("abc, def, ghi, jkl")
        val split = bs.split(b(", "))

        // standard smoketest
        assertEquals(split.size, 4)
        assertEquals(split[2], b("ghi"))
        // ensure last item is properly copied
        assertEquals(split.last(), b("jkl"))
    }

    @Test
    fun `Test ByteString split with leading delimiter`() {
        val bs = b(",abc,def,ghi")
        val split = bs.split(b(","))

        assertEquals(split.size, 4)
        assertTrue(split.first().isEmpty())
    }

    @Test
    fun `Test ByteString split with no matches`() {
        val bs = b("A-Set, you bet!")
        val split = bs.split(b("."))

        assertEquals(split.size, 1)
        assertEquals(split.first(), bs)
    }

    @Test
    fun `Test ByteString join`() {
        val items = listOf(b("one"), b("two"), b("three"))
        val expected = b("one two three")

        val joined = items.join(b(" "))
        assertEquals(joined, expected)

        // join with a larger delim, to ensure its all good
        val expectedTwo = b("one  two  three")
        val joinedTwo = items.join(b("  "))
        assertEquals(joinedTwo, expectedTwo)
    }

    @Test
    fun `Test ByteString hexlify`() {
        val str = b("jpN")
        val hex = str.hexlify()

        assertEquals(hex, "6a704e")
    }

    @Test
    fun `Test ByteString unhexlify`() {
        assertEquals("6a704E".unhexlify(), b("jpN"))
        // empty string
        assertEquals("".unhexlify(), b(""))

        // non-multiples of two
        assertFails { "a".unhexlify() }
        // non-hex
        assertFails { "q".unhexlify() }
    }
}
