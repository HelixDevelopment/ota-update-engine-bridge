# ota-update-engine-bridge

| Field | Value |
|---|---|
| Revision | 2 |
| Created | 2026-06-07 |
| Status | implemented (`:core` fully unit-tested; `:android` real, AGP — see `BUILD_STATUS.md`) |
| Part of | [Helix OTA](https://github.com/HelixDevelopment/helix_ota) |
| Gradle modules | `:core` (Kotlin/JVM), `:android` (Android library) |
| Language | kotlin (Kotlin 2.2.0, JVM toolchain 17; AGP 8.5.2) |
| License | Apache-2.0 |

## Overview

`ota-update-engine-bridge` is a thin, testable bridge over the AOSP
`android.os.UpdateEngine` for native A/B updates. It is intentionally minimal:
**no networking, no polling, no business policy, no trust checks** — it accepts
an already-verified local (or streaming) artifact descriptor, drives
`update_engine.applyPayload(url, offset, size, headers)`, and surfaces typed
status. It never writes slot flags, bumps the rollback index, or calls
`markBootSuccessful` (`UpdateEngineBridge.kt`, `AndroidUpdateEngineBridge.kt`);
`update_verifier` + AVB + the framework take over on the next boot.

The repo splits into two Gradle modules (`settings.gradle.kts`):

- **`:core`** — a **PURE Kotlin/JVM** library (no Android plugin) holding the framework-independent typed model and raw-int mapping. Fully unit-tested.
- **`:android`** — the Android-framework wiring. Because `UpdateEngine` / `UpdateEngineCallback` are `@SystemApi` and absent from the public `android.jar`, the implementation binds to them at **runtime via reflection** so the module compiles against the public SDK and runs the real engine only on a platform-signed / system-UID build.

## Public API

### `:core` (`package digital.vasic.helix.ota.bridge`)

- `PayloadProperties(fileHashB64, fileSize, metadataHashB64, metadataSize)` (`PayloadProperties.kt`)
  - `headersArray(): Array<String>` — the 4-element header array in the **canonical, load-bearing order** `FILE_HASH, FILE_SIZE, METADATA_HASH, METADATA_SIZE`.
  - `companion.parse(text): PayloadProperties` — parse `payload_properties.txt` (order-tolerant; rejects missing/duplicate/unexpected keys and non-numeric sizes).
  - `Keys` object with the four canonical key constants.
- `ApplyRequest(url, offset, size, payloadProperties)` (`ApplyRequest.kt`) — validates `url` non-blank, `offset >= 0`, `size > 0`. Helpers: `headersArray()`, `isLocal` (`file://`), `isStreaming` (`http(s)://`).
- `EngineStatus` sealed class (`EngineStatus.kt`) — typed `UpdateEngine` status (`Idle`, `CheckingForUpdate`, `UpdateAvailable`, `Downloading`, `Verifying`, `Finalizing`, `UpdatedNeedReboot`, `ReportingErrorEvent`, `AttemptingRollback`, `Disabled`, plus `Unknown(rawCode)`); `isNeedReboot`; `companion.fromCode(Int)`. Raw-int table `UpdateStatusConstants`.
- `EngineError` sealed class (`EngineError.kt`) — typed `onPayloadApplicationComplete` error (`Success`, `Error`, `PayloadHashMismatchError`, `PayloadSizeMismatchError`, `NotEnoughSpace`, `DeviceCorrupted`, … plus `Unknown(rawCode)` for the UNVERIFIED `13..50` range); `isSuccess`; `companion.fromCode(Int)`. Raw-int table `ErrorCodeConstants`.

### `:android` (`package digital.vasic.helix.ota.bridge.android`)

- `UpdateEngineBridge` interface (`UpdateEngineBridge.kt`) — the only OS-apply path:
  - `applyVerifiedPackage(request: ApplyRequest): ApplyHandle`
  - `observeStatus(): Flow<EngineEvent>` (cold flow; binds on collection, unbinds on cancellation)
  - `rebootToNewSlot(handle: ApplyHandle)` — `PowerManager.reboot(null)` (UpdateEngine has no reboot API).
- `EngineEvent` sealed interface — `Progress(status: EngineStatus, percent: Float)`, `Complete(error: EngineError)`; `ApplyHandle(url)`.
- `AndroidUpdateEngineBridge(context, engine)` (`AndroidUpdateEngineBridge.kt`) — production `UpdateEngineBridge` over a `ReflectiveUpdateEngine`.
- `ReflectiveUpdateEngine` (`ReflectiveUpdateEngine.kt`) — reflective wrapper exposing `applyPayload`, `bind`, `unbind`, `makeCallback`, and `companion.isAvailable()`; `RawEngineCallback` interface.
- `BootStateObserver` interface + `SystemPropertyBootStateObserver` (`BootStateObserver.kt`) — **read-only** telemetry of `currentSlot()`, `verifiedBootState()`, `verityMode()` via reflective `SystemProperties.get`. No mutators by design. `MergeStatus` enum.

## Usage

```kotlin
import digital.vasic.helix.ota.bridge.ApplyRequest
import digital.vasic.helix.ota.bridge.PayloadProperties
import digital.vasic.helix.ota.bridge.android.AndroidUpdateEngineBridge
import digital.vasic.helix.ota.bridge.android.EngineEvent

// Parse payload_properties.txt and build a request for an already-verified local file.
val props = PayloadProperties.parse(
    """
    FILE_HASH=base64sha256...
    FILE_SIZE=1048576
    METADATA_HASH=base64sha256...
    METADATA_SIZE=4096
    """.trimIndent(),
)
val request = ApplyRequest(
    url = "file:///data/ota_package/ota_update.zip",
    offset = 1234L,           // payload.bin offset within the outer zip
    size = 1048576L,          // payload.bin size
    payloadProperties = props,
)

// On a platform-signed / system-UID build:
val bridge = AndroidUpdateEngineBridge(context)
val handle = bridge.applyVerifiedPackage(request)
scope.launch {
    bridge.observeStatus().collect { event ->
        when (event) {
            is EngineEvent.Progress -> log(event.status, event.percent)
            is EngineEvent.Complete -> if (event.error.isSuccess) bridge.rebootToNewSlot(handle)
        }
    }
}
```

## Testing

The `:core` module is the fully-tested deliverable; run its JVM unit tests:

```bash
cd submodules/ota-update-engine-bridge
gradle --no-daemon --console=plain :core:test
```

`:core` tests (`core/src/test/.../bridge/`) cover: `ApplyRequest` header
delegation + ordering, `file://`/`http(s)://` classification, and constructor
rejection of blank url / negative offset / non-positive size
(`ApplyRequestTest.kt`); `EngineStatus` mapping of every known code `0..9`, the
verified spec constant table, `Unknown`-preservation of unpinned codes, and
`isNeedReboot` (`EngineStatusTest.kt`); `EngineError` mapping of every known
code, the UNVERIFIED `13..50` range mapping to `Unknown`, and `isSuccess`
(`EngineErrorTest.kt`); and `PayloadProperties` canonical header order,
parse→`headersArray` round-trip, reordered/blank-line tolerance, and rejection
of missing/duplicate/unexpected keys, non-numeric sizes, and malformed lines
(`PayloadPropertiesTest.kt`).

`:android` is real Kotlin (no stubs), but its build runs the Android Gradle
Plugin under the system Gradle — see `BUILD_STATUS.md` for the honest
per-module build/test record in this environment.

## Reusable building brick

This is a **reusable, independently versioned** Helix OTA building brick
(HelixConstitution §11.4.28 — submodules-as-equal-codebase). Android-only and
thin: it wraps the platform API behind a clean, framework-independent-typed
interface (`:core`), so the agent (and any consumer) drives A/B apply without a
compile-time `@SystemApi` dependency. Universal constitution rules are inherited
via this repo's `CLAUDE.md` / `AGENTS.md` (`## INHERITED FROM Helix
Constitution`).

## Mirrors

- GitHub: https://github.com/HelixDevelopment/ota-update-engine-bridge
- GitLab: https://gitlab.com/helixdevelopment1/ota-update-engine-bridge
