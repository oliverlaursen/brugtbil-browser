// Unit tests for site/core.mjs (run: node --test scraper/)
import test from "node:test";
import assert from "node:assert/strict";
import { parsePage, group, baseModel, parseRange, parseKm, parsePrice } from "../site/core.mjs";

const card = (id, model, text, images = ["https://img/a.jpg", "https://img/b.jpg"]) =>
  ({ href: `https://www.bilbasen.dk/brugt/bil/vw/${model}/x/${id}`, title: "VW", text, images });

test("reads a real Bilbasen card", () => {
  const [l] = parsePage({ cards: [card("7048024", "id4",
    "VW ID.4\n77 1ST Max 5d\n204.900 kr.\n6/2021\n133.000 km\n490 km rækkevidde\nEl")] });
  assert.deepEqual([l.price, l.year, l.km, l.range, l.fuel, l.gear], [204900, 2021, 133000, 490, "electric", "auto"]);
});

test("monthly payments, range and fuel economy are not prices or mileage", () => {
  assert.equal(parsePrice("3.499 kr./md.\n129.900 kr."), 129900);
  assert.equal(parseKm("18,5 km/l\n450 km rækkevidde\n45.000 km"), 45000);
  assert.equal(parseRange("Rækkevidde: 420 km"), 420);
});

test("JSON-LD photo list wins and generations merge", () => {
  const ld = JSON.stringify({ "@type": "ItemList", itemListElement: [{ "@type": "Product",
    image: ["https://img/ld1.jpg", "https://img/ld2.jpg"], url: "https://www.bilbasen.dk/brugt/bil/vw/golf-viii/x/7000001",
    offers: { price: 199000 } }] });
  const ls = parsePage({ cards: [card("7000001", "golf-viii", "2021\n50.000 km\nBenzin", ["https://img/logo.jpg"]),
                                 card("7000002", "golf-vii", "189.000 kr.\n2017\n90.000 km\nDiesel")], jsonld: [ld] });
  assert.equal(ls.find((l) => l.id === "7000001").img, "https://img/ld1.jpg");
  assert.equal(baseModel("golf-viii"), "golf");
  const [g] = group(ls);
  assert.equal(g.name, "VW Golf");
  assert.deepEqual([g.count, g.minPrice, g.maxPrice], [2, 189000, 199000]);
});

test("filters and photo fallback", () => {
  const ls = [
    { id: "1", make: "vw", model: "id4", price: 200000, year: 2021, km: 50000, range: 490, fuel: "electric", gear: "auto", img: "https://img/1.jpg", imgCount: 9 },
    { id: "2", make: "vw", model: "id3", price: 150000, year: 2020, km: 80000, range: 350, fuel: "electric", gear: "auto", img: "https://x/missing.svg", imgCount: 1 },
    { id: "3", make: "vw", model: "golf", price: 90000, year: 2015, km: 150000, range: null, fuel: "petrol", gear: "manual", img: "https://img/3.jpg", imgCount: 1 },
  ];
  assert.deepEqual(group(ls, { rangeFrom: 400 }).map((g) => g.model), ["id4"]);
  assert.deepEqual(group(ls, { gear: "manual", kmTo: 200000 }).map((g) => g.model), ["golf"]);
  assert.deepEqual(group(ls, { fuels: ["electric"], priceTo: 160000 }).map((g) => g.model), ["id3"]);
  const id3 = group(ls, {}, { "vw/id3": "https://img/real.jpg" }).find((g) => g.model === "id3");
  assert.equal(id3.img, "https://img/real.jpg");
});
