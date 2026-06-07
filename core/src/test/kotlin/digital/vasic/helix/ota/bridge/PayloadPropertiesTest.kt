package digital.vasic.helix.ota.bridge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PayloadPropertiesTest {

    private val sample = PayloadProperties(
        fileHashB64 = "lURPCIkIAjtMOyB/EjQcl8zDzqtD6Ta3tJef6G/+z2k=",
        fileSize = 871903868L,
        metadataHashB64 = "tBvj43QOB0Jn++JojcpVdbRLz0qdAuL+uTkSy7hokaw=",
        metadataSize = 70604L,
    )

    @Test
    fun headersArray_has_exact_order_FILE_HASH_FILE_SIZE_METADATA_HASH_METADATA_SIZE() {
        val headers = sample.headersArray()
        assertEquals(4, headers.size)
        // Order is load-bearing (spec §3). Assert each slot.
        assertEquals("FILE_HASH=lURPCIkIAjtMOyB/EjQcl8zDzqtD6Ta3tJef6G/+z2k=", headers[0])
        assertEquals("FILE_SIZE=871903868", headers[1])
        assertEquals("METADATA_HASH=tBvj43QOB0Jn++JojcpVdbRLz0qdAuL+uTkSy7hokaw=", headers[2])
        assertEquals("METADATA_SIZE=70604", headers[3])
    }

    @Test
    fun headers_keys_appear_in_canonical_position_order() {
        val keysInOrder = sample.headersArray().map { it.substringBefore('=') }
        assertContentEquals(
            listOf("FILE_HASH", "FILE_SIZE", "METADATA_HASH", "METADATA_SIZE"),
            keysInOrder,
        )
    }

    @Test
    fun parse_then_headersArray_round_trips() {
        val text = """
            FILE_HASH=lURPCIkIAjtMOyB/EjQcl8zDzqtD6Ta3tJef6G/+z2k=
            FILE_SIZE=871903868
            METADATA_HASH=tBvj43QOB0Jn++JojcpVdbRLz0qdAuL+uTkSy7hokaw=
            METADATA_SIZE=70604
        """.trimIndent()
        val parsed = PayloadProperties.parse(text)
        assertEquals(sample, parsed)
        assertContentEquals(sample.headersArray(), parsed.headersArray())
    }

    @Test
    fun parse_tolerates_reordered_lines_blank_lines_and_whitespace() {
        val text = """

            METADATA_SIZE = 70604
              FILE_SIZE=871903868
            METADATA_HASH=tBvj43QOB0Jn++JojcpVdbRLz0qdAuL+uTkSy7hokaw=

            FILE_HASH=lURPCIkIAjtMOyB/EjQcl8zDzqtD6Ta3tJef6G/+z2k=
        """.trimIndent()
        assertEquals(sample, PayloadProperties.parse(text))
    }

    @Test
    fun parse_rejects_missing_key() {
        val text = """
            FILE_HASH=abc
            FILE_SIZE=10
            METADATA_HASH=def
        """.trimIndent()
        val ex = assertFailsWith<IllegalArgumentException> { PayloadProperties.parse(text) }
        assertEquals(true, ex.message!!.contains("METADATA_SIZE"))
    }

    @Test
    fun parse_rejects_duplicate_key() {
        val text = """
            FILE_HASH=abc
            FILE_HASH=xyz
            FILE_SIZE=10
            METADATA_HASH=def
            METADATA_SIZE=5
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { PayloadProperties.parse(text) }
    }

    @Test
    fun parse_rejects_non_numeric_size() {
        val text = """
            FILE_HASH=abc
            FILE_SIZE=not-a-number
            METADATA_HASH=def
            METADATA_SIZE=5
        """.trimIndent()
        val ex = assertFailsWith<IllegalArgumentException> { PayloadProperties.parse(text) }
        assertEquals(true, ex.message!!.contains("FILE_SIZE"))
    }

    @Test
    fun parse_rejects_unexpected_key() {
        val text = """
            FILE_HASH=abc
            FILE_SIZE=10
            METADATA_HASH=def
            METADATA_SIZE=5
            BOGUS=1
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { PayloadProperties.parse(text) }
    }

    @Test
    fun parse_rejects_malformed_line_without_equals() {
        val text = """
            FILE_HASH=abc
            FILE_SIZE 10
            METADATA_HASH=def
            METADATA_SIZE=5
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { PayloadProperties.parse(text) }
    }

    @Test
    fun constructor_rejects_negative_sizes() {
        assertFailsWith<IllegalArgumentException> {
            PayloadProperties("h", -1L, "m", 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            PayloadProperties("h", 0L, "m", -1L)
        }
    }
}
