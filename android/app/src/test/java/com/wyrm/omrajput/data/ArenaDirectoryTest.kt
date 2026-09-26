package com.wyrm.omrajput.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ArenaDirectoryTest {
    @Test
    fun playOwnsGamePortUntilNativeConnectionHasClosed() = runBlocking {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 100
            val running = AtomicBoolean(true)
            val first = CountDownLatch(1)
            val second = CountDownLatch(1)
            val accepter = Thread {
                var count = 0
                while (running.get()) {
                    try {
                        server.accept().use { count++ }
                        if (count == 1) first.countDown()
                        if (count == 2) second.countDown()
                    } catch (_: SocketTimeoutException) {
                        // Let the thread observe the stop flag.
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            accepter.start()
            val arena = Arena("127.0.0.1", server.localPort, 0, 0, 0)
            try {
                // A closed picker never dials.
                assertEquals(ArenaDirectory.UNREACHABLE, ArenaDirectory.ping(arena))
                ArenaDirectory.openPicker()
                assertTrue(ArenaDirectory.ping(arena) > 0)
                assertTrue(first.await(2, TimeUnit.SECONDS))

                // Inside a minute the same arena is answered from memory, not dialled.
                assertTrue(ArenaDirectory.ping(arena) > 0)
                assertEquals(1L, second.count)

                ArenaDirectory.forgetMeasurements()
                ArenaDirectory.beginArenaPlay()
                assertEquals(ArenaDirectory.UNREACHABLE, ArenaDirectory.ping(arena))
                assertEquals(1L, second.count)

                ArenaDirectory.endArenaPlay()
                ArenaDirectory.forgetMeasurements()
                assertTrue(ArenaDirectory.ping(arena) > 0)
                assertTrue(second.await(2, TimeUnit.SECONDS))
            } finally {
                ArenaDirectory.endArenaPlay()
                ArenaDirectory.closePicker()
                ArenaDirectory.forgetMeasurements()
                running.set(false)
                accepter.join(1000)
            }
        }
    }
}
