# ota-update-engine-bridge

| Field | Value |
|---|---|
| Revision | 1 |
| Created | 2026-06-07 |
| Status | scaffold |
| Part of | [Helix OTA](https://github.com/HelixDevelopment/helix_ota) |
| Language | kotlin |
| License | Apache-2.0 |

## Purpose

Thin bridge over AOSP update_engine / boot_control for A/B apply (applyPayload, callbacks, slot/mark-success).

## Boundary (decoupling)

Android-only, thin and testable; no networking, no policy. Wraps the platform API behind a clean interface.

This is a **reusable, independently versioned** building brick (HelixConstitution
§11.4.28 submodules-as-equal-codebase). It is consumed by Helix OTA and is designed
to be reusable by other projects. It must ship in-depth documentation, user guides,
and full test coverage (§1 four-layer) before leaving `scaffold` status.

## Status

Scaffold. Implementation tracked in the Helix OTA spec corpus
(`docs/research/main_specs/`). See the master design and the submodule reuse map.

## Mirrors

- GitHub: https://github.com/HelixDevelopment/ota-update-engine-bridge
- GitLab: https://gitlab.com/helixdevelopment1/ota-update-engine-bridge
