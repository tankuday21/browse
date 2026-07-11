# Phase 5: Media & Downloads Plus Implementation Plan (5a + 5b)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (5a) A long-press context menu on links and images — open in new tab, copy, share, download image. (5b) An in-app Downloads screen listing all downloads with live progress, open-on-tap, and delete.

## Part 5a — Long-press context menu (branch `feature/context-menu`)

**Architecture:** WebView's `setOnLongClickListener` + `hitTestResult` identifies what's under the finger (`SRC_ANCHOR_TYPE` = link, `IMAGE_TYPE`/`SRC_IMAGE_ANCHOR_TYPE` = image; `extra` = URL). Holder forwards `onLongPress(tabId, url, isImage)` to the VM; active-tab guard puts a `LinkContextMenu(url, isImage)` into `BrowserUiState`; BrowserScreen renders a ModalBottomSheet with actions. Only link/image types consume the long-press — text selection keeps working. "Open in new tab" inherits the current tab's incognito flag (Chrome behavior). `WebViewHolder.downloadFile(url)` is extracted from the download listener so the menu can reuse it.

Tasks:
1. Holder: long-click hit-testing + `downloadFile` extraction + `Listener.onLongPress`
2. VM: `contextMenu` state, `onLongPress`/`onContextMenuDismissed`/`onOpenInNewTab(url)` (+ tests: active-tab guard; incognito inheritance)
3. UI: bottom sheet (Open in new tab · Download image [images only] · Copy link · Share); MainActivity listener wiring
4. Acceptance: long-press link → menu; open-in-new-tab works incl. from incognito; image download lands in Downloads; text selection unaffected. Merge.

## Part 5b — Downloads screen (branch `feature/downloads-screen`)

**Architecture:** keep the system `DownloadManager` as the engine (notifications, resume, storage); add our own record: `DownloadEntry(id, downloadId, fileName, url, createdAt)` Room table (migration v3→v4). Enqueue path records a row. `DownloadsScreen` lists entries; a polling `LaunchedEffect` (1s, only while visible) queries `DownloadManager` for status/progress per row; tap a completed row → `ACTION_VIEW` via `DownloadManager.getUriForDownloadedFile`; delete removes from both `DownloadManager` and our table. ⋮ menu gains "Downloads".

Tasks:
1. Migration v3→v4 + `DownloadEntry` + `DownloadDao` (+ instrumented tests)
2. Record on enqueue (holder callback → VM → DAO)
3. `DownloadsScreen` + route + menu item + status polling + open/delete
4. Acceptance: download shows in list with progress; opens on tap; delete works; survives restart. Merge, tag `phase-5`.
