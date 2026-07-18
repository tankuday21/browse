package com.udaytank.browse

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udaytank.browse.browser.IntentHardening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives IntentHardening with hostile intent:// URIs (v4.9). Instrumented — Intent.parseUri
 * needs the real Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class IntentHardeningTest {

    private val self = "com.udaytank.andromeda"

    private fun parse(url: String): Intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

    @Test
    fun componentTargetingIsStripped() {
        val hostile = parse(
            "intent://x#Intent;scheme=zxing;component=com.udaytank.andromeda/.SecretActivity;end"
        )
        val hardened = IntentHardening.harden(hostile, self)!!
        assertNull(hardened.component)
        assertTrue(hardened.categories.contains(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun selectorComponentIsStrippedAndBrowsableAdded() {
        val hostile = parse(
            "intent:#Intent;action=android.intent.action.VIEW;" +
                "SEL;component=com.victim/.Internal;end"
        )
        val hardened = IntentHardening.harden(hostile, self)!!
        hardened.selector?.let { sel ->
            assertNull(sel.component)
            assertTrue(sel.categories.contains(Intent.CATEGORY_BROWSABLE))
        }
    }

    @Test
    fun grantFlagsAreReplacedWholesale() {
        val hostile = parse(
            "intent://x#Intent;scheme=zxing;launchFlags=0x00000043;end" // GRANT_READ|GRANT_WRITE|FROM_BACKGROUND
        )
        val hardened = IntentHardening.harden(hostile, self)!!
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, hardened.flags)
    }

    @Test
    fun actionIsForcedToView() {
        val hostile = parse("intent://5551234#Intent;scheme=tel;action=android.intent.action.CALL;end")
        val hardened = IntentHardening.harden(hostile, self)!!
        assertEquals(Intent.ACTION_VIEW, hardened.action)
    }

    @Test
    fun unsafeDataSchemesAreRejected() {
        assertNull(IntentHardening.harden(parse("intent:///sdcard/x#Intent;scheme=file;end"), self))
        assertNull(IntentHardening.harden(parse("intent://a/b#Intent;scheme=content;end"), self))
        assertNull(IntentHardening.harden(parse("intent://alert(1)#Intent;scheme=javascript;end"), self))
    }

    @Test
    fun extrasAreCleared() {
        val hostile = parse(
            "intent://x#Intent;scheme=zxing;S.browser_fallback_url=https%3A%2F%2Fexample.com;S.evil=payload;end"
        )
        // Caller contract: fallback is read BEFORE hardening.
        assertEquals("https://example.com", hostile.getStringExtra("browser_fallback_url"))
        val hardened = IntentHardening.harden(hostile, self)!!
        assertNull(hardened.extras)
    }

    @Test
    fun selfPackageIsDeTargeted() {
        val hostile = parse("intent://example.com#Intent;scheme=http;package=$self;end")
        val hardened = IntentHardening.harden(hostile, self)!!
        assertNull(hardened.`package`)
    }

    @Test
    fun otherPackageTargetingIsKept() {
        // package= is the legitimate way intent:// links pick a specific app (e.g. a UPI app).
        val link = parse("intent://pay#Intent;scheme=upi;package=com.google.android.apps.nbu.paisa.user;end")
        val hardened = IntentHardening.harden(link, self)!!
        assertEquals("com.google.android.apps.nbu.paisa.user", hardened.`package`)
    }
}
