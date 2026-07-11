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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
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

    // callbackFlow's default channel capacity is bounded (~64) with a SUSPEND
    // overflow policy; trySend() never suspends, so once a lagging collector lets
    // that buffer fill up, every further trySend() — including the one terminal
    // onPayloadApplicationComplete event below — starts failing outright and (pre-
    // fix) was silently discarded. The trailing `.buffer(Channel.UNLIMITED)` widens
    // the channel so a slow collector can never cause the terminal outcome to be
    // dropped for capacity reasons; the defensive close() inside
    // onPayloadApplicationComplete below remains as a second, independent layer for
    // any OTHER trySend failure mode (e.g. the channel already being closed).
    override fun observeStatus(): Flow<EngineEvent> = callbackFlow {
        val raw = object : RawEngineCallback {
            override fun onStatusUpdate(status: Int, percent: Float) {
                trySend(EngineEvent.Progress(EngineStatus.fromCode(status), percent))
            }

            override fun onPayloadApplicationComplete(errorCode: Int) {
                // onPayloadApplicationComplete delivers the TERMINAL event: whether the
                // apply outcome was a SUCCESS or a FAILURE. Unlike onStatusUpdate (an
                // interim progress signal a collector can afford to miss one of),
                // losing this exact event leaves the caller with no way to ever learn
                // whether the update succeeded (§11.4.108: a silently-lost outcome is a
                // release-blocking correctness gap, not a cosmetic one). trySend()
                // never suspends — on a lagging collector it can legitimately fail
                // once the channel buffer is saturated, and a discarded ChannelResult
                // would swallow that failure with no log, no exception, no signal at
                // all. Escalate exactly like the sibling engine.bind()-false path
                // above: fail the flow so collect{}/exception handling sees a real,
                // actionable error instead of hanging forever on an outcome that will
                // never arrive.
                val result = trySend(EngineEvent.Complete(EngineError.fromCode(errorCode)))
                if (result.isFailure) {
                    close(
                        IllegalStateException(
                            "Failed to deliver terminal onPayloadApplicationComplete(errorCode=$errorCode) " +
                                "event to the observeStatus() collector: trySend() result was $result. " +
                                "The apply outcome would otherwise be silently lost.",
                            result.exceptionOrNull(),
                        ),
                    )
                }
            }
        }
        val callback = engine.makeCallback(raw)
        // engine.bind() returns false (never throws) when the platform failed to
        // register the callback. Ignoring that would leave this cold Flow silently
        // hung forever — no Progress, no Complete, no error — since update_engine
        // would never actually be told to deliver events to it (§11.4.1: an
        // unreported failure is as misleading as a false PASS). Fail the flow instead
        // so the caller's collect{}/exception handling sees a real, actionable error.
        if (!engine.bind(callback)) {
            close(IllegalStateException("UpdateEngine.bind() returned false: callback was not registered"))
            return@callbackFlow
        }
        awaitClose { engine.unbind() }
    }.buffer(Channel.UNLIMITED)

    override fun rebootToNewSlot(handle: ApplyHandle) {
        // The engine only STAGES the inactive slot; the caller triggers the actual
        // reboot. update_verifier/AVB/markBootSuccessful take over on next boot (§7).
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(null)
    }
}
