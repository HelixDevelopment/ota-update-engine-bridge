package digital.vasic.helix.ota.bridge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyRequestTest {

    private val props = PayloadProperties("fh", 100L, "mh", 10L)

    @Test
    fun headersArray_delegates_to_payload_properties_in_order() {
        val req = ApplyRequest("file:///data/ota_package/ota_update.zip", 1024L, 5000L, props)
        assertContentEquals(props.headersArray(), req.headersArray())
    }

    @Test
    fun local_file_url_is_local_not_streaming() {
        val req = ApplyRequest("file:///data/ota_package/ota_update.zip", 0L, 10L, props)
        assertTrue(req.isLocal)
        assertFalse(req.isStreaming)
    }

    @Test
    fun https_url_is_streaming_not_local() {
        val req = ApplyRequest("https://cdn.example/ota_update.zip", 0L, 10L, props)
        assertTrue(req.isStreaming)
        assertFalse(req.isLocal)
    }

    @Test
    fun offset_and_size_describe_payload_entry() {
        val req = ApplyRequest("file:///x.zip", 70604L, 871903868L, props)
        assertEquals(70604L, req.offset)
        assertEquals(871903868L, req.size)
    }

    @Test
    fun rejects_blank_url() {
        assertFailsWith<IllegalArgumentException> { ApplyRequest("  ", 0L, 10L, props) }
    }

    @Test
    fun rejects_negative_offset() {
        assertFailsWith<IllegalArgumentException> { ApplyRequest("file:///x", -1L, 10L, props) }
    }

    @Test
    fun rejects_non_positive_size() {
        assertFailsWith<IllegalArgumentException> { ApplyRequest("file:///x", 0L, 0L, props) }
        assertFailsWith<IllegalArgumentException> { ApplyRequest("file:///x", 0L, -5L, props) }
    }
}
