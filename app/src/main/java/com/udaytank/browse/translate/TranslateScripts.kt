package com.udaytank.browse.translate

/**
 * JS injected via `WebView.evaluateJavascript` for full-page translation (v6.1). No persistent
 * `@JavascriptInterface` is used — each script is a self-contained expression whose value comes
 * back through the evaluate callback, keeping the attack surface minimal.
 *
 * [COLLECT] walks text nodes, stashes each `(node, original)` on `window.__andromedaTr`, and
 * returns their strings as a JSON array. [applyScript] writes translations back by index;
 * [RESTORE] puts the originals back ("Show original").
 */
object TranslateScripts {

    const val MAX_NODES = 2000

    val COLLECT = """
        (function() {
          try {
            var MAX = $MAX_NODES;
            var body = document.body;
            if (!body) return '[]';
            var skip = {SCRIPT:1, STYLE:1, NOSCRIPT:1, TEXTAREA:1, CODE:1, PRE:1};
            var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, {
              acceptNode: function(n) {
                if (!n.nodeValue || !n.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
                var p = n.parentNode;
                if (!p || skip[(p.nodeName || '').toUpperCase()]) return NodeFilter.FILTER_REJECT;
                return NodeFilter.FILTER_ACCEPT;
              }
            });
            var store = [];
            var texts = [];
            var node;
            while ((node = walker.nextNode()) && texts.length < MAX) {
              store.push({ node: node, original: node.nodeValue });
              texts.push(node.nodeValue);
            }
            window.__andromedaTr = store;
            return JSON.stringify(texts);
          } catch (e) { return '[]'; }
        })();
    """.trimIndent()

    val RESTORE = """
        (function() {
          try {
            var arr = window.__andromedaTr || [];
            for (var i = 0; i < arr.length; i++) {
              try { arr[i].node.nodeValue = arr[i].original; } catch (e) {}
            }
            window.__andromedaTr = [];
            return 'ok';
          } catch (e) { return 'err'; }
        })();
    """.trimIndent()

    /** The apply script for a given payload (a JSON array literal from [TranslatePayload]). */
    fun applyScript(payloadJsonArray: String): String = """
        (function() {
          try {
            var t = $payloadJsonArray;
            var arr = window.__andromedaTr || [];
            var n = Math.min(arr.length, t.length);
            for (var i = 0; i < n; i++) {
              try { arr[i].node.nodeValue = t[i]; } catch (e) {}
            }
            return 'ok';
          } catch (e) { return 'err'; }
        })();
    """.trimIndent()
}
