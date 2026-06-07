/*
 * Helix OTA — read-only boot-state observers for telemetry (spec §7, §9).
 *
 * The agent OBSERVES the AVB / boot_control / Virtual A/B stack but never DRIVES it
 * outside update_engine (spec §8). These read system properties via reflection on
 * android.os.SystemProperties (also a hidden API) so the module compiles against the
 * public SDK. There are NO mutators here by design (grep gate, spec §10/§8).
 */
package digital.vasic.helix.ota.bridge.android

/** Virtual A/B MergeStatus (spec §7). */
enum class MergeStatus { NONE, UNKNOWN, SNAPSHOTTED, MERGING, CANCELLED }

/** Read-only telemetry observers. No method mutates slot/verity/rollback state. */
interface BootStateObserver {
    /** ro.boot.slot_suffix — active slot, telemetry only. */
    fun currentSlot(): String

    /** ro.boot.verifiedbootstate — GREEN/YELLOW/ORANGE/RED, telemetry only. */
    fun verifiedBootState(): String

    /** ro.boot.veritymode, telemetry only. */
    fun verityMode(): String
}

/**
 * Default [BootStateObserver] reading via reflective `android.os.SystemProperties.get`.
 * Read-only: it exposes no way to set a property or touch slot flags.
 */
class SystemPropertyBootStateObserver(
    classLoader: ClassLoader = SystemPropertyBootStateObserver::class.java.classLoader!!,
) : BootStateObserver {

    private val getMethod = Class
        .forName("android.os.SystemProperties", true, classLoader)
        .getMethod("get", String::class.java, String::class.java)

    private fun read(key: String, default: String = ""): String =
        (getMethod.invoke(null, key, default) as? String) ?: default

    override fun currentSlot(): String = read("ro.boot.slot_suffix")
    override fun verifiedBootState(): String = read("ro.boot.verifiedbootstate")
    override fun verityMode(): String = read("ro.boot.veritymode")
}
