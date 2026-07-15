package com.udaytank.browse.browser.zap

import org.json.JSONArray

/**
 * The Element Zapper's injected JavaScript (v4.0). The picker overlay, element highlighting, and
 * the confirm bar all live in-page (keeps native wiring minimal); the page posts the chosen
 * selector back over the [BRIDGE_NAME] bridge for persistence, and saved selectors are re-applied
 * on every load via [applyHiddenJs]. All selector text sent to [applyHiddenJs] is JSON-encoded so
 * quotes/backslashes can never break out of the injected string.
 */
object ZapScripts {
    const val BRIDGE_NAME = "AndromedaZap"

    /** Builds JS that hides [selectors] via a managed <style> element (idempotent per load). */
    fun applyHiddenJs(selectors: List<String>): String {
        val json = JSONArray(selectors).toString() // valid JS array literal, fully escaped
        return """
            (function(){
              try {
                var sels = $json;
                if (!sels || !sels.length) return;
                var id = '__andromeda_zap_style';
                var st = document.getElementById(id);
                if (!st) { st = document.createElement('style'); st.id = id;
                  (document.head || document.documentElement).appendChild(st); }
                st.textContent = sels.map(function(s){ return s + '{display:none !important;}'; }).join('\n');
              } catch (e) {}
            })();
        """.trimIndent()
    }

    /** Tears down an active picker session (overlay, confirm bar, listeners). */
    val TEARDOWN_JS = """
        (function(){ if (window.__andromedaZap) { try { window.__andromedaZap.stop(); } catch(e){} } })();
    """.trimIndent()

    /**
     * The picker: highlight the element under the pointer, tap to target it, then a bottom confirm
     * bar offers Hide / Hide similar / Cancel. On confirm it hides the element live and posts
     * `(host, selector, label)` to the bridge for persistence. Robust selector: #id → tag+stable
     * classes → nth-of-type path from the nearest stable ancestor. "Similar" = tag+stable classes
     * only (matches sibling cards).
     */
    val PICKER_JS = """
        (function(){
          if (window.__andromedaZap) return;
          var Z = {};
          window.__andromedaZap = Z;

          var box = document.createElement('div');
          box.style.cssText = 'position:fixed;z-index:2147483646;pointer-events:none;border:2px solid #46D0F5;background:rgba(70,208,245,0.15);border-radius:4px;transition:all .04s;display:none;';
          document.documentElement.appendChild(box);

          var bar = null, target = null;

          function stableClasses(el){
            return (el.className && typeof el.className === 'string' ? el.className.trim().split(/\s+/) : [])
              .filter(function(c){ return c && /^[A-Za-z][A-Za-z0-9_-]*${'$'}/.test(c)
                && c.length <= 25 && !/[0-9]{4,}/.test(c); });
          }
          function tagSel(el){
            var t = el.tagName.toLowerCase();
            var cls = stableClasses(el).slice(0,3);
            return t + (cls.length ? '.' + cls.join('.') : '');
          }
          function isStableId(id){ return id && /^[A-Za-z][A-Za-z0-9_-]*${'$'}/.test(id) && !/[0-9]{4,}/.test(id) && id.length <= 40; }
          function exactSelector(el){
            if (isStableId(el.id)) return '#' + el.id;
            var parts = [], node = el, depth = 0;
            while (node && node.nodeType === 1 && node !== document.body && depth < 5){
              if (isStableId(node.id)) { parts.unshift('#' + node.id); break; }
              var sel = tagSel(node);
              var parent = node.parentNode;
              if (parent){
                var same = Array.prototype.filter.call(parent.children, function(c){ return c.tagName === node.tagName; });
                if (same.length > 1) sel += ':nth-of-type(' + (Array.prototype.indexOf.call(parent.children, node)+1) + ')';
              }
              parts.unshift(sel);
              node = node.parentNode; depth++;
            }
            return parts.join(' > ');
          }

          function post(sel){
            try {
              if (window.$BRIDGE_NAME && sel) window.$BRIDGE_NAME.picked(location.host, sel, tagSel(target));
            } catch(e){}
          }
          function hide(sel){ try { document.querySelectorAll(sel).forEach(function(n){ n.style.setProperty('display','none','important'); }); } catch(e){} }

          function removeBar(){ if (bar){ bar.remove(); bar = null; } }
          function showBar(){
            removeBar();
            bar = document.createElement('div');
            bar.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;background:#111228;color:#F2F3FF;font:14px sans-serif;padding:12px 16px;display:flex;gap:10px;align-items:center;box-shadow:0 -4px 16px rgba(0,0,0,.5);';
            var lbl = document.createElement('span'); lbl.textContent = tagSel(target); lbl.style.cssText='flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#C4C7E8;';
            bar.appendChild(lbl);
            function btn(text, bg, fn){ var b=document.createElement('button'); b.textContent=text;
              b.style.cssText='border:0;border-radius:18px;padding:8px 14px;font:600 13px sans-serif;color:#fff;background:'+bg+';';
              b.addEventListener('click', function(ev){ ev.stopPropagation(); ev.preventDefault(); fn(); }, true); bar.appendChild(b); }
            btn('Hide', '#2C5BE6', function(){ var s=exactSelector(target); hide(s); post(s); stop(); });
            btn('Hide similar', '#7A5CFF', function(){ var s=tagSel(target); hide(s); post(s); stop(); });
            btn('Cancel', '#33344d', function(){ stop(); });
            document.documentElement.appendChild(bar);
          }

          function onMove(e){
            var t = e.target;
            if (!t || t === box || (bar && bar.contains(t))) return;
            target = t;
            var r = t.getBoundingClientRect();
            box.style.display='block'; box.style.left=r.left+'px'; box.style.top=r.top+'px';
            box.style.width=r.width+'px'; box.style.height=r.height+'px';
          }
          function onClick(e){
            if (bar && bar.contains(e.target)) return;
            e.preventDefault(); e.stopPropagation();
            target = e.target; showBar();
          }
          function stop(){
            document.removeEventListener('pointermove', onMove, true);
            document.removeEventListener('click', onClick, true);
            if (box) box.remove(); removeBar(); window.__andromedaZap = null;
          }
          Z.stop = stop;
          document.addEventListener('pointermove', onMove, true);
          document.addEventListener('click', onClick, true);
        })();
    """.trimIndent()
}
