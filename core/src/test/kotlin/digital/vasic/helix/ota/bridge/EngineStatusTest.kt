package digital.vasic.helix.ota.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineStatusTest {

    @Test
    fun maps_every_known_status_code_0_through_9() {
        val expected: Map<Int, EngineStatus> = mapOf(
            0 to EngineStatus.Idle,
            1 to EngineStatus.CheckingForUpdate,
            2 to EngineStatus.UpdateAvailable,
            3 to EngineStatus.Downloading,
            4 to EngineStatus.Verifying,
            5 to EngineStatus.Finalizing,
            6 to EngineStatus.UpdatedNeedReboot,
            7 to EngineStatus.ReportingErrorEvent,
            8 to EngineStatus.AttemptingRollback,
            9 to EngineStatus.Disabled,
        )
        for ((code, status) in expected) {
            assertEquals(status, EngineStatus.fromCode(code), "code $code")
            assertEquals(code, EngineStatus.fromCode(code).code, "round-trip code $code")
        }
    }

    @Test
    fun constants_match_spec_table_section_4() {
        assertEquals(0, UpdateStatusConstants.IDLE)
        assertEquals(1, UpdateStatusConstants.CHECKING_FOR_UPDATE)
        assertEquals(2, UpdateStatusConstants.UPDATE_AVAILABLE)
        assertEquals(3, UpdateStatusConstants.DOWNLOADING)
        assertEquals(4, UpdateStatusConstants.VERIFYING)
        assertEquals(5, UpdateStatusConstants.FINALIZING)
        assertEquals(6, UpdateStatusConstants.UPDATED_NEED_REBOOT)
        assertEquals(7, UpdateStatusConstants.REPORTING_ERROR_EVENT)
        assertEquals(8, UpdateStatusConstants.ATTEMPTING_ROLLBACK)
        assertEquals(9, UpdateStatusConstants.DISABLED)
    }

    @Test
    fun unknown_code_is_preserved_not_dropped() {
        // 12 = the UNVERIFIED CLEANUP_PREVIOUS_UPDATE; must not be silently pinned.
        val status = EngineStatus.fromCode(12)
        val unknown = assertIs<EngineStatus.Unknown>(status)
        assertEquals(12, unknown.rawCode)
        assertEquals(12, unknown.code)
    }

    @Test
    fun negative_and_large_unknown_codes_map_to_unknown() {
        assertEquals(EngineStatus.Unknown(-1), EngineStatus.fromCode(-1))
        assertEquals(EngineStatus.Unknown(999), EngineStatus.fromCode(999))
    }

    @Test
    fun isNeedReboot_only_true_for_status_6() {
        assertTrue(EngineStatus.fromCode(6).isNeedReboot)
        assertFalse(EngineStatus.fromCode(3).isNeedReboot)
        assertFalse(EngineStatus.fromCode(12).isNeedReboot)
    }
}
