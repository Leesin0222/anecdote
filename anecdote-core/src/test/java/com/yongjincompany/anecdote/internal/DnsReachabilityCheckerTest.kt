package com.yongjincompany.anecdote.internal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsReachabilityCheckerTest {

    @Test
    fun `isSinkholeAddress catches 0_0_0_0`() {
        val zero = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
        assertTrue(zero.isSinkholeAddress())
    }

    @Test
    fun `isSinkholeAddress catches loopback ipv4`() {
        val lo = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        assertTrue(lo.isSinkholeAddress())
    }

    @Test
    fun `isSinkholeAddress catches ipv6 unspecified and loopback`() {
        val unspec = InetAddress.getByAddress(ByteArray(16))
        assertTrue(unspec.isSinkholeAddress())

        val v6Loop = InetAddress.getByAddress(ByteArray(16).also { it[15] = 1 })
        assertTrue(v6Loop.isSinkholeAddress())
    }

    @Test
    fun `isSinkholeAddress catches private LAN addresses`() {
        assertTrue(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 0, 2)).isSinkholeAddress())
        assertTrue(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1)).isSinkholeAddress())
        assertTrue(InetAddress.getByAddress(byteArrayOf(172.toByte(), 16, 0, 1)).isSinkholeAddress())
        assertTrue(InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 1, 1)).isSinkholeAddress())
    }

    @Test
    fun `isSinkholeAddress catches ipv6 unique local`() {
        val ula = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xFD.toByte(); it[15] = 1 })
        assertTrue(ula.isSinkholeAddress())
    }

    @Test
    fun `isSinkholeAddress allows public ipv6`() {
        // 2001:4860:4860::8888
        val bytes = ByteArray(16).also {
            it[0] = 0x20; it[1] = 0x01; it[2] = 0x48; it[3] = 0x60
            it[4] = 0x48; it[5] = 0x60; it[14] = 0x88.toByte(); it[15] = 0x88.toByte()
        }
        assertFalse(InetAddress.getByAddress(bytes).isSinkholeAddress())
    }

    @Test
    fun `isSinkholeAddress allows real public ipv4`() {
        val real = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        assertFalse(real.isSinkholeAddress())
    }

    @Test
    fun `checker reports unresolved when fake lookup fails`() = runTest {
        val checker = DnsReachabilityChecker { _ ->
            DnsResolution(resolved = false, allSinkholed = false, lookupSucceeded = false)
        }
        val result = checker.resolve("example.com")
        assertFalse(result.resolved)
    }
}
