package com.yongjincompany.anecdote.internal

import app.cash.turbine.test
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkEnvironmentSourceTest {

    private val fixedClock = Clock { 42L }

    private class FakeRegistrar : NetworkChangeRegistrar {
        var onChange: (() -> Unit)? = null
        var unsubscribed: Boolean = false

        override fun register(onChange: () -> Unit): NetworkChangeSubscription {
            this.onChange = onChange
            return NetworkChangeSubscription { unsubscribed = true }
        }

        fun fireChange() {
            onChange?.invoke()
        }
    }

    @Test
    fun `start emits initial snapshot`() = runTest {
        val snapshot = NetworkEnvironmentSnapshot(
            vpnActive = true,
            privateDnsActive = false,
            privateDnsServer = null,
            mcc = 450,
        )
        val source = NetworkEnvironmentSource(
            reader = { snapshot },
            registrar = FakeRegistrar(),
            clock = fixedClock,
        )

        source.start()

        source.signals.test {
            val emitted = awaitItem() as AdNetworkSignal.NetworkEnvironment
            assertTrue(emitted.vpnActive)
            assertFalse(emitted.privateDnsActive)
            assertNull(emitted.privateDnsServer)
            assertEquals(450, emitted.mcc)
            assertEquals(42L, emitted.timestamp)
            assertEquals(AdNetworkSignal.NetworkEnvironment.NETWORK_ID, emitted.networkId)
        }
    }

    @Test
    fun `registrar change triggers new emission`() = runTest {
        val snapshots = ArrayDeque(
            listOf(
                NetworkEnvironmentSnapshot(false, false, null, null),
                NetworkEnvironmentSnapshot(true, true, "dns.example", 460),
            ),
        )
        val registrar = FakeRegistrar()
        val source = NetworkEnvironmentSource(
            reader = { snapshots.removeFirst() },
            registrar = registrar,
            clock = fixedClock,
        )

        source.signals.test {
            source.start()
            val first = awaitItem() as AdNetworkSignal.NetworkEnvironment
            assertFalse(first.vpnActive)

            registrar.fireChange()
            val second = awaitItem() as AdNetworkSignal.NetworkEnvironment
            assertTrue(second.vpnActive)
            assertTrue(second.privateDnsActive)
            assertEquals("dns.example", second.privateDnsServer)
            assertEquals(460, second.mcc)
        }
    }

    @Test
    fun `stop unsubscribes from registrar`() = runTest {
        val registrar = FakeRegistrar()
        val source = NetworkEnvironmentSource(
            reader = { NetworkEnvironmentSnapshot(false, false, null, null) },
            registrar = registrar,
            clock = fixedClock,
        )

        source.start()
        source.stop()

        assertTrue(registrar.unsubscribed)
    }

    @Test
    fun `double start is idempotent`() = runTest {
        val registrar = FakeRegistrar()
        var registerCount = 0
        val trackingRegistrar = object : NetworkChangeRegistrar {
            override fun register(onChange: () -> Unit): NetworkChangeSubscription {
                registerCount++
                return registrar.register(onChange)
            }
        }
        val source = NetworkEnvironmentSource(
            reader = { NetworkEnvironmentSnapshot(false, false, null, null) },
            registrar = trackingRegistrar,
            clock = fixedClock,
        )

        source.start()
        source.start()

        assertEquals(1, registerCount)
    }
}
