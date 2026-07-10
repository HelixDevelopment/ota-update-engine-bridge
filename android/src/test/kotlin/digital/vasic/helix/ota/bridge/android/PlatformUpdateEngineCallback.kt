/*
 * Helix OTA — test-only stand-in for the concrete subclass a real platform build
 * supplies (see ReflectiveUpdateEngine.makeCallback's doc comment). Forwards to
 * [RawEngineCallback] exactly as the real platform subclass would.
 */
package digital.vasic.helix.ota.bridge.android

import android.os.UpdateEngineCallback

class PlatformUpdateEngineCallback(private val delegate: RawEngineCallback) : UpdateEngineCallback() {
    override fun onStatusUpdate(status: Int, percent: Float) {
        delegate.onStatusUpdate(status, percent)
    }

    override fun onPayloadApplicationComplete(errorCode: Int) {
        delegate.onPayloadApplicationComplete(errorCode)
    }
}
