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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
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

    /*
     * HD-1 / HelixConstitution §11.4.108 (silent-outcome-loss): observeStatus()'s
     * callbackFlow { ... } discarded the ChannelResult of every trySend(...) call,
     * including the ONE terminal event that reports whether the apply outcome was a
     * SUCCESS or a FAILURE (onPayloadApplicationComplete -> EngineEvent.Complete).
     * callbackFlow's default channel is BOUNDED (capacity ~64, onBufferOverflow =
     * SUSPEND); trySend never suspends, so once a lagging collector lets the buffer
     * fill up, a further trySend simply FAILS and — pre-fix — that failure was
     * silently swallowed. The collector would then never learn whether the update
     * succeeded or failed: no Progress, no Complete, no exception, forever.
     *
     * This test reproduces that exact scenario end-to-end through the real public
     * entrypoint (`AndroidUpdateEngineBridge(context).observeStatus()`), using the
     * same real-`ReflectiveUpdateEngine`-over-`FakeUpdateEnginePlatform` harness as
     * the sibling tests in this file (see this file's own header + FakeUpdateEnginePlatform.kt
     * for why this exercises the real production reflective glue, not a mock):
     *
     *   1. Start collecting so the callback actually binds (`lastBoundCallback` is
     *      set) and consume exactly the FIRST emitted event, then deliberately
     *      PAUSE the collector (a bounded `delay`) to simulate a real lagging
     *      consumer (e.g. per-event I/O) that stops draining the channel.
     *   2. While the collector is parked, fire far more `onStatusUpdate` callbacks
     *      than the channel's bounded default capacity can hold, so the buffer is
     *      genuinely saturated (subsequent trySend calls for Progress events start
     *      failing — that data loss is a KNOWN, separate, lower-severity gap this
     *      test does not gate on).
     *   3. Fire the ONE terminal `onPayloadApplicationComplete` event this test
     *      exists to protect.
     *   4. Let virtual time advance past the pause so the collector resumes
     *      draining every currently-buffered item.
     *   5. Assert the terminal Complete event was actually delivered to the
     *      collector, OR the flow closed with an explicit error — i.e. the apply
     *      outcome was NEVER silently and permanently lost.
     *
     * RED_MODE polarity (HelixConstitution §11.4.115): reverting the fix (discarding
     * the trySend result again / removing the `.buffer(Channel.UNLIMITED)` widening)
     * makes this test FAIL; restoring the fix makes it PASS. See the fix commit for
     * the captured RED/GREEN evidence.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeStatus_never_silently_drops_the_terminal_complete_event_under_channel_backpressure() = runTest {
        FakeUpdateEnginePlatform.bindResult = true
        val context = mock(Context::class.java)
        val bridge = AndroidUpdateEngineBridge(context, ReflectiveUpdateEngine())

        val received = mutableListOf<EngineEvent>()
        var closedWithError: Throwable? = null

        val collectorJob = launch {
            try {
                bridge.observeStatus().collect { event ->
                    received += event
                    // Simulate a lagging real-world collector (e.g. per-event I/O):
                    // stop draining the channel right after the first item so every
                    // subsequent trySend has to contend with a full buffer.
                    if (received.size == 1) {
                        delay(60_000L)
                    }
                }
            } catch (e: CancellationException) {
                // NOT the signal this test looks for: this is the test's OWN
                // teardown (collectorJob.cancelAndJoin() below) unwinding the
                // collector, not a production-code close(). java.util.concurrent
                // .CancellationException (which kotlinx.coroutines.CancellationException
                // is on the JVM) extends IllegalStateException, so it MUST be
                // excluded from the catch below — conflating the two would make
                // this test pass unconditionally regardless of the real defect.
                throw e
            } catch (e: IllegalStateException) {
                closedWithError = e
            }
        }

        // Let the collector start, reach engine.bind(), and register the callback.
        runCurrent()
        val bound = FakeUpdateEnginePlatform.lastBoundCallback
        requireNotNull(bound) { "bind() should have been called with a real callback" }

        // Deliver exactly one Progress event so the collector consumes it and then
        // parks in the simulated-lag delay above.
        bound.onStatusUpdate(0, 0f)
        runCurrent()

        // Now saturate the channel: far more sends than callbackFlow's bounded
        // default capacity (~64) can hold while nobody is draining it.
        repeat(1_000) { i -> bound.onStatusUpdate(2, i / 1_000f) }

        // The event this test exists to protect: the terminal apply outcome.
        bound.onPayloadApplicationComplete(0)

        // Let the simulated lag elapse so the collector resumes draining
        // everything currently buffered (or observes the flow closing).
        advanceUntilIdle()

        // Read the outcome BEFORE tearing the collector down: cancelling
        // collectorJob below unwinds the still-suspended collect() via a
        // CancellationException, which must NEVER be mistaken for a genuine
        // production close() (see the CancellationException rethrow above).
        val deliveredComplete = received.filterIsInstance<EngineEvent.Complete>()
        try {
            assertTrue(
                deliveredComplete.isNotEmpty() || closedWithError != null,
                "the terminal Complete event must be delivered OR the flow must close " +
                    "with an error under channel backpressure — it must never be silently " +
                    "and permanently dropped. received=$received closedWithError=$closedWithError",
            )
        } finally {
            collectorJob.cancelAndJoin()
        }
    }
}
