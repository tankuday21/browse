package com.udaytank.browse.browser

import org.json.JSONObject

/**
 * JS for the password save/fill feature (v4.7), kept next to the other injected-script objects
 * (ZapScripts, MediaControl). [BRIDGE_NAME] is the `@JavascriptInterface` channel — attached ONLY
 * to non-incognito tabs (mirrors MediaJsBridge), so an incognito login has no channel to report on.
 *
 * Security: [fillJs] JSON-encodes the credential strings via [JSONObject.quote] so a password can
 * never break out of the JS string literal (same escaping discipline as ZapScripts.applyHiddenJs).
 */
object PasswordScripts {
    const val BRIDGE_NAME = "AndromedaPass"

    /**
     * Idempotently hooks capturing `submit` listeners on the document. When a form containing a
     * non-empty password field is submitted, reports the best-guess username (nearest preceding
     * text/email input) + password to the native bridge. Best-effort; never throws into the page.
     */
    val HOOK_SUBMIT_JS = """
        (function () {
          if (window.__andromedaPassHooked) return;
          window.__andromedaPassHooked = true;
          function guessUser(form, pwd) {
            var inputs = Array.prototype.slice.call(form.querySelectorAll('input'));
            var pIdx = inputs.indexOf(pwd);
            for (var i = pIdx - 1; i >= 0; i--) {
              var t = (inputs[i].type || '').toLowerCase();
              if (t === 'text' || t === 'email' || t === 'tel' || t === '') return inputs[i].value || '';
            }
            var any = document.querySelector('input[type=email], input[type=text]');
            return any ? (any.value || '') : '';
          }
          document.addEventListener('submit', function (e) {
            try {
              var form = e.target;
              if (!form || !form.querySelector) return;
              var pwd = form.querySelector('input[type=password]');
              if (!pwd || !pwd.value) return;
              window.AndromedaPass.onSubmit(guessUser(form, pwd), pwd.value);
            } catch (_) {}
          }, true);
        })();
    """.trimIndent()

    /** Fills the page's (first) password field + its best-guess username field with the given login. */
    fun fillJs(username: String, password: String): String {
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        return """
            (function () {
              try {
                var pwd = document.querySelector('input[type=password]');
                if (!pwd) return;
                var scope = pwd.form || document;
                var inputs = Array.prototype.slice.call(scope.querySelectorAll('input'));
                var pIdx = inputs.indexOf(pwd);
                var userEl = null;
                for (var i = pIdx - 1; i >= 0; i--) {
                  var t = (inputs[i].type || '').toLowerCase();
                  if (t === 'text' || t === 'email' || t === 'tel' || t === '') { userEl = inputs[i]; break; }
                }
                if (!userEl) userEl = document.querySelector('input[type=email], input[type=text]');
                function setVal(el, v) {
                  if (!el) return;
                  el.focus();
                  el.value = v;
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                setVal(userEl, $u);
                setVal(pwd, $p);
              } catch (_) {}
            })();
        """.trimIndent()
    }
}
