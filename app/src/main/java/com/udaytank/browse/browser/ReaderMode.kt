package com.udaytank.browse.browser

import com.udaytank.browse.data.ReaderFont
import com.udaytank.browse.data.ReaderTheme
import java.util.Locale
import kotlin.math.round

/**
 * Reader mode: a small Readability-style script extracts the main article,
 * and [buildReaderHtml] wraps the result in a clean, themeable page.
 */
object ReaderMode {

    /**
     * Injected into the page; returns JSON {"title","content","ok"} where
     * content is sanitized article HTML. Heuristic: the block with the most
     * paragraph text wins, then we keep only its headings, paragraphs, lists,
     * images, and blockquotes. Nav-like blocks (skip tags, role=navigation,
     * nav-ish class names, link farms with >50% link text) are pruned, a
     * leading H1 that duplicates the page title is dropped, and img srcs are
     * resolved to absolute URLs so saved offline copies keep loadable images.
     * The whole script is wrapped in try/catch: it must never throw.
     */
    val EXTRACT_SCRIPT = """
        (function () {
          try {
            function textLen(el){try{return ((el.innerText!=null?el.innerText:el.textContent)||'').trim().length;}catch(e){return 0;}}
            var candidates = document.querySelectorAll('article, main, [role=main], .post, .article, #content, .content, body');
            var best=null, bestScore=0;
            candidates.forEach(function(el){
              var ps = el.querySelectorAll('p');
              var score=0; ps.forEach(function(p){score+=textLen(p);});
              if(score>bestScore){bestScore=score;best=el;}
            });
            if(!best||bestScore<250){return JSON.stringify({ok:false});}
            var allowed={H1:1,H2:1,H3:1,H4:1,P:1,UL:1,OL:1,LI:1,BLOCKQUOTE:1,IMG:1,FIGURE:1,FIGCAPTION:1,PRE:1,CODE:1,STRONG:1,EM:1,A:1,BR:1};
            var skip={SCRIPT:1,STYLE:1,NAV:1,ASIDE:1,FOOTER:1,HEADER:1,FORM:1,BUTTON:1};
            var skipClassTokens=['nav','menu','sidebar','comments','related','share'];
            function norm(s){return (s||'').replace(/\s+/g,' ').trim().toLowerCase();}
            function classOf(el){
              try{
                var c=el.className;
                if(typeof c==='string'){return c;}
                if(c&&typeof c.baseVal==='string'){return c.baseVal;} // SVGAnimatedString
              }catch(e){}
              return '';
            }
            function shouldSkip(el){
              try{
                if(skip[el.tagName]){return true;}
                if((el.getAttribute('role')||'')==='navigation'){return true;}
                var cls=classOf(el).toLowerCase();
                for(var i=0;i<skipClassTokens.length;i++){
                  if(cls.indexOf(skipClassTokens[i])!==-1){return true;}
                }
              }catch(e){}
              return false;
            }
            function linkDensity(el){
              try{
                var total=textLen(el);
                if(total<=0){return 0;}
                var linkText=0;
                el.querySelectorAll('a').forEach(function(a){linkText+=textLen(a);});
                return linkText/total;
              }catch(e){return 0;}
            }
            var title=(document.querySelector('h1')||{}).innerText||document.title||'';
            var normTitle=norm(title);
            var seenContent=false;
            function clean(node){
              var out='';
              node.childNodes.forEach(function(c){
                if(c.nodeType===3){
                  out+=c.textContent;
                  if(c.textContent.trim()){seenContent=true;}
                }
                else if(c.nodeType===1){
                  if(shouldSkip(c)){return;}
                  if(allowed[c.tagName]){
                    // Leading H1 that duplicates the page title: the reader
                    // header already shows it, so drop the duplicate.
                    if(c.tagName==='H1'&&!seenContent&&normTitle&&norm(c.innerText)===normTitle){return;}
                    // Link farms disguised as lists (nav menus, tag clouds).
                    if((c.tagName==='UL'||c.tagName==='OL')&&linkDensity(c)>0.5){return;}
                    if(c.tagName==='IMG'){
                      var s=c.getAttribute('src')||'';
                      if(s){
                        try{s=new URL(s,location.href).href;}catch(e){}
                        out+='<img src="'+s.replace(/"/g,'&quot;')+'">';
                        seenContent=true;
                      }
                    }
                    else{
                      out+='<'+c.tagName.toLowerCase()+'>'+clean(c)+'</'+c.tagName.toLowerCase()+'>';
                      seenContent=true;
                    }
                  } else {
                    // Structural wrapper (div, section, span…): descend but
                    // emit no tag — unless it is mostly links (nav farm).
                    if(linkDensity(c)>0.5){return;}
                    out+=clean(c);
                  }
                }
              });
              return out;
            }
            return JSON.stringify({ok:true,title:title,content:clean(best)});
          } catch(e) {
            return JSON.stringify({ok:false});
          }
        })();
    """.trimIndent()

    /** Wraps extracted article HTML in a clean, theme-aware reader page. */
    fun buildReaderHtml(
        title: String,
        contentHtml: String,
        theme: ReaderTheme,
        systemDark: Boolean,
        fontScale: Int,
        wide: Boolean,
        font: ReaderFont = ReaderFont.SANS,
    ): String {
        val resolved = when (theme) {
            ReaderTheme.SYSTEM -> if (systemDark) ReaderTheme.DARK else ReaderTheme.LIGHT
            else -> theme
        }
        val (bg, fg, link) = when (resolved) {
            ReaderTheme.DARK -> Triple("#14142E", "#E4E6F1", "#35C3F3")
            ReaderTheme.SEPIA -> Triple("#F4ECD8", "#5B4636", "#8A6D3B")
            else -> Triple("#FFFFFF", "#171A2C", "#1E4FD8")
        }
        val scheme = if (resolved == ReaderTheme.DARK) "dark" else "light"
        val fontSize = formatPx(19.0 * fontScale / 100.0)
        val fontFamily = when (font) {
            ReaderFont.SERIF -> "Georgia,'Times New Roman',serif"
            ReaderFont.SANS -> "-apple-system,Roboto,sans-serif"
        }
        val widthCss = if (wide) "" else " max-width:680px;"
        // v6.11: an estimated reading time under the title (omitted for an empty body).
        val readingTime = ReadingTime.label(contentHtml)
            ?.let { "<div class=\"reading-time\">${escape(it)}</div>" } ?: ""
        return """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="$scheme">
            <style>
              :root { color-scheme: $scheme; }
              body { background:$bg; color:$fg; margin:0 auto; padding:24px 20px 96px;$widthCss
                     font-family:$fontFamily; font-size:${fontSize}px; line-height:1.7; }
              h1 { font-size:28px; line-height:1.25; margin:0 0 8px; }
              .reading-time { opacity:.6; font-size:15px; margin:0 0 24px; }
              h2,h3 { line-height:1.3; margin-top:32px; }
              a { color:$link; }
              img { max-width:100%; height:auto; border-radius:12px; margin:16px 0; }
              blockquote { border-left:3px solid $link; margin:16px 0; padding-left:16px; opacity:.9; }
              pre,code { font-family:monospace; font-size:16px; white-space:pre-wrap; }
            </style></head>
            <body><h1>${escape(title)}</h1>$readingTime$contentHtml</body></html>
        """.trimIndent()
    }

    /** 19px base scaled by [percent], one decimal max, dot separator in any locale. */
    private fun formatPx(px: Double): String {
        val rounded = round(px * 10) / 10
        return if (rounded == round(rounded)) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", rounded)
        }
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
