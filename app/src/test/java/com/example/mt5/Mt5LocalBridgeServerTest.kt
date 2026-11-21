package com.example.mt5

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Mt5LocalBridgeServerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        Mt5LocalBridgeServer.stop()
    }

    @Test
    fun rejectsInvalidTokenAndAuthenticatesValidExpertAdvisor() {
        Mt5LocalBridgeServer.start(context)
        val token = Mt5LocalBridgeServer.token.value
        assertTrue(token.length >= 32)

        Socket("127.0.0.1", Mt5LocalBridgeServer.PORT).use { socket ->
            socket.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            writer.write("AUTH|invalid")
            writer.newLine()
            writer.flush()
            assertEquals("AUTH_FAILED", reader.readLine())
        }

        Socket("127.0.0.1", Mt5LocalBridgeServer.PORT).use { socket ->
            socket.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            writer.write("AUTH|$token")
            writer.newLine()
            writer.flush()
            assertEquals("AUTH_OK", reader.readLine())
            assertTrue(Mt5LocalBridgeServer.status.value.contains("authenticated"))

            writer.write("HELLO|123456|Demo-Server|Broker")
            writer.newLine()
            writer.write("ACCOUNT|123456|Demo-Server|USD|1000.0|1020.0|10.0|1010.0")
            writer.newLine()
            writer.write("POSITION|987654|EURUSD|0|0.10|1.08000|20.0")
            writer.newLine()
            writer.write("SNAPSHOT_END")
            writer.newLine()
            writer.write("ORDER_RESULT|BUY|EURUSD|OK|10009|Request completed")
            writer.newLine()
            writer.flush()

            awaitCondition { Mt5LocalBridgeServer.account.value?.login == 123456L }
            awaitCondition { Mt5LocalBridgeServer.positions.value.size == 1 }
            awaitCondition { Mt5LocalBridgeServer.lastResult.value?.contains("Request completed") == true }

            assertEquals("USD", Mt5LocalBridgeServer.account.value?.currency)
            assertEquals("EURUSD", Mt5LocalBridgeServer.positions.value.single().symbol)
            assertNotNull(Mt5LocalBridgeServer.lastResult.value)

            assertTrue(Mt5LocalBridgeServer.requestBars("EURUSD", "M5", 200))
            assertEquals("BARS|EURUSD|M5|200", reader.readLine())
        }
    }

    @Test
    fun clearsSessionSnapshotsAfterDisconnect() {
        Mt5LocalBridgeServer.start(context)
        val token = Mt5LocalBridgeServer.token.value

        Socket("127.0.0.1", Mt5LocalBridgeServer.PORT).use { socket ->
            socket.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            writer.write("AUTH|$token")
            writer.newLine()
            writer.flush()
            assertEquals("AUTH_OK", reader.readLine())
            writer.write("ACCOUNT|123456|Demo-Server|USD|1000.0|1020.0|10.0|1010.0")
            writer.newLine()
            writer.flush()
            awaitCondition { Mt5LocalBridgeServer.account.value != null }
        }

        awaitCondition {
            Mt5LocalBridgeServer.account.value == null &&
                Mt5LocalBridgeServer.status.value == "Waiting for authenticated MT5 Expert Advisor"
        }
        assertTrue(Mt5LocalBridgeServer.positions.value.isEmpty())
        assertTrue(Mt5LocalBridgeServer.bars.value.isEmpty())
        assertEquals("Waiting for authenticated MT5 Expert Advisor", Mt5LocalBridgeServer.status.value)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("Condition was not met before timeout.", condition())
    }
}
