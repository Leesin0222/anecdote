package com.yongjincompany.anecdote.signal

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomSignalSourceTest {

    @Test
    fun `blank networkId rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomSignalSource(networkId = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomSignalSource(networkId = "   ")
        }
    }

    @Test
    fun `reserved networkIds rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomSignalSource(networkId = "probe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomSignalSource(networkId = "env")
        }
    }

    @Test
    fun `emit pushes signal to flow`() = runTest {
        val source = CustomSignalSource(networkId = "my-network")

        source.signals.test {
            val signal = AdNetworkSignal.LoadSucceeded(
                networkId = "my-network",
                timestamp = 100L,
            )
            assertTrue(source.emit(signal))
            assertEquals(signal, awaitItem())
        }
    }

    @Test
    fun `emitLoadFailed uses source networkId and provided fields`() = runTest {
        val source = CustomSignalSource(networkId = "my-network")

        source.signals.test {
            source.emitLoadFailed(
                errorType = ErrorType.NETWORK_ERROR,
                rawErrorCode = 42,
                rawErrorMessage = "timeout",
            )
            val signal = awaitItem() as AdNetworkSignal.LoadFailed
            assertEquals("my-network", signal.networkId)
            assertEquals(ErrorType.NETWORK_ERROR, signal.errorType)
            assertEquals(42, signal.rawErrorCode)
            assertEquals("timeout", signal.rawErrorMessage)
            assertTrue(signal.timestamp > 0L)
        }
    }

    @Test
    fun `emitLoadSucceeded uses source networkId`() = runTest {
        val source = CustomSignalSource(networkId = "my-network")

        source.signals.test {
            source.emitLoadSucceeded()
            val signal = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals("my-network", signal.networkId)
        }
    }

    @Test
    fun `start and stop are no-op and idempotent`() {
        val source = CustomSignalSource(networkId = "my-network")
        source.start()
        source.start()
        source.stop()
        source.stop()
        // no assertion beyond not throwing
    }

    @Test
    fun `emit rejects signal whose networkId does not match source`() {
        val source = CustomSignalSource(networkId = "my-network")
        assertThrows(IllegalArgumentException::class.java) {
            source.emit(AdNetworkSignal.LoadSucceeded(networkId = "other", timestamp = 1L))
        }
    }

    @Test
    fun `multiple emits all delivered to subscriber in order`() = runTest {
        val source = CustomSignalSource(networkId = "my-network")

        source.signals.test {
            repeat(5) { idx ->
                source.emit(AdNetworkSignal.LoadSucceeded("my-network", idx.toLong()))
            }
            repeat(5) { idx ->
                val s = awaitItem() as AdNetworkSignal.LoadSucceeded
                assertEquals(idx.toLong(), s.timestamp)
            }
        }
    }
}
