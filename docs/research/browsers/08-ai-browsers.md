# AI-first browsers (2025-2026 wave)

## 1. The landscape

**Who the players are (as of mid-2026):**

| Player | Product(s) | Status mid-2026 |
|---|---|---|
| Perplexity | **Comet** (desktop + iOS + Android) | Category leader on mobile; went **fully free March 18, 2026** on all platforms; raised $200M for Comet in June 2026 |
| The Browser Company / Atlassian | **Dia** (macOS) + **Arc Search** (iOS/Android legacy) | Acquired by Atlassian for **$610M cash** (closed Oct 2025); Arc sunset; Dia mobile not yet shipped — "Arc Search-inspired mobile updates" promised for 2026 |
| OpenAI | **ChatGPT Atlas** (macOS) | Launched Oct 2025, **discontinued — sunsets Aug 9, 2026**; agentic browsing folded into the ChatGPT app instead. Mobile version never shipped |
| Opera | **Opera AI (formerly Aria)**; **Opera Neon** (premium agentic, $19.90/mo) | Aria rebranded "Opera AI" late 2025, free; Neon publicly available Dec 2025 |
| Google | **Gemini in Chrome** (desktop + iOS/Android) | Sidebar + agentic "auto-browse" rolled out Jan 2026; agentic features gated behind AI Pro/Ultra |
| Samsung | **Samsung Browser "Browsing Assist"** | Rebuilt browser launched March 26, 2026; conversational + agentic AI on Android and Windows, **powered by Perplexity APIs**; ~1B device reach; US/Korea only for agentic features |
| Microsoft | **Edge Copilot Mode** | Strong for M365 users, less compelling outside that ecosystem |
| Brave | **Leo** | Privacy-first assistant (anonymized proxy, local model option); deliberately non-agentic |
| Apple | Safari | AI overhaul reportedly **paused**; Apple pivoting to Gemini-powered Siri instead of an AI Safari |
| Smaller entrants | **SigmaOS**, **Genspark** (169+ on-device open-weight models), **Fellou** (agentic, editable workflow plans) | Niche; none has meaningful mobile presence |

**What "AI browser" means in 2026:** two tiers — (1) **assistant browsers** (chat sidebar, summarize, ask-the-page: Leo, Edge Copilot, Opera AI, Gemini-in-Chrome baseline) and (2) **agentic browsers** (AI navigates, fills forms, completes multi-step tasks: Comet, Dia, Neon, Chrome auto-browse). The 2026 defining features are cross-tab context, browser "memories," and agent mode.

**Adoption reality vs hype:**
- Chrome still holds ~65–71% global share. Dedicated AI browsers remain **niche**; the real shift is incumbents (Chrome, Edge, Samsung) absorbing AI features, which happened fast in H1 2026.
- OpenAI killing Atlas after ~9 months is the wave's biggest deflation signal: the standalone "AI browser" may be a feature, not a product.
- Counter-signal: Perplexity making Comet free everywhere + the Samsung distribution deal (1B devices) shows the mobile battle is fought on **distribution and zero price**, not subscriptions.
- Barriers: learning curve of new UI paradigms, battery/compute cost on low-end phones, and unsolved prompt-injection security.
- Business models diverging: free + publisher revenue share (Comet Plus pays **80% of revenue to publishers**) vs. $19.90/mo premium (Neon) vs. enterprise SaaS (Atlassian/Dia).

## 2. Per-browser breakdown

### Perplexity Comet (mobile — the reference implementation)
- **Core AI:** Assistant that (a) understands the current page ("ask-the-page"), (b) runs **multi-step agentic tasks across tabs**, (c) exposes Perplexity's citation-backed answer engine as the default search. **Cross-tab summarization**. Voice mode. Deep Research in-browser. On iOS, agent tasks run in a **cloud virtual browser** — cookies temporarily transferred, screenshots stream back.
- **Non-AI:** Built-in ad/tracker blocker; Chromium-based on Android/desktop with Chrome extension support (desktop only — no extensions on mobile); reviewers found it renders pages faster than Chrome.
- **UI/UX:** "**One-Thumb**" philosophy — omnibox and **Assistant Drawer at the bottom of the screen**; the drawer is a dynamic overlay summoned with one tap so you keep visual context of the page. Bottom address bar that minimizes on scroll, **prominent Assistant button center of the address bar**, voice button on its right, **long-press a link → "Ask Comet"**, a Library screen for past AI conversations. During agent runs, **blue screen edges signal "AI is in control"** and a tappable step-by-step action log provides transparency/intervention.
- **Pricing:** Free (since March 2026). Optional **Comet Plus $5/mo** (premium publisher content).
- **Platforms:** macOS, Windows, **Android (Nov 2025)**, iOS/iPadOS.

### Arc Search / Dia (The Browser Company, now Atlassian)
- **Arc Search (iOS + Android, the 2024-25 mobile pioneer, still live):**
  - Core AI: **"Browse for Me"** — AI builds a custom results webpage per query (facts, images, citations); **pinch-to-summarize** gesture (two-finger pinch folds the page origami-style with haptics while a summary generates); voice search; "Call Arc" (search by talking as if on a phone call); toggle to flip between Google and Browse-for-Me.
  - Non-AI: rebuilt ad/tracker blocking (2x effectiveness), Google Translate integration, home-screen widget, Arc Sync.
  - Pricing: free.
- **Dia (the strategic future):**
  - macOS-only, public since Oct 2025. Chat-with-your-tabs, **"Skills"** (user-created custom AI automations), personal "work memory," planned deep Jira/Linear/Confluence context under Atlassian.
  - **No mobile app as of mid-2026**; "Arc Search-inspired mobile updates come to Dia during 2026."
  - Pricing: free tier with limits; Pro subscription for unlimited AI use.

### Opera AI + Opera Neon
- **Opera AI (ex-Aria), free, embedded everywhere incl. Android:** tab-aware chat, page/article summarization, YouTube video summarization + translation, PDF summarization, free image generation, voice input, real-time web access. Late-2025 upgrade: faster engine, local model options.
- **Opera Neon (separate premium product, desktop):** **$19.90/mo** — agents "Chat," "**Do**" (task completion), and "**Make**" (builds mini web apps/documents autonomously). Reception: widely judged overpriced vs free Chrome/Comet.

### ChatGPT Atlas (OpenAI) — cautionary tale
- Chromium macOS browser (Oct 2025): ChatGPT sidebar everywhere, **browser memories**, tab-level conversations, paid-only **Agent Mode**. Windows/iOS/Android promised, never shipped. **Killed July 2026 (service stops Aug 9, 2026)** — agentic browsing merged into the ChatGPT desktop app. Data point: even OpenAI couldn't justify a standalone browser.

### Others (brief)
- **Samsung Browser Browsing Assist:** summarize, translate, read-aloud, natural-language **tab management and history navigation**, agentic actions; Perplexity-powered; free; the biggest OEM-default AI browser play.
- **Gemini in Chrome (mobile):** address-bar Gemini icon for ask-the-page/summarize; desktop gets sidebar + agentic auto-browse (find item, apply coupon, buy) for AI Pro/Ultra subscribers.
- **Brave Leo:** summarize, ask-the-page via privacy proxy, optional local models; explicitly no agentic actions.
- **Fellou:** agentic with **user-editable workflow plans before execution** — notable UX idea; also had prompt-injection vulnerabilities.

## 3. Common AI feature patterns

Every AI browser converges on this stack (roughly in order of ubiquity):

1. **Summarize** — current page, then video (YouTube), PDFs, and (differentiator tier) **all open tabs at once**.
2. **Ask-the-page / contextual chat** — Q&A grounded in current tab content; the table-stakes feature.
3. **AI omnibox / answer-engine search** — address bar accepts natural-language questions and returns a generated, cited answer.
4. **Agentic actions / "browse for me" tasks** — multi-step navigation, form filling, shopping, booking. Almost always with a visible step log and interrupt control; on iOS, executed via cloud virtual browsers.
5. **Cross-tab context + tab management by language** — "compare my open tabs," "close everything about X."
6. **Memory / personalization** — browser remembers your history/context for the assistant.
7. **Voice mode** — realtime speech assistant.
8. **Writing help in text fields** and generation extras — the least essential tier.
9. **Non-AI accompaniments that ship with nearly all of them:** built-in ad/tracker blocking, cross-device sync, bottom-bar mobile ergonomics.

## 4. UI/UX patterns for AI in browsers

- **Bottom address bar as AI host (mobile standard):** Comet puts the Assistant button dead-center in a bottom omnibox ("one-thumb" reach); the bar shrinks on scroll.
- **Assistant drawer / bottom-sheet overlay:** the dominant mobile pattern — AI slides up over the page so the page stays visible; not a separate screen.
- **Sidebar panel (desktop pattern):** largely irrelevant on phones but shapes tablet layouts.
- **Gestures:** Arc Search's **two-finger pinch-to-summarize** (origami fold animation + haptics) is the canonical gesture experiment — praised for delight, criticized for summary quality.
- **Long-press context menus:** long-press a link → "Ask Comet"; text-selection → AI actions (summarize/translate/explain) is common.
- **AI-generated results as a page:** Browse for Me renders the answer *as the destination webpage* with a toggle back to Google.
- **Agent-mode takeover signals:** **colored screen border (Comet's blue edges) while AI controls the browser**, plus a reviewable step-by-step action log and a stop/intervene affordance; Fellou goes further with an editable plan preview *before* execution. This transparency layer is now considered mandatory UX for agentic modes.
- **Dedicated screens:** conversation Library/history; voice mode as a full-screen call-style UI.
- **Address-bar icon entry point:** Gemini in Chrome iOS uses a small icon in the address bar — the lowest-friction incumbent pattern.

## 5. What actually works vs gimmicks (per reviews/user sentiment)

**Works (consistent praise):**
- **Summarization + ask-the-page** — the daily-driver features; reviewers who kept an AI browser kept it for research/reading workflows.
- **Cited answer-engine search as default** — genuinely replaces some Google queries.
- **Cross-tab context** ("compare these tabs") — repeatedly called the surprise killer feature.
- **Speed:** Comet rendered common sites faster than Chrome in independent testing — AI browsers are not inherently slow.
- **Voice mode on mobile** for hands-free lookup.
- Reviewer verdicts on Comet: "faster than expected on simple tasks, more useful than expected on research, **rougher than expected on agentic flows**."

**Gimmicks / broken (consistent criticism):**
- **Agentic task completion** — the marquee feature is the weakest: slow, fragile on real-world sites; reviewers advise supervising every run.
- **Pinch-to-summarize** — beloved animation, mediocre summaries.
- **Image/video generation inside a browser** — widely tagged as padding.
- **Security is the category's open wound:** Brave's research showed **indirect prompt injection is systemic** — Comet executed hidden instructions from page content and even from **invisible text in screenshots (OCR steganography attack, Oct 2025)**. "No major AI browser has fully solved prompt injection as of 2026." Practical user posture: use AI for reading/research, keep agents away from logged-in/financial contexts.

## 6. Requirements to build these (APIs, on-device models, costs) — brief and practical

**Rendering layer:** Android → WebView or a Chromium fork (WebView is sufficient for assistant-tier features).

**AI layer options:**
1. **Cloud LLM APIs** (what Comet/Opera/Samsung use): any frontier or mid-tier model API for summarize/ask-page; Perplexity's **Sonar API** is literally what powers Samsung's Browsing Assist. Typical costs: **$0.01–0.10 per page interaction**; agentic multi-step tasks $0.10–0.30 per task on frontier models. Cheap models (Gemini Flash-class, Haiku-class) cut summarization to fractions of a cent per page.
2. **On-device (free inference, private):** Android **ML Kit GenAI APIs on AICore/Gemini Nano** — Summarization API (up to ~3,000 words, EN/JA/KO), Proofreading, Rewriting, Image Description, and the newer **Prompt API** for custom prompts. Zero cloud cost; runs on Pixel 9/10, Galaxy S25, and other flagships — **flagship-only coverage**, so a cloud fallback is still required.
3. **Hybrid** is the practical 2026 pattern: on-device for summarize/translate, cloud for search-answers and agents.

**Agentic stack (if attempted):** page → structured text extraction (Readability-style), an orchestration loop, action transparency UI (step log, colored border, stop button) and hard guardrails — treat all page content as untrusted input (prompt injection), never let the agent act on sensitive origins without confirmation.

**Build cost reality:** assistant-tier features (summarize + ask-page + AI omnibox via one API) are a small team's work; full agentic browsers are $80k–$1.5M builds commercially. Non-AI table stakes to be credible: ad blocking, bottom-bar UX, sync.
