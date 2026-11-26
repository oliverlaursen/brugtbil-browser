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
            // Image URLs: exactly one entry per <img> element, in page order, so that
            // "picture number 2" means the 2nd image on the card. Images are not
            // downloaded in the WebView and lazy-loading may only fill data-*
            // attributes, so every likely attribute is checked.
            var images = [];
            function abs(u) {
              if (!u) return null;
              u = u.trim();
              if (!u || u.indexOf('data:') === 0) return null;
              try { return new URL(u, location.href).href; } catch (e) { return null; }
            }
            function largest(ss) {
              if (!ss) return null;
              var parts = ss.split(',').map(function(p) { return p.trim().split(' ')[0]; });
              return parts[parts.length - 1]; // largest is usually last
            }
            el.querySelectorAll('img').forEach(function(img) {
              var src = img.closest('picture') && img.closest('picture').querySelector('source');
              var u = abs(largest(img.getAttribute('srcset'))) || abs(largest(img.getAttribute('data-srcset')))
                   || abs(src && (largest(src.getAttribute('srcset')) || largest(src.getAttribute('data-srcset'))))
                   || abs(img.getAttribute('src')) || abs(img.getAttribute('data-src'))
                   || abs(img.getAttribute('data-lazy-src'));
              if (u && images.indexOf(u) < 0) images.push(u);
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
