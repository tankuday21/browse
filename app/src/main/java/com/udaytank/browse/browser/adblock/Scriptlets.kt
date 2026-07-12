package com.udaytank.browse.browser.adblock

/**
 * Site-specific ad-block scriptlets — JS injected at document start on hosts where network
 * blocking alone can't win. Deliberately separate from the cosmetic-CSS path
 * ([com.udaytank.browse.browser.AdBlockEngine.cosmeticInjectionScript]): cosmetics hide
 * elements, scriptlets rewrite page behavior before the page's own scripts run.
 *
 * Current pack: YouTube (+ YouTube Music / m.youtube.com), whose ads are same-origin and
 * stream-stitched, so the request blocker never sees them. The script prunes ad metadata out
 * of player responses before the player reads them (Part A), auto-skips whatever still plays
 * (Part B), and hides static ad renderers (Part C).
 *
 * Runtime gating: the script early-exits per invocation while `window.__andromedaAdblockOff`
 * is true — WebViewHolder sets that flag at every page start from the ad-block master toggle
 * and per-site allowlist, because a document-start registration can't be host-conditional
 * after the fact.
 */
object Scriptlets {

    private val youtubeDomains = setOf("youtube.com")

    /**
     * The scriptlet for [host], or "" when the host has none. Suffix-chain matching via
     * [DomainChains]: youtube.com, www./m./music.youtube.com all match; notyoutube.com never.
     */
    fun scriptFor(host: String?): String {
        val h = host?.lowercase() ?: return ""
        return if (DomainChains.matches(h, youtubeDomains)) YOUTUBE_SCRIPT else ""
    }

    /**
     * YouTube ad-block scriptlet. Idempotent via `window.__andromedaYt`; every hook and the
     * watcher tick re-check the `__andromedaAdblockOff` kill switch; everything is wrapped in
     * try/catch so a YT markup change can never break the page; no console output.
     *
     * NOTE: keep this free of `$` (Kotlin raw-string interpolation) and of triple quotes. Its JS
     * syntax is verified with `node -e "new Function(...)"` — see the build notes.
     */
    val YOUTUBE_SCRIPT: String = """
        (function () {
          try {
            var host = (location && location.hostname) ? String(location.hostname).toLowerCase() : '';
            if (host !== 'youtube.com' && host.slice(-12) !== '.youtube.com') { return; }
            if (window.__andromedaYt) { return; }
            window.__andromedaYt = true;

            var off = function () {
              try { return window.__andromedaAdblockOff === true; } catch (e) { return false; }
            };

            /* PART A: prune ad metadata out of player responses before the player reads it. */
            var AD_KEYS = ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams'];

            var pruneOne = function (obj) {
              try {
                if (!obj || typeof obj !== 'object') { return; }
                for (var i = 0; i < AD_KEYS.length; i++) {
                  if (AD_KEYS[i] in obj) {
                    try { delete obj[AD_KEYS[i]]; } catch (e) {}
                  }
                }
              } catch (e) {}
            };

            var prune = function (root) {
              try {
                if (off() || !root || typeof root !== 'object') { return root; }
                pruneOne(root);
                pruneOne(root.playerResponse);
                if (root.response && typeof root.response === 'object') {
                  pruneOne(root.response);
                  pruneOne(root.response.playerResponse);
                }
              } catch (e) {}
              return root;
            };

            try {
              var realParse = JSON.parse;
              JSON.parse = function () {
                return prune(realParse.apply(this, arguments));
              };
            } catch (e) {}

            try {
              if (window.Response && window.Response.prototype && window.Response.prototype.json) {
                var realJson = window.Response.prototype.json;
                window.Response.prototype.json = function () {
                  return realJson.apply(this, arguments).then(function (data) {
                    try { return prune(data); } catch (e) { return data; }
                  });
                };
              }
            } catch (e) {}

            var pruneInline = function () {
              try { prune(window.ytInitialPlayerResponse); } catch (e) {}
            };
            pruneInline();
            try { document.addEventListener('DOMContentLoaded', pruneInline, { once: true }); } catch (e) {}

            /* PART C: static ad renderers, hidden by CSS and removed by the watcher. */
            var STATIC_HIDE = [
              'ytd-display-ad-renderer',
              'ytd-companion-slot-renderer',
              'ytd-action-companion-ad-renderer',
              'ytd-in-feed-ad-layout-renderer',
              'ytd-ad-slot-renderer',
              'ytm-companion-ad-renderer',
              'ytm-promoted-video-renderer',
              'ad-slot-renderer',
              '#masthead-ad'
            ];
            var HAS_HIDE = 'ytd-rich-item-renderer:has(> #content > ytd-ad-slot-renderer)';

            var injectCss = function () {
              try {
                if (!document.documentElement || document.getElementById('__andromedaYtCss')) { return; }
                var s = document.createElement('style');
                s.id = '__andromedaYtCss';
                s.textContent = STATIC_HIDE.join(',') + '{display:none !important;}';
                document.documentElement.appendChild(s);
              } catch (e) {}
            };

            var removeStatics = function () {
              var all = STATIC_HIDE.concat([HAS_HIDE]);
              for (var i = 0; i < all.length; i++) {
                try {
                  var nodes = document.querySelectorAll(all[i]);
                  for (var j = 0; j < nodes.length; j++) {
                    try { nodes[j].remove(); } catch (e) {}
                  }
                } catch (e) {}
              }
            };

            /* PART B: auto-skip watcher for whatever slips past the pruning. */
            var weMuted = false;
            var weSped = false;
            var SKIP_BUTTONS = '.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, button[aria-label*="Skip"]';

            var tick = function () {
              try {
                if (off()) { return; }
                if (document.visibilityState !== 'visible') { return; }
                injectCss();
                pruneInline();
                var player = document.querySelector('#movie_player, .html5-video-player');
                var video = document.querySelector('video');
                var adShowing = false;
                if (player && player.classList) {
                  adShowing = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
                }
                if (adShowing && video) {
                  if (!video.muted) { video.muted = true; weMuted = true; }
                  try {
                    var d = video.duration;
                    if (typeof d === 'number' && isFinite(d) && d > 0) {
                      video.currentTime = d;
                    } else {
                      video.currentTime = 100000;
                    }
                  } catch (e) {}
                  try { video.playbackRate = 16; weSped = true; } catch (e) {}
                  try {
                    var skip = document.querySelector(SKIP_BUTTONS);
                    if (skip) { skip.click(); }
                  } catch (e) {}
                } else if (!adShowing && video) {
                  if (weSped) { try { video.playbackRate = 1; } catch (e) {} weSped = false; }
                  if (weMuted) { try { video.muted = false; } catch (e) {} weMuted = false; }
                }
                try {
                  var close = document.querySelector('.ytp-ad-overlay-close-button');
                  if (close) { close.click(); }
                } catch (e) {}
                removeStatics();
              } catch (e) {}
            };

            setInterval(tick, 500);
          } catch (e) {}
        })();
    """.trimIndent()
}
