# BUILD_STATUS

Honest record of what builds and tests pass in **this** environment versus what
requires the full AOSP / system-API toolchain. No bluffing (HelixConstitution §7.1).

## Environment

| Tool | Version |
|---|---|
| Gradle | 9.5.0 (system, no wrapper) |
| Launcher JVM | 25 (Homebrew) |
| Toolchain JVM | OpenJDK 17 (Homebrew `openjdk@17`, made discoverable via `gradle.properties`) |
| Kotlin | 2.2.0 (`org.jetbrains.kotlin.jvm` / `kotlin.android`) |
| AGP | 8.5.2 (`com.android.library`) |

All commands run with `gradle --no-daemon --console=plain`. No Gradle wrapper is
generated; the system Gradle 9.5 is used.

## Modules

| Module | Type | Plugin | Status here |
|---|---|---|---|
| `:core` | Pure Kotlin/JVM library | `org.jetbrains.kotlin.jvm` 2.2.0 (NO Android plugin) | **Builds + all unit tests PASS** |
| `:android` | Android library | `com.android.library` 8.5.2 + `kotlin.android` | **`assembleRelease` SUCCEEDS** (AAR produced) |

## :core — PASSES

`gradle --no-daemon --console=plain :core:test`

- Result: **BUILD SUCCESSFUL**
- Tests: **27 passed, 0 failed, 0 skipped** across 4 suites:
  - `EngineStatusTest` (5) — maps every status 0..9, constants match spec §4,
    unknown/CLEANUP_PREVIOUS_UPDATE preserved as `Unknown`, `isNeedReboot`.
  - `EngineErrorTest` (5) — maps every pinned error code, constants match spec §5,
    UNVERIFIED 13..50 range → `Unknown`, `isSuccess`.
  - `PayloadPropertiesTest` (10) — `headersArray()` ordering
    (FILE_HASH / FILE_SIZE / METADATA_HASH / METADATA_SIZE), parse round-trip,
    reorder/whitespace tolerance, invalid-input rejection.
  - `ApplyRequestTest` (7) — header delegation, file:// vs https:// classification,
    offset/size semantics, argument validation.

This module is framework-independent and is the authoritative source for the typed
status/error model, `PayloadProperties`, and the `ApplyRequest` value type. It compiles
and tests purely with the system Gradle + JDK 17.

## :android — BUILDS (best-effort, succeeded)

`gradle --no-daemon --console=plain :android:assembleRelease`

- Result in this environment: **BUILD SUCCESSFUL** — `android/build/outputs/aar/android-release.aar` is produced.
- The task brief flagged AGP-on-Gradle-9.5 as a likely failure; here AGP **8.5.2**
  runs on Gradle 9.5 and produces the AAR. This is reported as the **true** result,
  not assumed. If a different AGP/Gradle pairing is used, re-verify.

### Why reflection (and what is NOT exercised here)

`android.os.UpdateEngine`, `android.os.UpdateEngineCallback`, and
`android.os.SystemProperties` are `@SystemApi` / hidden and are **not** in the public
`android.jar`. The `:android` module therefore reaches them **only via reflection**
(`ReflectiveUpdateEngine`, `SystemPropertyBootStateObserver`), which is exactly why it
**compiles against the public SDK**.

What this environment CANNOT do (needs the full AOSP / system-API toolchain + a real
A/B device or emulator):

- Resolve `android.os.UpdateEngine` at **runtime** (only present on a platform image).
- Actually call `applyPayload` / `bind` / `unbind` against a live `update_engine`.
- Provide `PlatformUpdateEngineCallback` (the concrete subclass of the abstract
  `UpdateEngineCallback`) — `UpdateEngineCallback` is an abstract **class**, not an
  interface, so `java.lang.reflect.Proxy` cannot implement it. On a platform build the
  consuming system app supplies that subclass; `ReflectiveUpdateEngine.makeCallback`
  loads it by name. Off-device this throws, by design — inject a fake
  `UpdateEngineBridge` for host tests instead.
- Read real `ro.boot.*` system properties (returns defaults off-device).
- Trigger `PowerManager.reboot(null)`.

These are runtime/integration concerns (spec §10 layers 3–4) that require the AOSP
tree, platform signing / system-UID, SELinux policy, and target-board AVB/boot_control
validation — all explicitly out of scope for a host build and tracked as UNVERIFIED in
the spec (§11).

## Reproduce

```sh
cd ota-update-engine-bridge
gradle --no-daemon --console=plain :core:test
gradle --no-daemon --console=plain :android:assembleRelease
```

(If your JDK 17 lives elsewhere, adjust `org.gradle.java.installations.paths` in
`gradle.properties`.)

## Decoupling (§11.4.28)

`:core` has zero Android dependencies and could be reused by any JVM project. `:android`
holds only the framework wiring and depends on `:core`. The bridge does no networking,
no polling, and no business policy, and contains **no** slot-flag / `markBootSuccessful`
/ rollback-index mutators (spec §8) — only read-only telemetry observers.
