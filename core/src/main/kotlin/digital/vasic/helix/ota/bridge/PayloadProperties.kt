/*
 * Helix OTA — the four payload_properties.txt header values (spec §3).
 *
 * Pure Kotlin/JVM. [headersArray] produces the exact 4-element String[] passed
 * VERBATIM as `headerKeyValuePairs` to UpdateEngine.applyPayload, in the
 * canonical order FILE_HASH, FILE_SIZE, METADATA_HASH, METADATA_SIZE (spec §2, §3).
 * The order is load-bearing and asserted by the test suite (spec §10 source gate).
 */
package digital.vasic.helix.ota.bridge

/**
 * The four `KEY=VALUE` lines of payload_properties.txt (spec §3).
 *
 * @param fileHashB64    FILE_HASH     = base64(SHA-256) of payload.bin
 * @param fileSize       FILE_SIZE     = total length of payload.bin, in bytes
 * @param metadataHashB64 METADATA_HASH = base64(SHA-256) of the metadata prefix
 * @param metadataSize   METADATA_SIZE = length of the metadata prefix region, in bytes
 */
data class PayloadProperties(
    val fileHashB64: String,
    val fileSize: Long,
    val metadataHashB64: String,
    val metadataSize: Long,
) {
    init {
        require(fileSize >= 0) { "FILE_SIZE must be non-negative, was $fileSize" }
        require(metadataSize >= 0) { "METADATA_SIZE must be non-negative, was $metadataSize" }
    }

    /**
     * The 4-element header array passed verbatim to applyPayload, in the canonical
     * order required by update_engine (spec §3). Order is FILE_HASH, FILE_SIZE,
     * METADATA_HASH, METADATA_SIZE.
     */
    fun headersArray(): Array<String> = arrayOf(
        "${Keys.FILE_HASH}=$fileHashB64",
        "${Keys.FILE_SIZE}=$fileSize",
        "${Keys.METADATA_HASH}=$metadataHashB64",
        "${Keys.METADATA_SIZE}=$metadataSize",
    )

    /** Canonical property keys (spec §3). */
    object Keys {
        const val FILE_HASH = "FILE_HASH"
        const val FILE_SIZE = "FILE_SIZE"
        const val METADATA_HASH = "METADATA_HASH"
        const val METADATA_SIZE = "METADATA_SIZE"
    }

    companion object {
        /**
         * Parse the raw text of payload_properties.txt into [PayloadProperties].
         *
         * Accepts the four `KEY=VALUE` lines in any order, tolerates blank lines and
         * surrounding whitespace. All four keys must be present exactly once; numeric
         * fields must parse as non-negative longs. Throws [IllegalArgumentException]
         * (with a precise message) otherwise — invalid input is never silently coerced.
         */
        fun parse(text: String): PayloadProperties {
            val map = LinkedHashMap<String, String>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val eq = line.indexOf('=')
                require(eq > 0) { "malformed payload_properties line (expected KEY=VALUE): '$rawLine'" }
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                require(map.put(key, value) == null) { "duplicate key in payload_properties: '$key'" }
            }

            val fileHash = map.require(Keys.FILE_HASH)
            val metadataHash = map.require(Keys.METADATA_HASH)
            val fileSize = map.require(Keys.FILE_SIZE).toLongOrThrow(Keys.FILE_SIZE)
            val metadataSize = map.require(Keys.METADATA_SIZE).toLongOrThrow(Keys.METADATA_SIZE)

            val unexpected = map.keys - EXPECTED_KEYS
            require(unexpected.isEmpty()) { "unexpected key(s) in payload_properties: $unexpected" }

            return PayloadProperties(
                fileHashB64 = fileHash,
                fileSize = fileSize,
                metadataHashB64 = metadataHash,
                metadataSize = metadataSize,
            )
        }

        private val EXPECTED_KEYS = setOf(
            Keys.FILE_HASH, Keys.FILE_SIZE, Keys.METADATA_HASH, Keys.METADATA_SIZE,
        )

        private fun Map<String, String>.require(key: String): String =
            this[key] ?: throw IllegalArgumentException("missing required key in payload_properties: '$key'")

        private fun String.toLongOrThrow(key: String): Long =
            toLongOrNull() ?: throw IllegalArgumentException("$key must be a decimal long, was '$this'")
    }
}
