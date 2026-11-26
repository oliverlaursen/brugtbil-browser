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
            // Image URLs. Images are not downloaded in the WebView and lazy-loading may
            // only fill data-* attributes, so read every likely attribute.
            var images = [];
            function addUrl(u) {
              if (!u || u.indexOf('data:') === 0) return;
              try { images.push(new URL(u.trim(), location.href).href); } catch (e) {}
            }
            function addSrcset(ss) {
              if (!ss) return;
              var parts = ss.split(',').map(function(p) { return p.trim().split(' ')[0]; });
              addUrl(parts[parts.length - 1]); // largest is usually last
            }
            el.querySelectorAll('img, source').forEach(function(img) {
              addSrcset(img.getAttribute('srcset')); addSrcset(img.getAttribute('data-srcset'));
              addUrl(img.getAttribute('src')); addUrl(img.getAttribute('data-src'));
              addUrl(img.getAttribute('data-lazy-src'));
            });
            out.cards.push({
              href: a.href,
              title: (heading && heading.innerText) || a.getAttribute('title') || a.innerText || '',
              text: el.innerText || '',
              images: images
            });
          });
          out.challenge = !!(window.AwsWafIntegration || document.querySelector('#challenge-container'))
                          && out.cards.length === 0;
          return JSON.stringify(out);
        })();
    """.trimIndent()
}
