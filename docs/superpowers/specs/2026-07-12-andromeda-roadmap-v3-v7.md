# Andromeda Version Roadmap: v3 → v7

**Date:** 2026-07-12 · **Status:** APPROVED by owner ("we will add everything and make this a proper browser")
**Source:** [Browser landscape research](../../research/2026-07-12-browser-landscape.md) + [v3 candidate catalog](../../research/2026-07-12-v3-candidate-catalog.md) (feature IDs referenced below)

## The commitment

Everything the research found gets built — nothing is dropped, only sequenced. The five engine-impossible items (extensions, uBlock parity, fingerprint randomization, DoH choice, full DevTools) get WebView substitutes in v5 and a real path at the v7 Engine Decision.

## Standing rules (apply to every version)

1. **Add AND improve:** every version's final phase includes an improve-pass on the weakest already-shipped features before tagging.
2. **Curation over clutter:** every new feature is discoverable, toggleable, and never shouts (anti-bloat lesson from Edge/Opera/Brave criticism).
3. Same delivery rhythm as v1/v2: spec → phased plans → TDD → feature branches → per-phase emulator verification → owner acceptance → merge + tag.

## The versions

### v3 — "Daily Driver" (next)
Goal: after v3, the owner never misses Chrome in daily use.
Scope: B1 B2 B3 B5 B6 B7 B9 · G1 G2 G3 G4 G7 · H1 H3 H5 H6 H7 · D1(upgraded) D2 D5 · F1 · J1 J2 J4 · C1 C3 · A2 A5 A6 · I3 · K1
Spec: [2026-07-12-andromeda-v3-design.md](2026-07-12-andromeda-v3-design.md)

### v4 — "Fortress"
Goal: the safest browser on Android by architecture, not marketing.
Scope: D6 site panel · D7 **Orbits profiles** (signature) · D4 Black Hole · D3 forgetful browsing · D8 permission chip · E1–E6 ad-block v2 complete (incl. **element zapper**) · F2 own password vault · F3 passkeys · F4 breach alerts · K3 clean-URL share · I4 high contrast · download-type warnings (D1 layer 2)

### v5 — "Power & Polish"
Goal: power features that embarrass desktop browsers + the engine-substitute layer.
Scope: H2 on-device translate · H4 PDF viewer · A4 omnibox power mode · B8 workspaces · B4 tab time-capsule archive · G5 video assistant · G6 offline pages · I1→I2 toolbar/menu customization · I5 theme packs · I6 settings search · K2 full-page screenshot+annotate · K4 animation pass · A1 bar position choice · A3 QR scanner · C2 wallpapers · C4 resume card · **user-script engine** (Tampermonkey-class, the extensions substitute) · **injected dev console** (Eruda-class, the DevTools substitute)

### v6 — "Connected"
Goal: Andromeda leaves the phone and meets the world.
Scope: J3 sync (accountless QR-chain, E2E encrypted, own backend) · L1–L3 assistant-tier AI (summarize, ask-the-page, AI omnibox) with a master off-switch; **never agentic** · Play Store launch (listing, crash reporting, privacy policy, "Andromeda" trademark check)

### v7 — "Engine Decision"
Goal: evaluate forking Chromium with a mature product behind us — the only door to true extensions, uBlock parity, engine-level privacy, and DoH. Explicit decision record required either way. `WebViewHolder` remains the single engine boundary until then so the option stays cheap to exercise.
