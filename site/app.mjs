import { group, brandName, modelName, baseModel, FUELS, GEARS } from "./core.mjs";

const PAGE = 60; // model rows rendered at a time
const nf = new Intl.NumberFormat("da-DK");
const kr = (n) => nf.format(n) + " kr.";
const km = (n) => nf.format(n) + " km";
const span = (a, b, f) => (a == null ? null : a === b ? f(a) : `${f(a)} – ${f(b)}`);
const $ = (s) => document.querySelector(s);
const form = $("#filters");

let listings = [];
let modelPhotos = {};
let shown = PAGE;

// ---- Filter form <-> state <-> URL ----------------------------------------------------

const NUMBERS = ["yearFrom", "yearTo", "kmFrom", "kmTo", "priceFrom", "priceTo", "rangeFrom"];

function chip(container, type, name, value, label) {
  const l = document.createElement("label");
  l.className = "chip";
  l.innerHTML = `<input type="${type}" name="${name}" value="${value}"><span></span>`;
  l.querySelector("span").textContent = label;
  container.append(l);
}
Object.entries(FUELS).forEach(([v, label]) => chip($("#fuels"), "checkbox", "fuel", v, label));
chip($("#gears"), "radio", "gear", "", "Alle");
Object.entries(GEARS).forEach(([v, label]) => chip($("#gears"), "radio", "gear", v, label));

function readForm() {
  const f = { brand: form.brand.value, model: form.model.value,
              fuels: [...form.querySelectorAll("[name=fuel]:checked")].map((i) => i.value),
              gear: form.querySelector("[name=gear]:checked")?.value || "" };
  for (const k of NUMBERS) {
    const v = parseInt(form[k].value.replace(/\D/g, ""), 10);
    f[k] = Number.isFinite(v) ? v : null;
  }
  return f;
}

function writeUrl(f) {
  const q = new URLSearchParams();
  if (f.brand) q.set("maerke", f.brand);
  if (f.model) q.set("model", f.model);
  for (const k of NUMBERS) if (f[k] != null) q.set(k, f[k]);
  if (f.fuels.length) q.set("fuel", f.fuels.join(","));
  if (f.gear) q.set("gear", f.gear);
  history.replaceState(null, "", q.toString() ? "?" + q : location.pathname);
}

function readUrl() {
  const q = new URLSearchParams(location.search);
  form.brand.value = q.get("maerke") || "";
  fillModels();
  form.model.value = q.get("model") || "";
  for (const k of NUMBERS) form[k].value = q.get(k) || "";
  const fuels = (q.get("fuel") || "").split(",");
  form.querySelectorAll("[name=fuel]").forEach((i) => (i.checked = fuels.includes(i.value)));
  const gear = q.get("gear") || "";
  form.querySelectorAll("[name=gear]").forEach((i) => (i.checked = i.value === gear));
}

// ---- Brand / model dropdowns from the data ----------------------------------------

function options(select, entries) {
  const keep = select.value;
  select.replaceChildren(new Option("Alle", ""), ...entries.map(([v, label]) => new Option(label, v)));
  select.value = entries.some(([v]) => v === keep) ? keep : "";
}

function fillBrands() {
  const counts = new Map();
  for (const l of listings) counts.set(l.make, (counts.get(l.make) || 0) + 1);
  const brands = [...counts].map(([m, n]) => [m, `${brandName(m)} (${n})`])
    .sort((a, b) => a[1].localeCompare(b[1], "da"));
  options(form.brand, brands);
}

function fillModels() {
  const make = form.brand.value;
  form.model.disabled = !make;
  const counts = new Map();
  for (const l of listings) if (l.make === make) {
    const m = baseModel(l.model);
    counts.set(m, (counts.get(m) || 0) + 1);
  }
  options(form.model, [...counts].map(([m, n]) => [m, `${modelName(make, m)} (${n})`])
    .sort((a, b) => a[1].localeCompare(b[1], "da")));
}

// ---- Results ------------------------------------------------------------------------

const PLACEHOLDER = "data:image/svg+xml," + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-8 -8 40 40"><path fill="#8a80a0" d="M18.9 6c-.2-.6-.8-1-1.4-1h-11c-.7 0-1.2.4-1.4 1L3 12v8c0 .6.4 1 1 1h1c.6 0 1-.4 1-1v-1h12v1c0 .6.4 1 1 1h1c.6 0 1-.4 1-1v-8l-2.1-6zM6.5 16c-.8 0-1.5-.7-1.5-1.5S5.7 13 6.5 13s1.5.7 1.5 1.5S7.3 16 6.5 16zm11 0c-.8 0-1.5-.7-1.5-1.5s.7-1.5 1.5-1.5 1.5.7 1.5 1.5-.7 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z"/></svg>`);

function card(g) {
  const a = document.createElement("a");
  a.className = "car";
  a.href = g.url;
  a.target = "_blank";
  a.rel = "noopener";
  const img = new Image(128, 96);
  img.className = "photo";
  img.alt = "";
  img.loading = "lazy";
  img.referrerPolicy = "no-referrer";
  img.src = g.img || PLACEHOLDER;
  img.onerror = () => { img.onerror = null; img.src = PLACEHOLDER; };
  const body = document.createElement("div");
  const h = document.createElement("h2");
  h.textContent = g.name;
  const p = document.createElement("p");
  p.className = "price";
  p.textContent = span(g.minPrice, g.maxPrice, kr);
  const d = document.createElement("p");
  d.className = "details";
  d.textContent = [
    `${g.count} ${g.count === 1 ? "bil" : "biler"}`,
    span(g.minYear, g.maxYear, String),
    span(g.minKm, g.maxKm, km),
    g.fuels.map((f) => FUELS[f]).join("/") || null,
    g.minRange != null ? span(g.minRange, g.maxRange, km) + " rækkevidde" : null,
  ].filter(Boolean).join(" · ");
  body.append(h, p, d);
  a.append(img, body);
  return a;
}

function render() {
  const f = readForm();
  writeUrl(f);
  const groups = group(listings, f, modelPhotos);
  const matching = groups.reduce((n, g) => n + g.count, 0);
  $("#summary").textContent = `${nf.format(groups.length)} modeller · ${nf.format(matching)} af ${nf.format(listings.length)} annoncer matcher`;
  const list = $("#results");
  list.replaceChildren(...groups.slice(0, shown).map(card));
  if (!groups.length) list.innerHTML = `<p class="empty">Ingen biler matcher filtrene.</p>`;
  $("#more").hidden = groups.length <= shown;
}

// ---- Wiring ---------------------------------------------------------------------------

let timer;
form.addEventListener("input", (e) => {
  if (e.target === form.brand) fillModels();
  shown = PAGE;
  clearTimeout(timer);
  timer = setTimeout(render, e.target.tagName === "INPUT" && e.target.type !== "checkbox" && e.target.type !== "radio" ? 200 : 0);
});
form.addEventListener("reset", () => setTimeout(() => { fillModels(); shown = PAGE; render(); }));
form.addEventListener("submit", (e) => e.preventDefault());
$("#more").addEventListener("click", () => { shown += PAGE; render(); });
function setCollapsed(collapsed) {
  form.classList.toggle("collapsed", collapsed);
  $("#toggle").textContent = collapsed ? "Vis filtre" : "Skjul filtre";
  $("#toggle").setAttribute("aria-expanded", String(!collapsed));
}
$("#toggle").addEventListener("click", () => setCollapsed(!form.classList.contains("collapsed")));
// On phones the filters fill the screen, so start with them folded away.
if (matchMedia("(max-width: 760px)").matches) setCollapsed(true);

try {
  const res = await fetch("data/listings.json", { cache: "no-cache" });
  if (!res.ok) throw new Error(res.status);
  const data = await res.json();
  modelPhotos = data.modelPhotos || {};
  listings = data.rows.map(([id, make, model, price, year, km, range, fuel, gear, img, imgCount]) =>
    ({ id, make, model, price, year, km, range, fuel, gear, img, imgCount }));
  const when = new Date(data.updated).toLocaleString("da-DK", { dateStyle: "medium", timeStyle: "short" });
  $("#footer").textContent = `${nf.format(listings.length)} annoncer hentet fra bilbasen.dk ${when}. `
    + "Ikke tilknyttet Bilbasen. Tryk på en model for at se annoncerne på bilbasen.dk.";
  fillBrands();
  readUrl();
  render();
} catch (e) {
  $("#summary").textContent = "Kunne ikke hente bildata. Prøv igen om lidt.";
  console.error(e);
}
