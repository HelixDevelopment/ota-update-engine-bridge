/*
 * Helix OTA — RED/GREEN proof for the ReflectiveUpdateEngine.bind() defect.
 *
 * Defect: android.os.UpdateEngine.bind(UpdateEngineCallback) is a verified boolean-
 * returning method (see ReflectiveUpdateEngine.kt's own header comment: "boolean
 * bind(UpdateEngineCallback callback)"), but the wrapper method historically declared
 * `fun bind(callback: Any)` with NO return type, discarding the real platform's
 * result via `bindMethod.invoke(engine, callback)` with the return value thrown away.
 * A real device can legitimately have `bind()` return `false` (binder registration
 * failure) WITHOUT throwing; with the old code that failure was silently swallowed,
 * and AndroidUpdateEngineBridge.observeStatus() would hang forever (no Progress, no
 * Complete, no exception) instead of surfacing the failure to the caller.
 *
 * This test runs on a PLAIN HOST JVM (no device/emulator/Robolectric) against the
 * REAL ReflectiveUpdateEngine.kt production source, using the fakes in
 * FakeUpdateEnginePlatform.kt (see that file's header for why this is possible).
 *
 * The assertion is deliberately made via java.lang.reflect.Method.invoke on
 * ReflectiveUpdateEngine::bind itself rather than a statically Boolean-typed Kotlin
 * call site, so this exact test file COMPILES UNCHANGED whether
 * ReflectiveUpdateEngine.bind() currently returns Unit (the bug, pre-fix) or Boolean
 * (the fix) — only the RUNTIME assertion differs. That is what makes this a valid
 * RED test per HelixConstitution §11.4.1: a build break is NOT a valid RED, the
 * defect must be proven failing at runtime.
 */
package digital.vasic.helix.ota.bridge.android

import android.os.UpdateEngine as FakeUpdateEnginePlatform
import android.os.UpdateEngineCallback as FakeUpdateEngineCallback
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflectiveUpdateEngineBindResultTest {

    private class NoopCallback : FakeUpdateEngineCallback() {
        override fun onStatusUpdate(status: Int, percent: Float) = Unit
        override fun onPayloadApplicationComplete(errorCode: Int) = Unit
    }

    @BeforeTest
    fun reset() {
        FakeUpdateEnginePlatform.reset()
    }

    @AfterTest
    fun tearDown() {
        FakeUpdateEnginePlatform.reset()
    }

    /**
     * Invoke ReflectiveUpdateEngine.bind(Any) purely reflectively so this call site
     * has no static dependency on its declared return type (Unit today post-fix vs.
     * Boolean pre-fix would otherwise be a compile-time distinction, not a runtime
     * one — see the file header).
     */
    private fun invokeBindReflectively(engine: ReflectiveUpdateEngine, callback: Any): Any? {
        val bindMethod = ReflectiveUpdateEngine::class.java.getMethod("bind", Any::class.java)
        return bindMethod.invoke(engine, callback)
    }

    @Test
    fun bind_true_result_from_the_platform_is_faithfully_surfaced() {
        FakeUpdateEnginePlatform.bindResult = true
        val engine = ReflectiveUpdateEngine()
        val callback = NoopCallback()

        val result = invokeBindReflectively(engine, callback)

        // The bug: a Unit-returning wrapper makes Method.invoke return null here,
        // never a Boolean — this is the direct, runtime proof of the defect.
        assertTrue(result is Boolean, "ReflectiveUpdateEngine.bind() must return the platform's boolean result, got $result (${result?.let { it::class }})")
        assertEquals(true, result)
        assertEquals(callback, FakeUpdateEnginePlatform.lastBoundCallback, "the real callback instance must reach the platform bind()")
    }

    @Test
    fun bind_false_result_from_the_platform_is_faithfully_surfaced_not_swallowed() {
        FakeUpdateEnginePlatform.bindResult = false
        val engine = ReflectiveUpdateEngine()
        val callback = NoopCallback()

        val result = invokeBindReflectively(engine, callback)

        assertTrue(result is Boolean, "ReflectiveUpdateEngine.bind() must return the platform's boolean result, got $result (${result?.let { it::class }})")
        assertEquals(false, result, "a platform bind() failure (false) must not be silently discarded")
    }
}
