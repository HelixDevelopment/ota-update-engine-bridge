/*
 * Helix OTA — typed model of android.os.UpdateEngine UpdateStatusConstants.
 *
 * Pure Kotlin/JVM: no Android imports. The raw integer values are carried verbatim
 * from the verified AOSP research notes pinned in the spec
 * (update_engine_integration.md §4; android-update-engine-api §8).
 *
 * This is the framework-independent half of the bridge: the :android module receives
 * raw ints from the @SystemApi UpdateEngineCallback and maps them through here.
 */
package digital.vasic.helix.ota.bridge

/**
 * Verified UpdateStatusConstants integer values (spec §4).
 * Kept as a separate constant table so callers that must speak raw ints
 * (e.g. telemetry by code) have a single source of truth.
 */
object UpdateStatusConstants {
    const val IDLE = 0
    const val CHECKING_FOR_UPDATE = 1
    const val UPDATE_AVAILABLE = 2
    const val DOWNLOADING = 3
    const val VERIFYING = 4
    const val FINALIZING = 5
    const val UPDATED_NEED_REBOOT = 6
    const val REPORTING_ERROR_EVENT = 7
    const val ATTEMPTING_ROLLBACK = 8
    const val DISABLED = 9
    // CLEANUP_PREVIOUS_UPDATE is UNVERIFIED for Android 15 (spec §4, §11);
    // intentionally NOT pinned to a literal here. Unknown codes map to
    // [EngineStatus.Unknown] rather than being silently dropped.
}

/**
 * Typed, exhaustive model of an UpdateEngine status callback value.
 *
 * Every known UpdateStatusConstants value (0..9) has a dedicated object so callers
 * can branch with a `when` and the compiler enforces handling. Any value the spec
 * does not pin (e.g. CLEANUP_PREVIOUS_UPDATE) is represented as [Unknown] carrying
 * the original raw code — never lost, never guessed.
 */
sealed class EngineStatus(val code: Int) {
    data object Idle : EngineStatus(UpdateStatusConstants.IDLE)
    data object CheckingForUpdate : EngineStatus(UpdateStatusConstants.CHECKING_FOR_UPDATE)
    data object UpdateAvailable : EngineStatus(UpdateStatusConstants.UPDATE_AVAILABLE)
    data object Downloading : EngineStatus(UpdateStatusConstants.DOWNLOADING)
    data object Verifying : EngineStatus(UpdateStatusConstants.VERIFYING)
    data object Finalizing : EngineStatus(UpdateStatusConstants.FINALIZING)
    data object UpdatedNeedReboot : EngineStatus(UpdateStatusConstants.UPDATED_NEED_REBOOT)
    data object ReportingErrorEvent : EngineStatus(UpdateStatusConstants.REPORTING_ERROR_EVENT)
    data object AttemptingRollback : EngineStatus(UpdateStatusConstants.ATTEMPTING_ROLLBACK)
    data object Disabled : EngineStatus(UpdateStatusConstants.DISABLED)

    /** A status code not pinned by the spec; the raw code is preserved. */
    data class Unknown(val rawCode: Int) : EngineStatus(rawCode)

    /** True for the terminal "payload applied, reboot to switch slots" state (§4). */
    val isNeedReboot: Boolean get() = this is UpdatedNeedReboot

    companion object {
        /**
         * Map a raw UpdateEngine status int to a typed [EngineStatus].
         * Unknown values become [Unknown] (never throws) so the bridge can still
         * surface them to telemetry.
         */
        fun fromCode(code: Int): EngineStatus = when (code) {
            UpdateStatusConstants.IDLE -> Idle
            UpdateStatusConstants.CHECKING_FOR_UPDATE -> CheckingForUpdate
            UpdateStatusConstants.UPDATE_AVAILABLE -> UpdateAvailable
            UpdateStatusConstants.DOWNLOADING -> Downloading
            UpdateStatusConstants.VERIFYING -> Verifying
            UpdateStatusConstants.FINALIZING -> Finalizing
            UpdateStatusConstants.UPDATED_NEED_REBOOT -> UpdatedNeedReboot
            UpdateStatusConstants.REPORTING_ERROR_EVENT -> ReportingErrorEvent
            UpdateStatusConstants.ATTEMPTING_ROLLBACK -> AttemptingRollback
            UpdateStatusConstants.DISABLED -> Disabled
            else -> Unknown(code)
        }
    }
}
