package com.yongjincompany.anecdote.internal

import app.cash.turbine.test
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstalledAdBlockerSourceTest {

    private val fixedClock = Clock { 42L }

    @Test
    fun `start emits installed packages from reader`() = runTest {
        val candidates = setOf("com.adguard.android", "org.jak_linux.dns66", "com.absent.example")
        val reader = InstalledAdBlockerReader { incoming ->
            assertEquals(candidates, incoming)
            setOf("com.adguard.android")
        }
        val source = InstalledAdBlockerSource(reader = reader, candidates = candidates, clock = fixedClock)

        source.signals.test {
            source.start()
            val emitted = awaitItem() as AdNetworkSignal.InstalledAdBlockers
            assertEquals(setOf("com.adguard.android"), emitted.packages)
            assertEquals(42L, emitted.timestamp)
            assertEquals(AdNetworkSignal.InstalledAdBlockers.NETWORK_ID, emitted.networkId)
        }
    }

    @Test
    fun `empty installed set is emitted as empty signal`() = runTest {
        val reader = InstalledAdBlockerReader { emptySet() }
        val source = InstalledAdBlockerSource(reader = reader, clock = fixedClock)

        source.signals.test {
            source.start()
            val emitted = awaitItem() as AdNetworkSignal.InstalledAdBlockers
            assertTrue(emitted.packages.isEmpty())
        }
    }

    @Test
    fun `double start is idempotent and emits only once`() = runTest {
        var readCount = 0
        val reader = InstalledAdBlockerReader {
            readCount++
            setOf("com.adguard.android")
        }
        val source = InstalledAdBlockerSource(reader = reader, clock = fixedClock)

        source.start()
        source.start()

        assertEquals(1, readCount)
    }

    @Test
    fun `reader throwing produces empty signal instead of crashing`() = runTest {
        val reader = InstalledAdBlockerReader { error("boom") }
        val source = InstalledAdBlockerSource(reader = reader, clock = fixedClock)

        source.signals.test {
            source.start()
            val emitted = awaitItem() as AdNetworkSignal.InstalledAdBlockers
            assertTrue(emitted.packages.isEmpty())
        }
    }
}
