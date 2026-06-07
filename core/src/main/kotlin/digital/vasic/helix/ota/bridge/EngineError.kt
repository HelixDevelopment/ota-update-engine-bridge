/*
 * Helix OTA — typed model of android.os.UpdateEngine ErrorCodeConstants.
 *
 * Pure Kotlin/JVM. Values are carried verbatim from the verified spec table
 * (update_engine_integration.md §5; android-update-engine-api §8). Codes that the
 * spec marks UNVERIFIED for Android 15 (the 13..50 range) are deliberately NOT
 * pinned to typed objects; they round-trip as [EngineError.Unknown].
 */
package digital.vasic.helix.ota.bridge

/** Verified ErrorCodeConstants integer values (spec §5). */
object ErrorCodeConstants {
    const val SUCCESS = 0
    const val ERROR = 1
    const val FILESYSTEM_COPIER_ERROR = 4
    const val POST_INSTALL_RUNNER_ERROR = 5
    const val PAYLOAD_MISMATCHED_TYPE_ERROR = 6
    const val INSTALL_DEVICE_OPEN_ERROR = 7
    const val KERNEL_DEVICE_OPEN_ERROR = 8
    const val DOWNLOAD_TRANSFER_ERROR = 9
    const val PAYLOAD_HASH_MISMATCH_ERROR = 10
    const val PAYLOAD_SIZE_MISMATCH_ERROR = 11
    const val DOWNLOAD_PAYLOAD_VERIFICATION_ERROR = 12
    const val PAYLOAD_TIMESTAMP_ERROR = 51
    const val UPDATED_BUT_NOT_ACTIVE = 52
    const val NOT_ENOUGH_SPACE = 60
    const val DEVICE_CORRUPTED = 61
}

/**
 * Typed model of an UpdateEngine `onPayloadApplicationComplete(errorCode)` value.
 *
 * [Success] is its own object; every other pinned code is a dedicated object so
 * callers branch exhaustively. Codes 13..50 (UNVERIFIED for Android 15, spec §5/§11)
 * and any other unrecognised value become [Unknown] carrying the raw code.
 */
sealed class EngineError(val code: Int) {
    data object Success : EngineError(ErrorCodeConstants.SUCCESS)
    data object Error : EngineError(ErrorCodeConstants.ERROR)
    data object FilesystemCopierError : EngineError(ErrorCodeConstants.FILESYSTEM_COPIER_ERROR)
    data object PostInstallRunnerError : EngineError(ErrorCodeConstants.POST_INSTALL_RUNNER_ERROR)
    data object PayloadMismatchedTypeError : EngineError(ErrorCodeConstants.PAYLOAD_MISMATCHED_TYPE_ERROR)
    data object InstallDeviceOpenError : EngineError(ErrorCodeConstants.INSTALL_DEVICE_OPEN_ERROR)
    data object KernelDeviceOpenError : EngineError(ErrorCodeConstants.KERNEL_DEVICE_OPEN_ERROR)
    data object DownloadTransferError : EngineError(ErrorCodeConstants.DOWNLOAD_TRANSFER_ERROR)
    data object PayloadHashMismatchError : EngineError(ErrorCodeConstants.PAYLOAD_HASH_MISMATCH_ERROR)
    data object PayloadSizeMismatchError : EngineError(ErrorCodeConstants.PAYLOAD_SIZE_MISMATCH_ERROR)
    data object DownloadPayloadVerificationError : EngineError(ErrorCodeConstants.DOWNLOAD_PAYLOAD_VERIFICATION_ERROR)
    data object PayloadTimestampError : EngineError(ErrorCodeConstants.PAYLOAD_TIMESTAMP_ERROR)
    data object UpdatedButNotActive : EngineError(ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE)
    data object NotEnoughSpace : EngineError(ErrorCodeConstants.NOT_ENOUGH_SPACE)
    data object DeviceCorrupted : EngineError(ErrorCodeConstants.DEVICE_CORRUPTED)

    /** An error code not pinned by the spec (incl. the UNVERIFIED 13..50 range). */
    data class Unknown(val rawCode: Int) : EngineError(rawCode)

    /** True only for [Success]; every other value (incl. [Unknown]) is a failure. */
    val isSuccess: Boolean get() = this is Success

    companion object {
        /**
         * Map a raw UpdateEngine error int to a typed [EngineError].
         * Never throws; unrecognised values become [Unknown].
         */
        fun fromCode(code: Int): EngineError = when (code) {
            ErrorCodeConstants.SUCCESS -> Success
            ErrorCodeConstants.ERROR -> Error
            ErrorCodeConstants.FILESYSTEM_COPIER_ERROR -> FilesystemCopierError
            ErrorCodeConstants.POST_INSTALL_RUNNER_ERROR -> PostInstallRunnerError
            ErrorCodeConstants.PAYLOAD_MISMATCHED_TYPE_ERROR -> PayloadMismatchedTypeError
            ErrorCodeConstants.INSTALL_DEVICE_OPEN_ERROR -> InstallDeviceOpenError
            ErrorCodeConstants.KERNEL_DEVICE_OPEN_ERROR -> KernelDeviceOpenError
            ErrorCodeConstants.DOWNLOAD_TRANSFER_ERROR -> DownloadTransferError
            ErrorCodeConstants.PAYLOAD_HASH_MISMATCH_ERROR -> PayloadHashMismatchError
            ErrorCodeConstants.PAYLOAD_SIZE_MISMATCH_ERROR -> PayloadSizeMismatchError
            ErrorCodeConstants.DOWNLOAD_PAYLOAD_VERIFICATION_ERROR -> DownloadPayloadVerificationError
            ErrorCodeConstants.PAYLOAD_TIMESTAMP_ERROR -> PayloadTimestampError
            ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE -> UpdatedButNotActive
            ErrorCodeConstants.NOT_ENOUGH_SPACE -> NotEnoughSpace
            ErrorCodeConstants.DEVICE_CORRUPTED -> DeviceCorrupted
            else -> Unknown(code)
        }
    }
}
