/*
 * Helix OTA — the apply-request value type (spec §2, §9).
 *
 * Pure Kotlin/JVM. Captures exactly the four arguments the bridge feeds to
 * UpdateEngine.applyPayload(url, offset, size, headerKeyValuePairs):
 *
 *   - url:    file:// (local, MVP default) or https:// (streaming, opt-in)
 *   - offset: byte position of the payload.bin entry WITHIN the outer zip (spec §2)
 *   - size:   byte length of that payload.bin entry
 *   - payloadProperties: the four header values (spec §3)
 *
 * The bridge accepts only an already-verified local artifact; this type carries no
 * trust/policy — verification lives in the agent (Security-KMP), not the bridge (§9).
 */
package digital.vasic.helix.ota.bridge

/**
 * An immutable request to apply a payload via update_engine.
 *
 * @param url the package URL — `file://...` for the local MVP path or `https://...`
 *   for the opt-in streaming path. Both schemes use the same applyPayload call;
 *   [offset]/[size] always describe the payload.bin entry inside the outer zip (§2).
 * @param offset byte offset of the payload.bin entry within the outer zip.
 * @param size byte length of the payload.bin entry.
 * @param payloadProperties the four verbatim header properties (§3).
 */
data class ApplyRequest(
    val url: String,
    val offset: Long,
    val size: Long,
    val payloadProperties: PayloadProperties,
) {
    init {
        require(url.isNotBlank()) { "url must not be blank" }
        require(offset >= 0) { "offset must be non-negative, was $offset" }
        require(size > 0) { "size must be positive, was $size" }
    }

    /** Convenience: the header array fed verbatim to applyPayload (§3 order). */
    fun headersArray(): Array<String> = payloadProperties.headersArray()

    /** True for the local verify-before-apply path (MVP default, §6). */
    val isLocal: Boolean get() = url.startsWith("file://")

    /** True for the opt-in streaming path (§6). */
    val isStreaming: Boolean get() = url.startsWith("http://") || url.startsWith("https://")
}
