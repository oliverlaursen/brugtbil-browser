package dk.brugtbil.browser.data

/**
 * JavaScript run inside the WebView after a Bilbasen search page has rendered.
 * It collects every link to a listing (URLs ending in a numeric id), walks up to the
 * enclosing card element and returns its visible text, plus any JSON-LD blocks.
 * It deliberately avoids CSS class names, which change whenever Bilbasen redeploys.
 */
object ExtractorScript {
    val JS = """
        (function() {
          var idRe = /\/brugt\/bil\/[^\/]+\/[^\/]+\/(?:[^?#]*\/)?(\d{5,})\/?(?:[?#].*)?$/;
          function idOf(href) { var m = idRe.exec(href || ''); return m ? m[1] : null; }
          var out = { cards: [], jsonld: [], challenge: false, title: document.title || '' };
          document.querySelectorAll('script[type="application/ld+json"]').forEach(function(s) {
            out.jsonld.push(s.textContent || '');
          });
          var seen = {};
          document.querySelectorAll('a[href]').forEach(function(a) {
            var id = idOf(a.href);
            if (!id || seen[id]) return;
            seen[id] = true;
            // Climb while the parent still only contains links to this one listing.
            var el = a;
            for (var i = 0; i < 10 && el.parentElement && el.parentElement !== document.body; i++) {
              var p = el.parentElement, other = false;
              p.querySelectorAll('a[href]').forEach(function(x) {
                var xid = idOf(x.href); if (xid && xid !== id) other = true;
              });
              if (other) break;
              el = p;
            }
            var heading = el.querySelector('h1,h2,h3,h4');
            out.cards.push({
              href: a.href,
              title: (heading && heading.innerText) || a.getAttribute('title') || a.innerText || '',
              text: el.innerText || ''
            });
          });
          out.challenge = !!(window.AwsWafIntegration || document.querySelector('#challenge-container'))
                          && out.cards.length === 0;
          return JSON.stringify(out);
        })();
    """.trimIndent()
}
