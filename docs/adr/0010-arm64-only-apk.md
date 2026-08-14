# ADR-0010: Ship one arm64-only APK — no App Bundle, no ABI splits

**Status:** Accepted
**Date:** 2026-07-24
**Version:** v6.15

## Context

[ADR-0009](0009-mlkit-on-device-translate.md) made the APK large, because ML Kit's native libraries
ship **per ABI**. A universal APK carrying all four ABIs was ~74 MB; filtering to the two ARM ABIs
(v6.1) brought it to ~37 MB, of which 26.7 MB was two copies of the same
`libtranslate_jni.so` — one of which every given phone never executes.

Andromeda is distributed by **sideloading** (installed directly from a staged APK), not through Google
Play, and targets **modern devices only**.

## Options considered

1. **Drop `armeabi-v7a`, keep a single universal APK** *(chosen)* — one line in `abiFilters`. Output
   stays one installable file, so the entire build/stage/verify ritual is unchanged.
2. **Android App Bundle (`.aab`)** — the "correct" answer for Play: per-device delivery, ~24 MB
   downloads, no architecture wasted. **Rejected for now:** an `.aab` is a publishing format, not an
   installable artifact. Without Play it requires `bundletool` to generate a device-specific APK,
   adding a dependency to the release loop and moving dex out of the APK root (breaking dex-verify).
   Enrolling in Play would also trigger **Play App Signing**, a near-permanent handover of the final
   signing key — a decision deserving its own ADR ([ADR-0014](0014-keystore-outside-vcs.md)).
3. **ABI splits (`splits { abi { ... } }`)** — produces one APK per architecture, ~24 MB for arm64,
   no new tools. Rejected as pointless complexity *given a single target architecture*: it yields
   multiple artifacts and a "pick the right file" step for an identical size result.
4. **Trim the adblock assets or drop translation instead** — real levers, but they cost features;
   dropping a dead architecture costs nothing.

## Decision

`ndk { abiFilters += listOf("arm64-v8a") }`. One universal APK, arm64 only.

## Consequences

**Good**
- APK **35.35 MB → 23.62 MB** (−11.7 MB, −33%) with **zero functional change** on any device that can
  install it.
- Ritual untouched: one APK in, one APK out; dex-verify and md5-staging work exactly as before.
- Trivially reversible — re-add the ABI string.

**Bad**
- **arm32-only devices can no longer install Andromeda.** The installer reports "not compatible" — no
  crash, no partial install. Accepted: essentially all phones since ~2017 are arm64.
- If distribution ever moves to Play, this ADR is superseded by the App Bundle path, which delivers
  the same win *and* keeps 32-bit devices.

Note `minSdk 26` is unaffected — ABI and API level are independent axes.

## Revisit when

Andromeda is published to Google Play (use an `.aab`), or a target device turns out to be arm32-only.
