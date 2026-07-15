package com.udaytank.browse.browser.zap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZapSelectorTest {
    @Test fun trimsAndKeepsValid() {
        assertEquals("div.ad-banner", ZapSelector.sanitize("  div.ad-banner  "))
        assertEquals("#id > .a:nth-of-type(2)", ZapSelector.sanitize("#id > .a:nth-of-type(2)"))
    }

    @Test fun blankIsNull() {
        assertNull(ZapSelector.sanitize(""))
        assertNull(ZapSelector.sanitize("   "))
        assertNull(ZapSelector.sanitize(null))
    }

    @Test fun tooLongIsNull() {
        assertNull(ZapSelector.sanitize("a".repeat(ZapSelector.MAX_LEN + 1)))
    }

    @Test fun rejectsUnsafeInput() {
        assertNull(ZapSelector.sanitize("div<script>")) // '<' never valid in CSS
        assertNull(ZapSelector.sanitize("a\nb"))        // control char
    }

    @Test fun keepsValidCssPunctuation() {
        // '>' combinator and '"' attribute selectors are legal CSS and must survive.
        assertEquals("input[name=\"q\"]", ZapSelector.sanitize("input[name=\"q\"]"))
        assertEquals("main > ul > li.item", ZapSelector.sanitize("main > ul > li.item"))
    }
}
