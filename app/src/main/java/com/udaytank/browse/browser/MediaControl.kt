package com.udaytank.browse.browser

/**
 * JavaScript the browser injects to drive an opted-in page's own media player from the
 * lock-screen MediaSession, and to report playback state back to native for the
 * notification/session metadata.
 *
 * The browser can't natively know what "the next track" is, so every snippet drives the
 * page's own controls rather than any WebView media API:
 *  - play/pause toggles the largest ready `<video>`/`<audio>`, falling back to clicking a
 *    site play/pause button;
 *  - next/previous click the site's own transport controls (YouTube / YouTube Music
 *    selectors first, then generic aria-label/title fallbacks);
 *  - [MONITOR] wires `play`/`pause`/`loadedmetadata`/`ended` listeners plus a slow safety
 *    poll that call back through the [BRIDGE_NAME] JavascriptInterface, and auto-advances
 *    on `ended` (only when the site didn't already autoplay the next item, so we never
 *    fight YouTube's own autoplay).
 *
 * Every snippet is wrapped in try/catch and probes for the objects it touches — it must
 * never throw into the page. These are plain constants (no per-call state) so they unit-test
 * as strings and inject identically every time.
 */
object MediaControl {

    /** Name of the [android.webkit.JavascriptInterface] the [MONITOR] reports through. */
    const val BRIDGE_NAME = "AndromedaMedia"

    /** Selector for the largest, most-likely-primary media element and a helper to pick it. */
    private val PICK_MEDIA = """
        function(){try{
          var els=[].slice.call(document.querySelectorAll('video,audio'));
          var live=els.filter(function(e){return e.readyState>0||!e.paused;});
          var pool=live.length?live:els;
          pool.sort(function(a,b){return (b.clientWidth*b.clientHeight)-(a.clientWidth*a.clientHeight);});
          return pool[0]||null;
        }catch(e){return null;}}
    """.trimIndent()

    /** Clicks the site's own "next" transport control; YouTube/YT Music first, then generic. */
    private const val CLICK_NEXT =
        "var b=document.querySelector('.ytp-next-button, ytmusic-player-bar tp-yt-paper-icon-button.next-button, " +
            "tp-yt-paper-icon-button.next-button, [aria-label*=\"Next\"], [title*=\"Next\"]');if(b){b.click();}"

    /** Clicks the site's own "previous" transport control; YouTube/YT Music first, then generic. */
    private const val CLICK_PREV =
        "var b=document.querySelector('.ytp-prev-button, ytmusic-player-bar tp-yt-paper-icon-button.previous-button, " +
            "tp-yt-paper-icon-button.previous-button, [aria-label*=\"Previous\"], [title*=\"Previous\"]');if(b){b.click();}"

    /** Toggle play/pause on the primary media element, else click a site play/pause button. */
    val PLAY_PAUSE: String = """
        (function(){try{
          var pick=$PICK_MEDIA;
          var m=pick();
          if(m){if(m.paused){m.play();}else{m.pause();}return;}
          var b=document.querySelector('.ytp-play-button, #play-pause-button, [aria-label*="Play"], [aria-label*="Pause"], [title*="Play"], [title*="Pause"]');
          if(b){b.click();}
        }catch(e){}})();
    """.trimIndent()

    /** Force-play the primary media element (lock-screen Play while paused). */
    val PLAY: String = """
        (function(){try{
          var pick=$PICK_MEDIA;
          var m=pick();
          if(m&&m.paused){m.play();return;}
          if(!m){var b=document.querySelector('.ytp-play-button, #play-pause-button, [aria-label*="Play"], [title*="Play"]');if(b){b.click();}}
        }catch(e){}})();
    """.trimIndent()

    /** Force-pause the primary media element (lock-screen Pause while playing). */
    val PAUSE: String = """
        (function(){try{
          var pick=$PICK_MEDIA;
          var m=pick();
          if(m&&!m.paused){m.pause();return;}
          if(!m){var b=document.querySelector('.ytp-pause-button, #play-pause-button, [aria-label*="Pause"], [title*="Pause"]');if(b){b.click();}}
        }catch(e){}})();
    """.trimIndent()

    /** Advance to the next track/video via the site's own control (generic fallback last). */
    val NEXT: String = """
        (function(){try{
          $CLICK_NEXT
        }catch(e){}})();
    """.trimIndent()

    /** Go to the previous track/video via the site's own control (generic fallback last). */
    val PREVIOUS: String = """
        (function(){try{
          $CLICK_PREV
        }catch(e){}})();
    """.trimIndent()

    /** Seek the primary media element to [positionMs] (from a lock-screen scrubber drag). */
    fun seekTo(positionMs: Long): String = """
        (function(){try{
          var pick=$PICK_MEDIA;
          var m=pick();
          if(m&&isFinite(m.duration)){m.currentTime=$positionMs/1000;}
        }catch(e){}})();
    """.trimIndent()

    /**
     * One-time monitor: reports title + playing state on media events and a slow poll, and
     * auto-advances on `ended`. The `ended` handler waits [1.5s] before clicking "next" so
     * that sites which autoplay the next item on their own (YouTube) aren't double-skipped —
     * we only step in when playback is still stopped after the grace window.
     */
    val MONITOR: String = """
        (function(){try{
          if(window.__andromedaMediaMon){return;}
          window.__andromedaMediaMon=true;
          var pick=$PICK_MEDIA;
          function title(){try{
            if(navigator.mediaSession&&navigator.mediaSession.metadata&&navigator.mediaSession.metadata.title){
              return navigator.mediaSession.metadata.title;
            }
          }catch(e){}
          return document.title||'';}
          function report(){try{
            var m=pick();
            var playing=!!(m&&!m.paused&&!m.ended);
            var pos=(m&&isFinite(m.currentTime))?Math.round(m.currentTime*1000):-1;
            var dur=(m&&isFinite(m.duration)&&m.duration>0)?Math.round(m.duration*1000):-1;
            if(window.$BRIDGE_NAME&&$BRIDGE_NAME.onMediaState){$BRIDGE_NAME.onMediaState(title(),playing,pos,dur);}
          }catch(e){}}
          function onEnded(){try{
            if(window.$BRIDGE_NAME&&$BRIDGE_NAME.onEnded){$BRIDGE_NAME.onEnded();}
            setTimeout(function(){try{
              var m=pick();
              if(!m||m.paused||m.ended){$CLICK_NEXT}
            }catch(e){}},1500);
          }catch(e){}}
          document.addEventListener('play',report,true);
          document.addEventListener('playing',report,true);
          document.addEventListener('pause',report,true);
          document.addEventListener('loadedmetadata',report,true);
          document.addEventListener('durationchange',report,true);
          document.addEventListener('seeked',report,true);
          document.addEventListener('ratechange',report,true);
          document.addEventListener('ended',onEnded,true);
          // A slow poll refreshes the reported position so the lock-screen scrubber's
          // extrapolation stays accurate; the OS ticks it smoothly between reports.
          if(!window.__andromedaMediaPoll){window.__andromedaMediaPoll=setInterval(report,3000);}
          report();
        }catch(e){}})();
    """.trimIndent()
}
