// Shared logic for the web version: turns what the scraper read from a Bilbasen
// search page into listings (used by scraper/scrape.mjs in Node) and groups
// listings into one row per car model (used by the website in the browser).
// It is a port of the Android app's ListingParser, Aggregator and Slugs.

// ---- Catalog ----------------------------------------------------------------------

export const BRANDS = [
  ["Alfa Romeo", "alfa-romeo", ["Giulia", "Giulietta", "Stelvio", "Tonale"]],
  ["Audi", "audi", ["A1", "A3", "A4", "A5", "A6", "Q2", "Q3", "Q4 e-tron", "Q5", "e-tron"]],
  ["BMW", "bmw", ["1-serie", "2-serie", "3-serie", "4-serie", "5-serie", "X1", "X3", "X5", "i3", "i4", "iX", "iX3"]],
  ["BYD", "byd", ["Atto 3", "Dolphin", "Seal"]],
  ["Citroën", "citron", ["C1", "C3", "C4", "C5 Aircross", "Berlingo"]],
  ["Cupra", "cupra", ["Born", "Formentor", "Leon"]],
  ["Dacia", "dacia", ["Sandero", "Duster", "Jogger", "Spring"]],
  ["Fiat", "fiat", ["500", "500e", "Panda", "Tipo"]],
  ["Ford", "ford", ["Fiesta", "Focus", "Kuga", "Puma", "Mondeo", "Mustang Mach-E", "Transit Custom"]],
  ["Honda", "honda", ["Civic", "CR-V", "Jazz", "HR-V"]],
  ["Hyundai", "hyundai", ["i10", "i20", "i30", "Kona", "Tucson", "Ioniq 5", "Ioniq 6"]],
  ["Kia", "kia", ["Picanto", "Rio", "Ceed", "Niro", "Sportage", "EV6", "EV9"]],
  ["Land Rover", "land-rover", ["Range Rover", "Range Rover Evoque", "Range Rover Sport", "Discovery", "Defender"]],
  ["Lexus", "lexus", ["NX", "RX", "UX"]],
  ["Mazda", "mazda", ["2", "3", "6", "CX-3", "CX-30", "CX-5", "MX-5"]],
  ["Mercedes-Benz", "mercedes", ["A-Klasse", "B-Klasse", "C-Klasse", "E-Klasse", "GLA", "GLC", "EQA", "EQB", "EQC"]],
  ["MG", "mg", ["MG4", "ZS", "Marvel R"]],
  ["Mini", "mini", ["Cooper", "Cooper SE", "Countryman", "Clubman"]],
  ["Mitsubishi", "mitsubishi", ["Space Star", "Outlander", "ASX", "Eclipse Cross"]],
  ["Nissan", "nissan", ["Micra", "Juke", "Qashqai", "Leaf", "X-Trail", "Ariya"]],
  ["Opel", "opel", ["Corsa", "Astra", "Mokka", "Insignia", "Grandland"]],
  ["Peugeot", "peugeot", ["108", "208", "2008", "308", "3008", "5008", "e-208"]],
  ["Polestar", "polestar", ["2", "3"]],
  ["Porsche", "porsche", ["911", "Cayenne", "Macan", "Taycan"]],
  ["Renault", "renault", ["Clio", "Captur", "Megane", "Zoe", "Kadjar"]],
  ["Seat", "seat", ["Ibiza", "Leon", "Arona", "Ateca", "Mii"]],
  ["Skoda", "skoda", ["Citigo", "Fabia", "Octavia", "Superb", "Kamiq", "Karoq", "Kodiaq", "Enyaq"]],
  ["Suzuki", "suzuki", ["Swift", "Vitara", "S-Cross", "Ignis"]],
  ["Tesla", "tesla", ["Model 3", "Model Y", "Model S", "Model X"]],
  ["Toyota", "toyota", ["Aygo", "Aygo X", "Yaris", "Yaris Cross", "Corolla", "C-HR", "RAV4", "bZ4X"]],
  ["VW", "vw", ["up!", "Polo", "Golf", "T-Roc", "Tiguan", "Passat", "Touran", "ID.3", "ID.4", "ID.5", "ID. Buzz"]],
  ["Volvo", "volvo", ["V40", "V60", "V90", "XC40", "XC60", "XC90", "EX30"]],
];

export const loose = (s) => String(s || "").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");

const GENERATION = /^(i|ii|iii|iv|v|vi|vii|viii|ix|x|xi|mk\d+)$/;

/** "golf-viii", "golf-vii" and "golf" all become "golf". At least one token is kept. */
export function baseModel(slug) {
  const parts = String(slug).toLowerCase().split("-");
  while (parts.length > 1 && GENERATION.test(parts[parts.length - 1])) parts.pop();
  return parts.join("-");
}

export function prettify(slug) {
  return String(slug).split("-").filter(Boolean)
    .map((p) => (p.length <= 3 && /\d/.test(p) ? p.toUpperCase() : p[0].toUpperCase() + p.slice(1)))
    .join(" ");
}

const brandBySlug = (slug) => BRANDS.find((b) => loose(b[1]) === loose(slug));
export const brandName = (slug) => brandBySlug(slug)?.[0] ?? prettify(slug);
export function modelName(make, model) {
  return brandBySlug(make)?.[2].find((m) => loose(m) === loose(model)) ?? prettify(model);
}

// ---- Parsing (scraper side) -------------------------------------------------------

const LISTING = /\/brugt\/bil\/([^/?#]+)\/([^/?#]+)\/(?:[^?#]*\/)?(\d{5,})\/?(?:[?#].*)?$/;

export function parseListingUrl(url) {
  const m = LISTING.exec(url || "");
  return m ? { make: m[1].toLowerCase(), model: m[2].toLowerCase(), id: m[3] } : null;
}

const NUM = String.raw`\d{1,3}(?:[.\s]\d{3})+|\d+`;
const priceRe = new RegExp(String.raw`(${NUM})\s*kr(?!\.?\s*(?:\/\s*md|pr\.?\s*md|\/\s*måned|om måneden))`, "gi");
const kmRe = new RegExp(String.raw`(?<![\d,])(${NUM})\s*km\b(?!\s*\/)(?!\s*rækkevidde)(?!\s*\(WLTP)`, "gi");
const monthYearRe = /(?<![\d.])(?:0?[1-9]|1[0-2])[/-]((?:19|20)\d{2})(?!\d)/;
const yearRe = /(?<![\d.,/])((?:19[5-9]|20[0-4])\d)(?![\d.,])/g;
const rangeRes = [
  /(?<![\d,.])(\d{2,4})\s*km\s*(?:\(WLTP\)\s*)?rækkevidde/i,
  /rækkevidde\D{0,20}?(\d{2,4})\s*km/i,
  /(?<![\d,.])(\d{2,4})\s*km\s*\(WLTP/i,
];
const toInt = (s) => { const d = String(s).replace(/\D/g, ""); return d ? parseInt(d, 10) : null; };

export function parsePrice(t) {
  for (const m of t.matchAll(priceRe)) { const v = toInt(m[1]); if (v >= 1000) return v; }
  return null;
}
export function parseKm(t) { const m = [...t.matchAll(kmRe)][0]; return m ? toInt(m[1]) : null; }
export function parseYear(t) {
  const my = monthYearRe.exec(t);
  if (my) return parseInt(my[1], 10);
  const cleaned = t.replace(priceRe, " ").replace(kmRe, " ");
  for (const m of cleaned.matchAll(yearRe)) { const y = parseInt(m[1], 10); if (y >= 1950 && y <= 2035) return y; }
  return null;
}
export function parseRange(t) {
  for (const re of rangeRes) {
    const m = re.exec(t);
    if (m) { const v = parseInt(m[1], 10); return v >= 10 && v <= 1500 ? v : null; }
  }
  return null;
}

export const FUELS = { petrol: "Benzin", diesel: "Diesel", electric: "El", hybrid: "Hybrid", plugin: "Plug-in hybrid" };
export const GEARS = { auto: "Automatisk", manual: "Manuel" };

// \b in JS regexes only knows ASCII letters, which is fine for these words.
export function parseFuel(t) {
  if (/\b(plug-?in|phev|e-hybrid|recharge)\b/i.test(t)) return "plugin";
  if (/\b(hybrid|hev|mhev)\b/i.test(t)) return "hybrid";
  if (/\b(el|electric|elbil|kwh|e-tron|bev)\b/i.test(t)) return "electric";
  if (/\b(diesel|tdi|crdi|dci|hdi|bluehdi|cdi|multijet|ecoblue)\b/i.test(t)) return "diesel";
  if (/\b(benzin|tsi|tfsi|t-gdi|gdi|puretech|ecoboost|tce|vti)\b/i.test(t)) return "petrol";
  return null;
}
export function parseGear(t) {
  if (/\b(manuel|manuelt)\b/i.test(t)) return "manual";
  if (/\b(automatisk|automatgear|automat|aut|dsg|s[- ]tronic|steptronic|cvt|e-cvt|eat\d|edc|tiptronic|powershift)\b/i.test(t)) return "auto";
  if (parseFuel(t) === "electric") return "auto";
  return null;
}

const isSvg = (u) => /\.svg$/i.test(String(u).split("?")[0]);

/** One listing from a card's visible text (see scraper/extract.js). */
export function fromCard(card) {
  const u = parseListingUrl(card.href);
  if (!u) return null;
  const text = String(card.text || "").replace(/ /g, " ");
  const all = (card.title || "") + "\n" + text;
  const images = card.images || [];
  return {
    id: u.id, make: u.make, model: u.model,
    title: String(card.title || "").split("\n").map((s) => s.trim()).find(Boolean) || "",
    price: parsePrice(text), year: parseYear(text), km: parseKm(text), range: parseRange(text),
    fuel: parseFuel(all), gear: parseGear(all),
    img: images.find((x) => /^http/.test(x) && !isSvg(x)) || null,
    imgCount: images.length,
  };
}

/** Listings named in the page's schema.org JSON-LD (photo list, price, name, URL). */
export function fromJsonLd(block) {
  let root;
  try { root = JSON.parse(block); } catch { return []; }
  const out = [];
  const walk = (e) => {
    if (Array.isArray(e)) return e.forEach(walk);
    if (!e || typeof e !== "object") return;
    const types = [].concat(e["@type"] || []);
    const url = e.url || e.offers?.url;
    const u = types.some((t) => ["Car", "Vehicle", "Product", "Offer"].includes(t)) && parseListingUrl(url);
    if (u) {
      const offers = Array.isArray(e.offers) ? e.offers[0] : e.offers;
      const imgs = Array.isArray(e.image) ? e.image : e.image ? [e.image] : [];
      const first = imgs.map((i) => (typeof i === "string" ? i : i?.url)).find(Boolean);
      const price = offers?.price ?? e.price;
      out.push({
        id: u.id, make: u.make, model: u.model, title: e.name || "",
        price: price != null ? toInt(String(price).split(",")[0]) : null,
        year: null, km: null, range: null,
        fuel: e.fuelType ? parseFuel(e.fuelType) : null,
        gear: e.vehicleTransmission ? parseGear(e.vehicleTransmission) : null,
        img: first && /^http/.test(first) ? first : null, imgCount: imgs.length,
      });
    }
    for (const k of ["itemListElement", "item", "@graph", "mainEntity"]) if (e[k]) walk(e[k]);
  };
  walk(root);
  return out;
}

/** Combines a page's cards (year/km/fuel) with its JSON-LD (photos, price). */
export function parsePage(page) {
  const byId = new Map();
  for (const c of page.cards || []) { const l = fromCard(c); if (l) byId.set(l.id, l); }
  for (const block of page.jsonld || []) {
    for (const l of fromJsonLd(block)) {
      const e = byId.get(l.id);
      byId.set(l.id, !e ? l : {
        ...e,
        price: l.price ?? e.price,
        fuel: e.fuel ?? l.fuel, gear: e.gear ?? l.gear,
        img: l.img ?? e.img, imgCount: Math.max(l.imgCount, e.imgCount),
        title: e.title || l.title,
      });
    }
  }
  return [...byId.values()];
}

export const expectedListings = (page) => (page.jsonld || []).reduce((n, b) => n + fromJsonLd(b).length, 0);

// ---- Grouping (website side) ------------------------------------------------------

/** A single "photo" is usually a dealer placeholder; Bilbasen's missing-photo icon is an svg. */
export const hasRealPhoto = (l) => !!l.img && l.imgCount >= 2 && !isSvg(l.img);

export function matches(l, f) {
  if (f.brand && loose(l.make) !== loose(f.brand)) return false;
  if (f.model && loose(baseModel(l.model)) !== loose(f.model)) return false;
  const lo = (v, min) => min == null || (v != null && v >= min);
  const hi = (v, max) => max == null || (v != null && v <= max);
  if (!lo(l.year, f.yearFrom) || !hi(l.year, f.yearTo)) return false;
  if (!lo(l.km, f.kmFrom) || !hi(l.km, f.kmTo)) return false;
  if (!lo(l.price, f.priceFrom) || !hi(l.price, f.priceTo)) return false;
  if (!lo(l.range, f.rangeFrom)) return false;
  if (f.fuels?.length && !f.fuels.includes(l.fuel)) return false;
  if (f.gear && l.gear !== f.gear) return false;
  return true;
}

const minMax = (xs) => (xs.length ? [Math.min(...xs), Math.max(...xs)] : [null, null]);

/** One row per make + model (generations merged), most listings first. */
export function group(listings, f = {}, modelPhotos = {}) {
  const groups = new Map();
  for (const l of listings) {
    if (l.price == null || !matches(l, f)) continue;
    const key = l.make + "/" + baseModel(l.model);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(l);
  }
  return [...groups.entries()].map(([key, items]) => {
    const [make, model] = key.split("/");
    const good = items.find(hasRealPhoto)?.img;
    const [minPrice, maxPrice] = minMax(items.map((l) => l.price));
    const [minYear, maxYear] = minMax(items.map((l) => l.year).filter((v) => v != null));
    const [minKm, maxKm] = minMax(items.map((l) => l.km).filter((v) => v != null));
    const [minRange, maxRange] = minMax(items.map((l) => l.range).filter((v) => v != null));
    return {
      key, make, model, name: brandName(make) + " " + modelName(make, model),
      count: items.length, minPrice, maxPrice, minYear, maxYear, minKm, maxKm, minRange, maxRange,
      img: good ?? modelPhotos[key] ?? items.map((l) => l.img).find((u) => u && !isSvg(u)) ?? null,
      fuels: [...new Set(items.map((l) => l.fuel).filter(Boolean))].sort(),
      url: "https://www.bilbasen.dk/brugt/bil/" + make + "/" + items[0].model,
    };
  }).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, "da"));
}
