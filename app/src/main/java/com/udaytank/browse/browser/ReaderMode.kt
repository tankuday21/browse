package com.udaytank.browse.browser

/**
 * Reader mode: a small Readability-style script extracts the main article,
 * and [buildReaderHtml] wraps the result in a clean, themeable page.
 */
object ReaderMode {

    /**
     * Injected into the page; returns JSON {"title","content","ok"} where
     * content is sanitized article HTML. Heuristic: the block with the most
     * paragraph text wins, then we keep only its headings, paragraphs, lists,
     * images, and blockquotes.
     */
    val EXTRACT_SCRIPT = """
        (function () {
          function textLen(el){return (el.innerText||'').trim().length;}
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
          function clean(node){
            var out='';
            node.childNodes.forEach(function(c){
              if(c.nodeType===3){out+=c.textContent;}
              else if(c.nodeType===1){
                if(skip[c.tagName]){return;}
                if(allowed[c.tagName]){
                  if(c.tagName==='IMG'){var s=c.getAttribute('src')||'';if(s)out+='<img src="'+s+'">';}
                  else{out+='<'+c.tagName.toLowerCase()+'>'+clean(c)+'</'+c.tagName.toLowerCase()+'>';}
                } else {
                  // Structural wrapper (div, section, span…): descend but emit no tag.
                  out+=clean(c);
                }
              }
            });
            return out;
          }
          var title=(document.querySelector('h1')||{}).innerText||document.title||'';
          return JSON.stringify({ok:true,title:title,content:clean(best)});
        })();
    """.trimIndent()

    /** Wraps extracted article HTML in a clean, theme-aware reader page. */
    fun buildReaderHtml(title: String, contentHtml: String, dark: Boolean): String {
        val bg = if (dark) "#14142E" else "#FFFFFF"
        val fg = if (dark) "#E4E6F1" else "#171A2C"
        val link = if (dark) "#35C3F3" else "#1E4FD8"
        return """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              :root { color-scheme: ${if (dark) "dark" else "light"}; }
              body { background:$bg; color:$fg; margin:0; padding:24px 20px 96px;
                     font-family:-apple-system,Roboto,sans-serif; font-size:19px; line-height:1.7; }
              h1 { font-size:28px; line-height:1.25; margin:0 0 20px; }
              h2,h3 { line-height:1.3; margin-top:32px; }
              a { color:$link; }
              img { max-width:100%; height:auto; border-radius:12px; margin:16px 0; }
              blockquote { border-left:3px solid $link; margin:16px 0; padding-left:16px; opacity:.9; }
              pre,code { font-family:monospace; font-size:16px; white-space:pre-wrap; }
            </style></head>
            <body><h1>${escape(title)}</h1>$contentHtml</body></html>
        """.trimIndent()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
