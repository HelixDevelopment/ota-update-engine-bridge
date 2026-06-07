/*
 * Helix OTA — real UpdateEngineBridge over the @SystemApi android.os.UpdateEngine,
 * accessed via REFLECTION (ReflectiveUpdateEngine) so the module compiles against the
 * public SDK. Status/error ints are mapped to the :core typed model.
 *
 * Decoupling (spec §9): no networking, no policy, no slot-flag/markBootSuccessful/
 * rollback-index writes (spec §8). rebootToNewSlot uses PowerManager.reboot(null);
 * UpdateEngine has no reboot API.
 */
package digital.vasic.helix.ota.bridge.android

import android.content.Context
import android.os.PowerManager
import digital.vasic.helix.ota.bridge.ApplyRequest
import digital.vasic.helix.ota.bridge.EngineError
import digital.vasic.helix.ota.bridge.EngineStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Production bridge. Holds one [ReflectiveUpdateEngine] for the lifetime of the
 * instance so apply + status observation share the same bound engine.
 *
 * @param context application context, used for [PowerManager] reboot.
 * @param engine reflective handle to the real engine (defaults to a fresh one).
 */
class AndroidUpdateEngineBridge(
    private val context: Context,
    private val engine: ReflectiveUpdateEngine = ReflectiveUpdateEngine(),
) : UpdateEngineBridge {

    override fun applyVerifiedPackage(request: ApplyRequest): ApplyHandle {
        // The artifact is already verified by the agent (Security-KMP). The bridge
        // only forwards url/offset/size and the four header props in canonical order.
        engine.applyPayload(
            url = request.url,
            offset = request.offset,
            size = request.size,
            headerKeyValuePairs = request.headersArray(),
        )
        return ApplyHandle(request.url)
    }

    override fun observeStatus(): Flow<EngineEvent> = callbackFlow {
        val raw = object : RawEngineCallback {
            override fun onStatusUpdate(status: Int, percent: Float) {
                trySend(EngineEvent.Progress(EngineStatus.fromCode(status), percent))
            }

            override fun onPayloadApplicationComplete(errorCode: Int) {
                trySend(EngineEvent.Complete(EngineError.fromCode(errorCode)))
            }
        }
        val callback = engine.makeCallback(raw)
        engine.bind(callback)
        awaitClose { engine.unbind() }
    }

    override fun rebootToNewSlot(handle: ApplyHandle) {
        // The engine only STAGES the inactive slot; the caller triggers the actual
        // reboot. update_verifier/AVB/markBootSuccessful take over on next boot (§7).
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(null)
    }
}
