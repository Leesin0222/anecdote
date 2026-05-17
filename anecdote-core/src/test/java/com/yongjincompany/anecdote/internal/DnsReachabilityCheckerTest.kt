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
