package digital.vasic.helix.ota.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineErrorTest {

    @Test
    fun maps_every_known_error_code() {
        val expected: Map<Int, EngineError> = mapOf(
            0 to EngineError.Success,
            1 to EngineError.Error,
            4 to EngineError.FilesystemCopierError,
            5 to EngineError.PostInstallRunnerError,
            6 to EngineError.PayloadMismatchedTypeError,
            7 to EngineError.InstallDeviceOpenError,
            8 to EngineError.KernelDeviceOpenError,
            9 to EngineError.DownloadTransferError,
            10 to EngineError.PayloadHashMismatchError,
            11 to EngineError.PayloadSizeMismatchError,
            12 to EngineError.DownloadPayloadVerificationError,
            51 to EngineError.PayloadTimestampError,
            52 to EngineError.UpdatedButNotActive,
            60 to EngineError.NotEnoughSpace,
            61 to EngineError.DeviceCorrupted,
        )
        for ((code, error) in expected) {
            assertEquals(error, EngineError.fromCode(code), "code $code")
            assertEquals(code, EngineError.fromCode(code).code, "round-trip code $code")
        }
    }

    @Test
    fun constants_match_spec_table_section_5() {
        assertEquals(0, ErrorCodeConstants.SUCCESS)
        assertEquals(1, ErrorCodeConstants.ERROR)
        assertEquals(4, ErrorCodeConstants.FILESYSTEM_COPIER_ERROR)
        assertEquals(5, ErrorCodeConstants.POST_INSTALL_RUNNER_ERROR)
        assertEquals(6, ErrorCodeConstants.PAYLOAD_MISMATCHED_TYPE_ERROR)
        assertEquals(7, ErrorCodeConstants.INSTALL_DEVICE_OPEN_ERROR)
        assertEquals(8, ErrorCodeConstants.KERNEL_DEVICE_OPEN_ERROR)
        assertEquals(9, ErrorCodeConstants.DOWNLOAD_TRANSFER_ERROR)
        assertEquals(10, ErrorCodeConstants.PAYLOAD_HASH_MISMATCH_ERROR)
        assertEquals(11, ErrorCodeConstants.PAYLOAD_SIZE_MISMATCH_ERROR)
        assertEquals(12, ErrorCodeConstants.DOWNLOAD_PAYLOAD_VERIFICATION_ERROR)
        assertEquals(51, ErrorCodeConstants.PAYLOAD_TIMESTAMP_ERROR)
        assertEquals(52, ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE)
        assertEquals(60, ErrorCodeConstants.NOT_ENOUGH_SPACE)
        assertEquals(61, ErrorCodeConstants.DEVICE_CORRUPTED)
    }

    @Test
    fun unverified_13_to_50_range_maps_to_unknown() {
        // Spec §5/§11: codes 13..50 are UNVERIFIED for Android 15; never pin them.
        for (code in 13..50) {
            val err = EngineError.fromCode(code)
            val unknown = assertIs<EngineError.Unknown>(err, "code $code should be Unknown")
            assertEquals(code, unknown.rawCode)
        }
    }

    @Test
    fun unknown_codes_outside_known_set_map_to_unknown() {
        assertEquals(EngineError.Unknown(2), EngineError.fromCode(2))
        assertEquals(EngineError.Unknown(3), EngineError.fromCode(3))
        assertEquals(EngineError.Unknown(-7), EngineError.fromCode(-7))
        assertEquals(EngineError.Unknown(70), EngineError.fromCode(70))
    }

    @Test
    fun isSuccess_only_true_for_success() {
        assertTrue(EngineError.fromCode(0).isSuccess)
        assertFalse(EngineError.fromCode(1).isSuccess)
        assertFalse(EngineError.fromCode(10).isSuccess)
        assertFalse(EngineError.fromCode(99).isSuccess)
    }
}
