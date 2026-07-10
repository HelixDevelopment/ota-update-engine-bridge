/*
 * Helix OTA — end-to-end RED/GREEN proof for the AndroidUpdateEngineBridge.observeStatus()
 * defect: previously, when the platform's UpdateEngine.bind() returned false (a real,
 * non-throwing failure mode meaning the callback was never registered), the cold Flow
 * returned by observeStatus() would hang forever — never emitting a Progress, never a
 * Complete, never an error — because the boolean result was discarded
 * (see ReflectiveUpdateEngineBindResultTest.kt for the lower-level proof of the same
 * root cause inside ReflectiveUpdateEngine.bind()).
 *
 * This test drives the REAL public entrypoint a consumer actually calls
 * (`AndroidUpdateEngineBridge(context).observeStatus()`, see README.md's own usage
 * example) end-to-end on a plain host JVM: no device/emulator/Robolectric. `Context`
 * is obtained via Mockito only because it is an abstract class with dozens of
 * unrelated abstract members that `observeStatus()` never touches; the real
 * `android.os.UpdateEngine` / `UpdateEngineCallback` classes are the test fakes in
 * FakeUpdateEnginePlatform.kt (see that file's header for why this works: those
 * classes are verifiably absent from the public android.jar on this classpath).
 */
package digital.vasic.helix.ota.bridge.android

import android.content.Context
import android.os.UpdateEngine as FakeUpdateEnginePlatform
import digital.vasic.helix.ota.bridge.EngineStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.mockito.Mockito.mock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidUpdateEngineBridgeObserveStatusTest {

    @BeforeTest
    fun reset() {
        FakeUpdateEnginePlatform.reset()
    }

    @AfterTest
    fun tearDown() {
        FakeUpdateEnginePlatform.reset()
    }

    @Test
    fun observeStatus_fails_fast_instead_of_hanging_forever_when_the_platform_rejects_bind() = runTest {
        FakeUpdateEnginePlatform.bindResult = false
        val context = mock(Context::class.java)
        val bridge = AndroidUpdateEngineBridge(context, ReflectiveUpdateEngine())

        // A real caller (per README.md's usage example) does
        // `bridge.observeStatus().collect { ... }` expecting Progress/Complete
        // events. Bound at a generous timeout so a regression back to "hangs
        // forever" fails this test instead of hanging the whole suite.
        val thrown = assertFailsWith<IllegalStateException>(
            message = "observeStatus() must fail fast (not hang) when UpdateEngine.bind() returns false",
        ) {
            withTimeout(5_000) {
                bridge.observeStatus().first()
            }
        }
        assertTrue(
            thrown.message.orEmpty().contains("bind", ignoreCase = true),
            "exception should explain the bind() failure, was: ${thrown.message}",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeStatus_delivers_progress_when_the_platform_accepts_bind() = runTest {
        FakeUpdateEnginePlatform.bindResult = true
        val context = mock(Context::class.java)
        val bridge = AndroidUpdateEngineBridge(context, ReflectiveUpdateEngine())

        val eventsJob = async {
            withTimeout(5_000) { bridge.observeStatus().first() }
        }
        // Drive the test scheduler so the launched collector actually reaches
        // engine.bind() before we inspect it, then fire a real callback invocation
        // exactly like the platform would.
        runCurrent()
        val bound = FakeUpdateEnginePlatform.lastBoundCallback
        requireNotNull(bound) { "bind() should have been called with a real callback" }
        bound.onStatusUpdate(3, 0.5f)

        val event = eventsJob.await()
        assertTrue(event is EngineEvent.Progress)
        event as EngineEvent.Progress
        assertTrue(event.status is EngineStatus.Downloading)
    }
}
