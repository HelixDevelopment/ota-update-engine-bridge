/*
 * Helix OTA — the bridge surface (spec §9).
 *
 * Clean, framework-independent-typed interface over AOSP update_engine. All callback
 * payloads are the :core typed model (EngineStatus / EngineError). The implementation
 * (AndroidUpdateEngineBridge) reaches the @SystemApi engine via reflection.
 *
 * The bridge is intentionally minimal: no networking, no polling, no business policy.
 * It accepts only an already-verified local artifact; verification lives in the agent.
 */
package digital.vasic.helix.ota.bridge.android

import digital.vasic.helix.ota.bridge.ApplyRequest
import digital.vasic.helix.ota.bridge.EngineError
import digital.vasic.helix.ota.bridge.EngineStatus
import kotlinx.coroutines.flow.Flow

/** Opaque handle for an in-flight apply, returned by [UpdateEngineBridge.applyVerifiedPackage]. */
data class ApplyHandle(val url: String)

/**
 * A typed engine callback event surfaced on [UpdateEngineBridge.observeStatus].
 *
 * - [Progress] wraps a typed [EngineStatus] plus percent from `onStatusUpdate`.
 * - [Complete] wraps a typed [EngineError] from `onPayloadApplicationComplete`.
 */
sealed interface EngineEvent {
    data class Progress(val status: EngineStatus, val percent: Float) : EngineEvent
    data class Complete(val error: EngineError) : EngineEvent
}

/**
 * The only OS-apply path (spec §9). Decoupled from the agent: it does no trust,
 * policy, networking, or polling — it just drives update_engine and reports typed
 * status.
 */
interface UpdateEngineBridge {

    /**
     * Bind the callback and call `applyPayload(url, offset, size, props)` with the four
     * header properties in canonical order. [request] carries an already-verified
     * local (or streaming) artifact descriptor.
     */
    fun applyVerifiedPackage(request: ApplyRequest): ApplyHandle

    /**
     * Cold [Flow] of typed engine events mapped from `onStatusUpdate` /
     * `onPayloadApplicationComplete`. Binds on collection, unbinds on cancellation.
     */
    fun observeStatus(): Flow<EngineEvent>

    /**
     * Trigger the reboot that switches to the freshly-staged slot. UpdateEngine has
     * NO reboot API; this is `PowerManager.reboot(null)`. update_verifier + AVB +
     * framework markBootSuccessful take over on the next boot (spec §7).
     *
     * The bridge NEVER writes slot flags, bumps the rollback index, or calls
     * markBootSuccessful itself (spec §8).
     */
    fun rebootToNewSlot(handle: ApplyHandle)
}
