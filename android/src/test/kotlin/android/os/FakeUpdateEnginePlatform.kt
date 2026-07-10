/*
 * Helix OTA — test-only stand-ins for the real, device-only, @SystemApi
 * `android.os.UpdateEngine` / `android.os.UpdateEngineCallback`.
 *
 * These are NOT part of the shipped `:android` module (main sourceSet); they exist
 * ONLY under `android/src/test/kotlin` so unit tests can run on a plain host JVM,
 * without any device/emulator/Robolectric.
 *
 * Why this works: `android.os.UpdateEngine` / `android.os.UpdateEngineCallback` are
 * `@SystemApi` and are genuinely absent from the public `android.jar` used to compile
 * and unit-test this module (verified: `unzip -l android-34/android.jar` has no
 * `UpdateEngine` entry) — that absence is exactly why `ReflectiveUpdateEngine` reaches
 * them only via `Class.forName("android.os.UpdateEngine", ...)` at runtime. Because
 * the real classes are not present anywhere on the test classpath, defining classes
 * under this exact package + name here is the ONLY definition the JVM classloader can
 * resolve — so `ReflectiveUpdateEngine`'s real reflective glue (constructor,
 * `applyPayload`, `bind`, `unbind`) runs against these fakes, completely unmodified
 * from production, with no test-only branch anywhere in the production source.
 */
package android.os

/** Test-only stand-in for the abstract `android.os.UpdateEngineCallback`. */
abstract class UpdateEngineCallback {
    abstract fun onStatusUpdate(status: Int, percent: Float)
    abstract fun onPayloadApplicationComplete(errorCode: Int)
}

/**
 * Test-only stand-in for `android.os.UpdateEngine`. Mirrors the real, verified
 * method surface exactly (see `ReflectiveUpdateEngine.kt`'s header comment):
 * `void applyPayload(String, long, long, String[])`,
 * `boolean bind(UpdateEngineCallback)`, `void unbind()`.
 *
 * [bindResult] lets a test control whether the platform "accepts" the binding,
 * exactly like the real `update_engine` binder call can legitimately return `false`
 * without throwing.
 */
class UpdateEngine {

    companion object {
        @JvmStatic
        var bindResult: Boolean = true

        @JvmStatic
        var lastBoundCallback: UpdateEngineCallback? = null

        @JvmStatic
        var unbindCalled: Boolean = false

        @JvmStatic
        var lastApplyArgs: List<Any?>? = null

        @JvmStatic
        fun reset() {
            bindResult = true
            lastBoundCallback = null
            unbindCalled = false
            lastApplyArgs = null
        }
    }

    fun applyPayload(url: String, offset: Long, size: Long, headerKeyValuePairs: Array<String>) {
        lastApplyArgs = listOf(url, offset, size, headerKeyValuePairs.toList())
    }

    fun bind(callback: UpdateEngineCallback): Boolean {
        lastBoundCallback = callback
        return bindResult
    }

    fun unbind() {
        unbindCalled = true
    }
}
