"use strict";

const REPO = "nurkert/porticle-gun";
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const touch = window.matchMedia("(hover: none)").matches;
const DE = window.PG_DE || {};
const TEXT = window.PG_TEXT;
let lang = "en";

function text() {
  return TEXT[lang];
}

function fill(template, values) {
  return template.replace(/\{(\w+)\}/g, (_, key) => values[key] ?? "");
}

/* pixel icons, 16 x 16, one character per pixel */
const ICON_COLOURS = {
  G: "#e4e4e4", R: "#d83a3a", r: "#8f1f1f",
  d: "#5e3f1d", w: "#c19a5b",
  k: "#1e1e1e", K: "#4a4a4a", W: "#dddddd", g: "#3fbf3f", x: "#e04040", b: "#4070e0", y: "#e0c040",
  B: "#7a4a22", o: "#a8723a", L: "#d9d9d9",
  c: "#4a2a12", C: "#7a4a22", Y: "#e0b040", P: "#f2ead8", p: "#cfc3a8", n: "#5aa0e0",
  O: "#3f76e4", l: "#5cb83a", A: "#55d6ff", a: "#2a8fb8", S: "#ecebdc", s: "#b8b6a4",
};

const ICONS = {
  magnet: [
    "................", "..GGG......GGG..", "..GGG......GGG..", "..RRR......RRR..", "..RRR......RRR..",
    "..RRR......RRR..", "..RRR......RRR..", "..RRR......RRR..", "..RRRr....rRRR..", "..rRRRRrrRRRRr..",
    "...rRRRRRRRRr...", "....rrRRRRrr....", "......rrrr......",
  ],
  table: Array.from({ length: 16 }, (_, y) =>
    Array.from({ length: 16 }, (_, x) => (x % 5 === 0 || y % 5 === 0 ? "d" : "w")).join("")),
  pad: [
    "................", "................", "................", "..kkkkkkkkkkkk..", ".kKKKKKKKKKKKKk.",
    ".kKWKKKKKKKgKKk.", ".kWWWKKKKKxKbKk.", ".kKWKKKKKKKyKKk.", ".kKKKKkkkkKKKKk.", ".kKKKk....kKKKk.",
    "..kkk......kkk..",
  ],
  chest: [
    "................", ".kkkkkkkkkkkkkk.", ".kBBBBBBBBBBBBk.", ".kBooooooooooBk.", ".kBooooooooooBk.",
    ".kkkkkkLLkkkkkk.", ".kBBBBBLLBBBBBk.", ".kBooooLLooooBk.", ".kBooooooooooBk.", ".kBooooooooooBk.",
    ".kBooooooooooBk.", ".kBooooooooooBk.", ".kBBBBBBBBBBBBk.", ".kkkkkkkkkkkkkk.",
  ],
  book: [
    "................", "..cccccccccccP..", "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..", "..cCCYYYYCCCcP..",
    "..cCCCCCCCCCcP..", "..cCCYYYYYCCcP..", "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..",
    "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..", "..cccccccccccP..", "...PPPPPPPPPPP..",
  ],
  paper: [
    "................", "...PPPPPPPPP....", "...PppppppPPP...", "...PPPPPPPPPPP..", "...PppppppppPP..",
    "...PPPPPPPPPPP..", "...PpppppppPPP..", "...PPPPPPPPPPP..", "...PppppppppPP..", "...PPPPPPPPPPP..",
    "...PpppppPPPPP..", "...PPPPPPPPPPP..", "...PPPPPPPPPPP..",
  ],
  bedrock: [
    "................", "...PPPPPPPPP....", "...PnnnnnnnPP...", "...PPPPPPPPPPP..", "...PnnnnnnnnPP..",
    "...PPPPPPPPPPP..", "...PnnnnnnnPPP..", "...PPPPPPPPPPP..", "...PnnnnnnnnPP..", "...PPPPPPPPPPP..",
    "...PnnnnnPPPPP..", "...PPPPPPPPPPP..", "...PPPPPPPPPPP..",
  ],
  globe: [
    "................", ".....OOOOOO.....", "...OOllOOOOOO...", "..OOlllOOOllOO..", "..OllllOOllllO..",
    ".OOlllOOOlllOOO.", ".OOOllOOOOllOOO.", ".OOOOOOOOOOlOOO.", ".OOOOllOOOOOOOO.", ".OOOlllllOOOOOO.",
    "..OOllllOOOOlO..", "..OOOlllOOOllO..", "...OOOlOOOOOO...", ".....OOOOOO.....",
  ],
  bed: [
    "................", "................", "................", "................", "................",
    "..WWW...........", "..WWWRRRRRRRRR..", "..WWWRRRRRRRRR..", ".dddddddddddddd.", ".dddddddddddddd.",
    ".d............d.", ".d............d.",
  ],
  house: [
    "................", ".......rr.......", "......rrrr......", ".....rrrrrr.....", "....rrrrrrrr....",
    "...rrrrrrrrrr...", "..rrrrrrrrrrrr..", "...wwwwwwwwww...", "...wwwwwwwwww...", "...wwnnwwddww...",
    "...wwnnwwddww...", "...wwwwwwddww...", "...wwwwwwddww...", "...wwwwwwddww...",
  ],
  back: [
    "................", "................", "......A.........", ".....AA.........", "....AAAAAAAAA...",
    "...AAAAAAAAAAA..", "....AAAAAAAAAAa.", ".....AA......Aa.", "......A......Aa.", ".............Aa.",
    "..........AAAAa.", "..........AAAa..",
  ],
  skull: [
    "................", "....SSSSSSSS....", "...SSSSSSSSSS...", "..SSSSSSSSSSSS..", "..SSkkkSSkkkSS..",
    "..SSkkkSSkkkSS..", "..SSkkkSSkkkSS..", "..SSSSSSSSSSSS..", "...SSSSkkSSSS...", "....SSSSSSSS....",
    "....SsSsSsSs....", "....ssssssss....",
  ],
};

function drawIcon(svg, name) {
  const rows = ICONS[name];
  if (!rows) return;
  svg.setAttribute("viewBox", "0 0 16 16");
  svg.setAttribute("shape-rendering", "crispEdges");
  svg.innerHTML = rows.flatMap((row, y) => [...row].map((ch, x) =>
    ICON_COLOURS[ch] ? `<rect x="${x}" y="${y}" width="1" height="1" fill="${ICON_COLOURS[ch]}"/>` : "")).join("");
}

function icon(name) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("aria-hidden", "true");
  drawIcon(svg, name);
  return svg;
}

document.querySelectorAll("svg[data-icon]").forEach(svg => drawIcon(svg, svg.dataset.icon));

/* tooltips: the game's purple item tooltip, following the pointer; a tap shows it on touch screens */
const tooltip = document.getElementById("tooltip");
let tipOwner = null;

function fillTooltip(el) {
  const [name, colour] = el.dataset.tip.split("|");
  const lore = el.dataset.lore ? el.dataset.lore.split("|") : [];
  tooltip.innerHTML = "";
  const title = document.createElement("strong");
  title.textContent = name;
  if (colour) title.className = colour;
  tooltip.append(title, ...lore.map(line => Object.assign(document.createElement("div"), { textContent: line })));
  tooltip.hidden = false;
  tipOwner = el;
}

function hideTooltip() {
  tooltip.hidden = true;
  tipOwner = null;
}

function placeTooltip(x, y) {
  const { width, height } = tooltip.getBoundingClientRect();
  let left = x + 16, top = y - height - 8;
  if (left + width > innerWidth - 8) left = x - width - 16;
  if (top < 8) top = y + 20;
  tooltip.style.left = `${Math.max(8, Math.min(left, innerWidth - width - 8))}px`;
  tooltip.style.top = `${top}px`;
}

function placeNear(el) {
  const box = el.getBoundingClientRect();
  placeTooltip(box.left + box.width / 2, box.top + 12);
}

function bindTooltip(el) {
  if (!el.hasAttribute("tabindex") && !/^(A|BUTTON)$/.test(el.tagName)) el.tabIndex = 0;
  el.addEventListener("pointerenter", event => {
    if (event.pointerType !== "mouse") return;
    fillTooltip(el);
    placeTooltip(event.clientX, event.clientY);
  });
  el.addEventListener("pointermove", event => {
    if (event.pointerType === "mouse" && tipOwner === el) placeTooltip(event.clientX, event.clientY);
  });
  el.addEventListener("pointerleave", event => { if (event.pointerType === "mouse") hideTooltip(); });
  el.addEventListener("click", () => {
    if (!touch || /^(A|BUTTON)$/.test(el.tagName)) return;
    if (tipOwner === el) return hideTooltip();
    fillTooltip(el);
    placeNear(el);
  });
  el.addEventListener("focus", () => { if (!touch) { fillTooltip(el); placeNear(el); } });
  el.addEventListener("blur", hideTooltip);
}

document.querySelectorAll("[data-tip]").forEach(bindTooltip);
addEventListener("scroll", () => { if (touch && tipOwner) hideTooltip(); }, { passive: true });

/* stagger: list items pop in one after another once their window opens */
document.querySelectorAll(".destinations, .steps, .facts, .atlas ul, .doc-buttons, .swatches, .asset-list").forEach(list => {
  list.classList.add("stagger");
  [...list.children].forEach((child, i) => child.style.setProperty("--i", i));
});

/* hotbar: the selected slot follows the scroll, the held item's name fades in above it */
const chapters = [...document.querySelectorAll("[data-slot]")];
const hots = [...document.querySelectorAll(".hot")];
const held = document.querySelector("[data-held]");
const xp = document.querySelector("[data-xp]");
const level = document.querySelector("[data-level]");
let current = -1;
let heldTimer;

function hotName(i) {
  return hots[i].querySelector(".sr").textContent;
}

function select(slot) {
  if (slot === current) return;
  const first = current === -1;
  current = slot;
  const index = Math.max(0, slot - 1);
  hots.forEach((hot, i) => {
    hot.classList.toggle("active", i === index);
    if (i === index) hot.setAttribute("aria-current", "true");
    else hot.removeAttribute("aria-current");
  });
  level.textContent = slot;
  level.classList.remove("pop");
  void level.offsetWidth;
  level.classList.add("pop");
  if (first || slot === 0) return;
  held.textContent = hotName(index);
  held.classList.add("show");
  clearTimeout(heldTimer);
  heldTimer = setTimeout(() => held.classList.remove("show"), 1800);
}

function onScroll() {
  const max = document.documentElement.scrollHeight - innerHeight;
  xp.style.setProperty("--progress", max > 0 ? Math.min(1, scrollY / max) : 0);
  const line = innerHeight * 0.45;
  let slot = 0;
  for (const chapter of chapters) {
    if (chapter.getBoundingClientRect().top <= line) slot = Number(chapter.dataset.slot);
  }
  if (scrollY >= max - 4) slot = chapters.length - 1;
  select(slot);
}

let ticking = false;
addEventListener("scroll", () => {
  if (ticking) return;
  ticking = true;
  requestAnimationFrame(() => { onScroll(); ticking = false; });
}, { passive: true });
addEventListener("resize", onScroll);
onScroll();

/* number keys 1 to 9 jump between sections, like switching hotbar slots */
addEventListener("keydown", event => {
  if (event.altKey || event.ctrlKey || event.metaKey || /INPUT|TEXTAREA/.test(event.target.tagName)) return;
  const n = Number(event.key);
  if (n >= 1 && n <= hots.length) hots[n - 1].click();
});

/* the preview boxes lean toward the cursor, like the player model in the inventory */
const previews = [...document.querySelectorAll(".preview img")];
if (!reducedMotion && !touch) {
  addEventListener("pointermove", event => {
    for (const img of previews) {
      const box = img.parentElement.getBoundingClientRect();
      if (box.bottom < 0 || box.top > innerHeight) continue;
      const dx = (event.clientX - (box.left + box.width / 2)) / innerWidth;
      const dy = (event.clientY - (box.top + box.height / 2)) / innerHeight;
      img.style.setProperty("--ry", `${Math.max(-1, Math.min(1, dx * 1.6)) * 28}deg`);
      img.style.setProperty("--rx", `${Math.max(-1, Math.min(1, dy * 1.6)) * -14}deg`);
    }
  }, { passive: true });
}

/* windows settle in as they come into view; the brewing arrows and recipe cycling run only while visible */
const windows = document.querySelectorAll(".chapter .window");
const visible = new Set();
if ("IntersectionObserver" in window) {
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) entry.target.classList.add("open");
      entry.target.classList.toggle("visible", entry.isIntersecting);
      if (entry.isIntersecting) visible.add(entry.target);
      else visible.delete(entry.target);
    }
    recipeCycle.update();
  }, { threshold: 0.15 });
  windows.forEach(w => observer.observe(w));
} else {
  windows.forEach(w => { w.classList.add("open", "visible"); visible.add(w); });
}

/* colour picker */
const colourGun = document.querySelector("[data-colour-gun]");
const swatches = [...document.querySelectorAll(".swatch")];
const swatchName = document.querySelector("[data-swatch-name]");
let colourIndex = 0;

function showColours(index, animate) {
  colourIndex = index;
  swatches.forEach((s, i) => s.setAttribute("aria-checked", String(i === index)));
  const name = text().swatch[index];
  swatchName.textContent = name;
  const apply = () => {
    colourGun.style.backgroundPositionX = `${(index * 100) / 3}%`;
    colourGun.setAttribute("aria-label", fill(text().gunLabel, { colours: name }));
    colourGun.classList.remove("swap");
  };
  if (!animate || reducedMotion) return apply();
  colourGun.classList.add("swap");
  setTimeout(apply, 170);
}

swatches.forEach((swatch, i) => {
  swatch.addEventListener("click", () => showColours(i, true));
  swatch.addEventListener("keydown", event => {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
    if (!step) return;
    event.preventDefault();
    const next = (i + step + swatches.length) % swatches.length;
    swatches[next].focus();
    showColours(next, true);
  });
});

/* controls: the key names per platform */
let platform = "java";
function showKeys(animate) {
  const keys = document.getElementById("keys");
  keys.querySelectorAll("dd").forEach(dd => { dd.textContent = text().keys[platform][dd.dataset.key]; });
  if (animate) {
    keys.classList.remove("swap");
    void keys.offsetWidth;
    keys.classList.add("swap");
  }
}

/* tabs: recipes switch panels and cycle on their own while visible, platforms switch the key names */
function setupTabs(list, onSelect) {
  const tabs = [...list.querySelectorAll('[role="tab"]')];
  function activate(tab, user) {
    tabs.forEach(t => {
      const on = t === tab;
      t.setAttribute("aria-selected", String(on));
      t.tabIndex = on ? 0 : -1;
    });
    onSelect(tab, user);
  }
  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => activate(tab, true));
    tab.addEventListener("keydown", event => {
      const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
      if (!step) return;
      const next = tabs[(i + step + tabs.length) % tabs.length];
      next.focus();
      activate(next, true);
    });
  });
  return { tabs, activate };
}

const recipeList = document.querySelector('#crafting [role="tablist"]');
const recipeTabs = setupTabs(recipeList, (tab, user) => {
  recipeTabs.tabs.forEach(t => {
    const panel = document.getElementById(t.getAttribute("aria-controls"));
    const on = t === tab;
    panel.hidden = !on;
    panel.classList.toggle("show", on);
  });
  if (user) recipeCycle.stop();
  else recipeCycle.restartTimer();
});

const recipeCycle = (() => {
  const CYCLE = 4500;
  let timer = null;
  let stopped = reducedMotion;
  const windowEl = recipeList.closest(".window");
  function next() {
    const tabs = recipeTabs.tabs;
    const i = tabs.findIndex(t => t.getAttribute("aria-selected") === "true");
    recipeTabs.activate(tabs[(i + 1) % tabs.length], false);
  }
  function restartTimer() {
    recipeList.classList.remove("cycling");
    void recipeList.offsetWidth;
    if (timer) recipeList.classList.add("cycling");
  }
  function update() {
    const run = !stopped && visible.has(windowEl) && !document.hidden && !windowEl.matches(":hover");
    if (run && !timer) {
      timer = setInterval(next, CYCLE);
      recipeList.style.setProperty("--cycle", `${CYCLE}ms`);
      restartTimer();
    } else if (!run && timer) {
      clearInterval(timer);
      timer = null;
      recipeList.classList.remove("cycling");
    }
  }
  function stop() {
    stopped = true;
    update();
  }
  windowEl.addEventListener("pointerenter", update);
  windowEl.addEventListener("pointerleave", update);
  document.addEventListener("visibilitychange", update);
  return { update, stop, restartTimer };
})();

setupTabs(document.querySelector('#controls [role="tablist"]'), tab => {
  platform = tab.dataset.platform;
  document.getElementById("keys").setAttribute("aria-labelledby", tab.id);
  showKeys(true);
});

/* xp orbs burst from the download buttons */
function burst(button) {
  if (reducedMotion || !button.animate) return;
  const box = button.getBoundingClientRect();
  for (let i = 0; i < 14; i++) {
    const orb = document.createElement("span");
    orb.className = "orb";
    orb.style.left = `${box.left + box.width / 2}px`;
    orb.style.top = `${box.top + box.height / 2}px`;
    document.body.append(orb);
    const angle = Math.random() * Math.PI * 2;
    const distance = 60 + Math.random() * 90;
    const dx = Math.cos(angle) * distance, dy = Math.sin(angle) * distance - 40;
    orb.animate([
      { transform: "translate(-50%, -50%) scale(1)", opacity: 1 },
      { transform: `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px)) scale(0.9)`, opacity: 1, offset: 0.55 },
      { transform: `translate(calc(-50% + ${dx * 0.4}px), calc(-50% + ${dy + 120}px)) scale(0.4)`, opacity: 0 },
    ], { duration: 900 + Math.random() * 300, easing: "cubic-bezier(0.16, 1, 0.3, 1)" }).onfinish = () => orb.remove();
  }
}

document.querySelectorAll(".download-jar").forEach(button => button.addEventListener("click", () => burst(button)));

/* recipes: every slot of the crafting images names its item on hover or tap, like the game's tooltips */
const ITEM_NAMES = {
  obsidian: ["Obsidian", "Obsidian"], beacon: ["Beacon", "Leuchtfeuer"], quartz: ["Block of Quartz", "Quarzblock"],
  pearl: ["Ender Pearl", "Enderperle"], crossbow: ["Crossbow", "Armbrust"], bottle: ["Glass Bottle", "Glasflasche"],
  glowstone: ["Glowstone Dust", "Glowstonestaub"], star: ["Nether Star", "Netherstern"], echo: ["Echo Shard", "Echoscherbe"],
  iron: ["Block of Iron", "Eisenblock"], compass: ["Compass", "Kompass"], eye: ["Eye of Ender", "Enderauge"],
  shard: ["Rift Shard", "Riss-Splitter", "purple"], fluid: ["Portal Fluid", "Portalflüssigkeit", "green"],
  gun: ["PorticleGun", "PorticleGun", "purple"], rgun: ["Interdimensional Gun", "Interdimensional Gun", "green"],
  rcompass: ["Rift Compass", "Riss-Kompass", "aqua"],
};
const RECIPES = {
  "recipe-gun": [["obsidian", "beacon", "obsidian", "quartz", "pearl", "quartz", "quartz", "crossbow", "quartz"], "gun"],
  "recipe-fluid": [["shard", "shard", "shard", "bottle", "glowstone", null, null, null, null], "fluid"],
  "recipe-rift": [["shard", "star", "shard", "echo", "gun", "echo", "iron", "fluid", "iron"], "rgun"],
  "recipe-compass": [[null, "shard", null, "shard", "compass", "shard", null, "eye", null], "rcompass"],
};
// slot boxes measured in the 1330 x 785 recipe images
const GRID_X = [95, 275, 455], GRID_Y = [170, 350, 530], SLOT = 160, RESULT_BOX = [995, 310, 240];

function renderRecipeSlots() {
  for (const [id, [grid, result]] of Object.entries(RECIPES)) {
    const panel = document.getElementById(id);
    if (!panel) continue;
    let art = panel.querySelector(".recipe-art");
    if (!art) {
      art = document.createElement("div");
      art.className = "recipe-art";
      const img = panel.querySelector("img");
      img.replaceWith(art);
      art.append(img);
    }
    art.querySelectorAll(".recipe-slot").forEach(slot => slot.remove());
    const place = (key, x, y, size) => {
      if (!key) return;
      const [en, de, colour] = ITEM_NAMES[key];
      const slot = document.createElement("span");
      slot.className = "recipe-slot";
      slot.style.cssText = `left:${(x / 1330) * 100}%;top:${(y / 785) * 100}%;width:${(size / 1330) * 100}%;height:${(size / 785) * 100}%`;
      slot.dataset.tip = `${lang === "de" ? de : en}|${colour || ""}`;
      slot.setAttribute("aria-label", lang === "de" ? de : en);
      slot.setAttribute("role", "img");
      art.append(slot);
      bindTooltip(slot);
    };
    grid.forEach((key, i) => place(key, GRID_X[i % 3], GRID_Y[Math.floor(i / 3)], SLOT));
    place(result, RESULT_BOX[0], RESULT_BOX[1], RESULT_BOX[2]);
  }
}

/* latest release: version on the buttons, direct jar download, release files in the chest */
const ASSET_KINDS = [
  { test: /^PorticleGun-[\d.]+\.jar$/, kind: "jar", icon: "img", colour: "gold" },
  { test: /resourcepack.*\.zip$/, kind: "zip", icon: "paper", colour: "aqua" },
  { test: /\.mcpack$/, kind: "mcpack", icon: "bedrock", colour: "aqua" },
  { test: /mappings.*\.json$/, kind: "json", icon: "book", colour: "aqua" },
];
let release = null;

function megabytes(bytes) {
  return bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function assetIcon(kind) {
  if (kind.icon !== "img") return icon(kind.icon);
  return Object.assign(document.createElement("img"), { src: "images/portal_gun_3d.png", alt: "" });
}

function renderChest() {
  const grid = document.querySelector("[data-assets]");
  const list = document.querySelector("[data-asset-list]");
  const assets = release ? release.assets || [] : [];
  const found = ASSET_KINDS.map(kind => ({ kind, asset: assets.find(a => kind.test.test(a.name)) })).filter(f => f.asset);
  grid.innerHTML = "";
  for (let i = 0; i < 27; i++) {
    const slot = document.createElement("span");
    slot.className = "slot";
    if (found[i]) slot.append(assetIcon(found[i].kind));
    grid.append(slot);
  }
  const all = list.lastElementChild;
  list.querySelectorAll("li[data-asset]").forEach(li => li.remove());
  found.forEach(({ kind, asset }, i) => {
    const [name, lore] = text().assets[kind.kind];
    const li = document.createElement("li");
    li.dataset.asset = "";
    li.style.setProperty("--i", i);
    const link = document.createElement("a");
    link.href = asset.browser_download_url;
    link.setAttribute("aria-label", fill(text().downloadFile, { name }));
    const label = document.createElement("span");
    label.innerHTML = `<b></b><small></small>`;
    label.querySelector("b").textContent = name;
    label.querySelector("small").textContent = `${asset.name} · ${lore}`;
    const size = Object.assign(document.createElement("span"), { className: "size", textContent: megabytes(asset.size) });
    link.append(assetIcon(kind), label, size);
    li.append(link);
    list.insertBefore(li, all);
  });
  all.style.setProperty("--i", found.length);
  if (!release) return;
  const jar = found.find(f => f.kind.kind === "jar");
  const date = new Date(release.published_at).toLocaleDateString(text().locale, { day: "numeric", month: "long", year: "numeric" });
  document.querySelector("[data-release-note]").textContent =
    fill(text().released, { tag: release.tag_name, date, size: jar ? megabytes(jar.asset.size) : "" });
}

fetch(`https://api.github.com/repos/${REPO}/releases/latest`)
  .then(response => (response.ok ? response.json() : null))
  .then(data => {
    if (!data || !data.tag_name) return;
    release = data;
    document.querySelectorAll("[data-version]").forEach(el => { el.textContent = data.tag_name; });
    const jar = (data.assets || []).find(a => ASSET_KINDS[0].test.test(a.name));
    if (jar) {
      document.querySelectorAll(".download-jar").forEach(a => {
        a.href = jar.browser_download_url;
        a.setAttribute("download", "");
      });
    }
    renderChest();
  })
  .catch(() => {});

/* language: English is in the page, German comes from i18n.js; the choice is remembered on this device */
const ATTRS = [["data-i18n-alt", "alt"], ["data-i18n-tip", "data-tip"], ["data-i18n-lore", "data-lore"], ["data-i18n-aria", "aria-label"]];

function applyLanguage(next) {
  lang = next === "de" ? "de" : "en";
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach(el => {
    if (el.dataset.en === undefined) el.dataset.en = el.innerHTML;
    const de = DE[el.dataset.i18n];
    el.innerHTML = lang === "de" && de ? de : el.dataset.en;
  });
  for (const [marker, attr] of ATTRS) {
    document.querySelectorAll(`[${marker}]`).forEach(el => {
      const store = `en${attr.replace(/[^a-z]/gi, "")}`;
      if (el.dataset[store] === undefined) el.dataset[store] = el.getAttribute(attr) || "";
      const de = DE[el.getAttribute(marker)];
      el.setAttribute(attr, lang === "de" && de ? de : el.dataset[store]);
    });
  }
  document.querySelectorAll("[data-lang]").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.lang === lang)));
  showColours(colourIndex, false);
  showKeys(false);
  renderChest();
  renderRecipeSlots();
  hideTooltip();
}

document.querySelectorAll("[data-lang]").forEach(button => button.addEventListener("click", () => {
  applyLanguage(button.dataset.lang);
  try { localStorage.setItem("pg-lang", lang); } catch (e) { /* storage blocked: the choice lasts this visit */ }
}));

let saved = null;
try { saved = localStorage.getItem("pg-lang"); } catch (e) { /* storage blocked */ }
const params = new URLSearchParams(location.search);
applyLanguage(params.get("lang") || saved || ((navigator.language || "").toLowerCase().startsWith("de") ? "de" : "en"));
