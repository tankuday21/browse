# Phase 6: Polish & Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship v1.0 of **Andromeda** (final name, decided by owner): rebrand, custom galaxy icon, friendly error page (closes the spec §6 gap), signed + minified release build, refreshed README.

**Naming decision:** display name "Andromeda". Kotlin package/namespace stays `com.udaytank.browse` (internal, invisible to users, renaming is churn). `applicationId` becomes `com.udaytank.andromeda` — the store identity is permanent after first publish, so we set it right *before* v1.0 while the app has never been published. (Users with the old debug applicationId install fresh; dev-device data loss acceptable pre-release.)

## Tasks

1. **Rebrand (branch `feature/polish-release`):** `app_name` → "Andromeda"; HomePage wordmark → "Andromeda"; `applicationId` → `com.udaytank.andromeda`.
2. **Icon:** adaptive icon — deep-space indigo background (vector gradient), minimal tilted-ellipse galaxy + bright core as VectorDrawable foreground; add `<monochrome>` for Android 13 themed icons. Verify on launcher.
3. **Friendly error page:** `onReceivedError` (main frame only) → `Listener.onPageError(tabId, description)` → VM `uiState.pageError` (cleared on next page start) → full overlay with message + Retry. VM test.
4. **Release build:** `minifyEnabled true` + `shrinkResources true` for release; generate signing keystore OUTSIDE the repo (`F:\Dev\keystores\andromeda.jks`), wire `signingConfigs.release` via gitignored `keystore.properties`; `versionCode 1`, `versionName "1.0"`; `assembleRelease` must build and install cleanly.
5. **README refresh:** new name, feature list as shipped, architecture, testing stats, screenshots section.
6. **Acceptance:** icon on launcher (incl. themed), error page on airplane-mode load, release APK installs and runs full regression. Merge, tag `v1.0`, push.
