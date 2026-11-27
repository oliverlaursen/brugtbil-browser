// Collects Bilbasen search results into site/data/listings.json for the website.
// Runs in GitHub Actions on a schedule (.github/workflows/pages.yml).
//
// Like the Android app, it uses a real browser (Playwright's Chromium) because
// Bilbasen's pages run a JavaScript check and draw their listings client-side.
// It reads the same things the app reads: the listing cards' text and the
// page's JSON-LD. Pages are fetched one at a time with pauses in between.
//
// Env: MAX_PAGES (default 100, 30 listings each), OUT (default site/data/listings.json),
//      BILBASEN_BASE (default https://www.bilbasen.dk, overridable for tests).
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parsePage, expectedListings, group, hasRealPhoto, baseModel } from "../site/core.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const EXTRACT = fs.readFileSync(path.join(here, "extract.js"), "utf8");
const BASE = process.env.BILBASEN_BASE || "https://www.bilbasen.dk";
const MAX_PAGES = parseInt(process.env.MAX_PAGES || "100", 10);
const MAX_PHOTO_LOOKUPS = parseInt(process.env.MAX_PHOTO_LOOKUPS || "40", 10);
const OUT = process.env.OUT || path.join(here, "..", "site", "data", "listings.json");
const PAUSE_MS = parseInt(process.env.PAUSE_MS || "2000", 10);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const searchUrl = (page) =>
  `${BASE}/brugt/bil?includeengroscvr=true&includeleasing=false${page > 1 ? `&page=${page}` : ""}`;
const pageOf = (url) => parseInt((/[?&]page=(\d+)/.exec(url) || [0, 1])[1], 10);

async function read(p) {
  try { return JSON.parse(await p.evaluate(EXTRACT)); } catch { return null; }
}

/** Loads a search page and scrolls until every listing has a drawn card. */
async function loadSearchPage(p, n) {
  await p.goto(searchUrl(n), { waitUntil: "domcontentloaded", timeout: 60_000 });
  let raw = null;
  for (let i = 0; i < 90; i++) { // the bot check reloads the page; poll up to ~45 s
    await sleep(500);
    raw = await read(p);
    if (raw && pageOf(raw.url) === n && raw.cards.length) break;
    if (raw && pageOf(raw.url) === n && !raw.challenge && i > 20 && expectedListings(raw) === 0) return raw;
  }
  if (!raw || !raw.cards.length) return raw;
  const cards = new Map(raw.cards.map((c) => [c.href.split("?")[0], c]));
  let stalls = 0;
  for (let i = 0; i < 60 && stalls < 6; i++) {
    const expected = expectedListings(raw);
    if (expected > 0 && cards.size >= expected) break;
    await p.evaluate(() => window.scrollBy(0, Math.max(400, window.innerHeight * 0.9)));
    await sleep(500);
    const next = await read(p);
    if (!next) continue;
    if (next.jsonld.length) raw = next;
    const before = cards.size;
    next.cards.forEach((c) => cards.set(c.href.split("?")[0], c));
    stalls = cards.size > before ? 0 : stalls + 1;
  }
  return { ...raw, cards: [...cards.values()] };
}

/** A real car photo for a model none of whose listings had one: from the model's own page. */
async function modelPhoto(p, make, model) {
  await p.goto(`${BASE}/brugt/bil/${make}/${model}?includeengroscvr=true&includeleasing=false`,
    { waitUntil: "domcontentloaded", timeout: 60_000 });
  for (let i = 0; i < 40; i++) {
    await sleep(500);
    const raw = await read(p);
    if (!raw || !raw.url.includes(`/brugt/bil/${make}/${model}`)) continue;
    const ls = parsePage({ cards: [], jsonld: raw.jsonld });
    if (ls.length) return ls.find((l) => baseModel(l.model) === baseModel(model) && hasRealPhoto(l))?.img ?? null;
  }
  return null;
}

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 412, height: 915 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true,
  locale: "da-DK", timezoneId: "Europe/Copenhagen",
});
await context.route(/\.(png|jpe?g|webp|gif|svg|woff2?)(\?.*)?$/i, (r) => r.abort()); // text only
const page = await context.newPage();

const all = new Map();
try {
  for (let n = 1; n <= MAX_PAGES; n++) {
    const raw = await loadSearchPage(page, n).catch((e) => { console.error(`page ${n}:`, e.message); return null; });
    if (!raw) { if (n === 1) throw new Error("Bilbasen did not answer"); break; }
    if (raw.challenge && !raw.cards.length) { if (n === 1) throw new Error("Bilbasen showed a bot check"); break; }
    const before = all.size;
    for (const l of parsePage(raw)) if (!all.has(l.id)) all.set(l.id, l);
    console.log(`page ${n}: ${raw.cards.length} cards, ${all.size} listings in total`);
    if (all.size === before) break; // past the last page
    await sleep(PAUSE_MS);
  }

  const listings = [...all.values()];
  const modelPhotos = {};
  const missing = group(listings).filter((g) => !listings.some((l) => l.make === g.make
    && baseModel(l.model) === g.model && hasRealPhoto(l))).slice(0, MAX_PHOTO_LOOKUPS);
  for (const g of missing) {
    const slug = listings.find((l) => l.make === g.make && baseModel(l.model) === g.model)?.model ?? g.model;
    const img = await modelPhoto(page, g.make, slug).catch(() => null);
    if (img) modelPhotos[g.key] = img;
    await sleep(PAUSE_MS);
  }
  console.log(`photos looked up for ${Object.keys(modelPhotos).length} of ${missing.length} models without one`);

  if (listings.length < 30) throw new Error(`only ${listings.length} listings, keeping the previous data`);
  // Compact rows keep the download small: [id, make, model, price, year, km, range, fuel, gear, img, imgCount]
  const rows = listings.map((l) => [l.id, l.make, l.model, l.price, l.year, l.km, l.range, l.fuel, l.gear, l.img, l.imgCount]);
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify({ updated: new Date().toISOString(), count: rows.length, modelPhotos, rows }));
  console.log(`wrote ${rows.length} listings to ${OUT}`);
} finally {
  await browser.close();
}
