"use strict";

const REPO = "nurkert/porticle-gun";
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* pixel icons, 16 x 16, one character per pixel */
const ICON_COLOURS = {
  G: "#e4e4e4", R: "#d83a3a", r: "#8f1f1f",
  d: "#5e3f1d", w: "#c19a5b",
  k: "#1e1e1e", K: "#4a4a4a", W: "#dddddd", g: "#3fbf3f", x: "#e04040", b: "#4070e0", y: "#e0c040",
  B: "#7a4a22", o: "#a8723a", L: "#d9d9d9",
  c: "#4a2a12", C: "#7a4a22", Y: "#e0b040", P: "#f2ead8", p: "#cfc3a8", n: "#5aa0e0",
};

const ICONS = {
  magnet: [
    "................", "..GGG......GGG..", "..GGG......GGG..", "..RRR......RRR..", "..RRR......RRR..",
    "..RRR......RRR..", "..RRR......RRR..", "..RRR......RRR..", "..RRRr....rRRR..", "..rRRRRrrRRRRr..",
    "...rRRRRRRRRr...", "....rrRRRRrr....", "......rrrr......", "................", "................",
    "................",
  ],
  table: Array.from({ length: 16 }, (_, y) =>
    Array.from({ length: 16 }, (_, x) => (x % 5 === 0 || y % 5 === 0 ? "d" : "w")).join("")),
  pad: [
    "................", "................", "................", "..kkkkkkkkkkkk..", ".kKKKKKKKKKKKKk.",
    ".kKWKKKKKKKgKKk.", ".kWWWKKKKKxKbKk.", ".kKWKKKKKKKyKKk.", ".kKKKKkkkkKKKKk.", ".kKKKk....kKKKk.",
    "..kkk......kkk..", "................", "................", "................", "................",
    "................",
  ],
  chest: [
    "................", ".kkkkkkkkkkkkkk.", ".kBBBBBBBBBBBBk.", ".kBooooooooooBk.", ".kBooooooooooBk.",
    ".kkkkkkLLkkkkkk.", ".kBBBBBLLBBBBBk.", ".kBooooLLooooBk.", ".kBooooooooooBk.", ".kBooooooooooBk.",
    ".kBooooooooooBk.", ".kBooooooooooBk.", ".kBBBBBBBBBBBBk.", ".kkkkkkkkkkkkkk.", "................",
    "................",
  ],
  book: [
    "................", "..cccccccccccP..", "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..", "..cCCYYYYCCCcP..",
    "..cCCCCCCCCCcP..", "..cCCYYYYYCCcP..", "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..",
    "..cCCCCCCCCCcP..", "..cCCCCCCCCCcP..", "..cccccccccccP..", "...PPPPPPPPPPP..", "................",
    "................",
  ],
  paper: [
    "................", "...PPPPPPPPP....", "...PppppppPPP...", "...PPPPPPPPPPP..", "...PppppppppPP..",
    "...PPPPPPPPPPP..", "...PpppppppPPP..", "...PPPPPPPPPPP..", "...PppppppppPP..", "...PPPPPPPPPPP..",
    "...PpppppPPPPP..", "...PPPPPPPPPPP..", "...PPPPPPPPPPP..", "................", "................",
    "................",
  ],
  bedrock: [
    "................", "...PPPPPPPPP....", "...PnnnnnnnPP...", "...PPPPPPPPPPP..", "...PnnnnnnnnPP..",
    "...PPPPPPPPPPP..", "...PnnnnnnnPPP..", "...PPPPPPPPPPP..", "...PnnnnnnnnPP..", "...PPPPPPPPPPP..",
    "...PnnnnnPPPPP..", "...PPPPPPPPPPP..", "...PPPPPPPPPPP..", "................", "................",
    "................",
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

document.querySelectorAll("svg[data-icon]").forEach(svg => drawIcon(svg, svg.dataset.icon));

/* tooltips: the game's purple item tooltip, following the pointer */
const tooltip = document.getElementById("tooltip");

function fillTooltip(el) {
  const [name, colour] = el.dataset.tip.split("|");
  const lore = el.dataset.lore ? el.dataset.lore.split("|") : [];
  tooltip.innerHTML = "";
  const title = document.createElement("strong");
  title.textContent = name;
  if (colour) title.className = colour;
  tooltip.append(title, ...lore.map(line => Object.assign(document.createElement("div"), { textContent: line })));
  tooltip.hidden = false;
}

function placeTooltip(x, y) {
  const { width, height } = tooltip.getBoundingClientRect();
  let left = x + 16, top = y - height - 8;
  if (left + width > innerWidth - 8) left = x - width - 16;
  if (top < 8) top = y + 20;
  tooltip.style.left = `${Math.max(8, left)}px`;
  tooltip.style.top = `${top}px`;
}

function bindTooltip(el) {
  if (!el.hasAttribute("tabindex") && !/^(A|BUTTON)$/.test(el.tagName)) el.tabIndex = 0;
  el.addEventListener("pointerenter", event => { if (event.pointerType === "mouse") fillTooltip(el); });
  el.addEventListener("pointermove", event => { if (!tooltip.hidden) placeTooltip(event.clientX, event.clientY); });
  el.addEventListener("pointerleave", () => { tooltip.hidden = true; });
  el.addEventListener("focus", () => {
    fillTooltip(el);
    const box = el.getBoundingClientRect();
    placeTooltip(box.right - 20, box.top + 10);
  });
  el.addEventListener("blur", () => { tooltip.hidden = true; });
}

document.querySelectorAll("[data-tip]").forEach(bindTooltip);

/* hotbar: the selected slot follows the scroll, the held item's name fades in above it */
const chapters = [...document.querySelectorAll("[data-slot]")];
const hots = [...document.querySelectorAll(".hot")];
const held = document.querySelector("[data-held]");
const xp = document.querySelector("[data-xp]");
const level = document.querySelector("[data-level]");
let current = -1;
let heldTimer;

function select(slot) {
  if (slot === current) return;
  const first = current === -1;
  current = slot;
  hots.forEach((hot, i) => {
    hot.classList.toggle("active", i === Math.max(0, slot - 1));
    if (i === Math.max(0, slot - 1)) hot.setAttribute("aria-current", "true");
    else hot.removeAttribute("aria-current");
  });
  level.textContent = slot;
  if (first || slot === 0) return;
  held.textContent = hots[slot - 1].dataset.name;
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
  if (n >= 1 && n <= hots.length) {
    hots[n - 1].click();
  }
});

/* the preview boxes lean toward the cursor, like the player model in the inventory */
const previews = [...document.querySelectorAll(".preview img")];
if (!reducedMotion) {
  addEventListener("pointermove", event => {
    if (event.pointerType !== "mouse") return;
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

/* windows settle in as they come into view; the brewing arrows run while visible */
const windows = document.querySelectorAll(".chapter .window");
if ("IntersectionObserver" in window) {
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) entry.target.classList.add("open");
      entry.target.classList.toggle("visible", entry.isIntersecting);
    }
  }, { threshold: 0.12 });
  windows.forEach(w => observer.observe(w));
} else {
  windows.forEach(w => w.classList.add("open", "visible"));
}

/* colour picker */
const colourGun = document.querySelector("[data-colour-gun]");
const swatches = [...document.querySelectorAll(".swatch")];
swatches.forEach((swatch, i) => {
  swatch.addEventListener("click", () => {
    swatches.forEach(s => s.setAttribute("aria-checked", String(s === swatch)));
    colourGun.classList.add("swap");
    setTimeout(() => {
      colourGun.style.backgroundPositionX = `${(Number(swatch.dataset.index) * 100) / 3}%`;
      colourGun.setAttribute("aria-label", `The PorticleGun with ${swatch.dataset.label} lights`);
      colourGun.classList.remove("swap");
    }, reducedMotion ? 0 : 160);
  });
  swatch.addEventListener("keydown", event => {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
    if (!step) return;
    event.preventDefault();
    const next = swatches[(i + step + swatches.length) % swatches.length];
    next.focus();
    next.click();
  });
});

/* tabs: recipes switch panels, platforms switch the key names */
document.querySelectorAll('[role="tablist"]').forEach(list => {
  const tabs = [...list.querySelectorAll('[role="tab"]')];
  function activate(tab) {
    tabs.forEach(t => {
      const on = t === tab;
      t.setAttribute("aria-selected", String(on));
      t.tabIndex = on ? 0 : -1;
      if (!t.dataset.platform) document.getElementById(t.getAttribute("aria-controls")).hidden = !on;
    });
    if (tab.dataset.platform) {
      const keys = document.getElementById(tab.getAttribute("aria-controls"));
      keys.setAttribute("aria-labelledby", tab.id);
      keys.querySelectorAll("dd").forEach(dd => { dd.textContent = dd.dataset[tab.dataset.platform]; });
      keys.classList.remove("swap");
      void keys.offsetWidth;
      keys.classList.add("swap");
    }
  }
  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => activate(tab));
    tab.addEventListener("keydown", event => {
      const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
      if (!step) return;
      const next = tabs[(i + step + tabs.length) % tabs.length];
      next.focus();
      activate(next);
    });
  });
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

/* latest release: version on the buttons, direct jar download, release files in the chest */
function megabytes(bytes) {
  return bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

const ASSET_KINDS = [
  { test: /^PorticleGun-[\d.]+\.jar$/, icon: "img", name: "Plugin", colour: "gold", lore: "Goes into the plugins folder" },
  { test: /resourcepack.*\.zip$/, icon: "paper", name: "Java resource pack", colour: "aqua", lore: "Sent to players automatically" },
  { test: /\.mcpack$/, icon: "bedrock", name: "Bedrock resource pack", colour: "aqua", lore: "Installed into Geyser automatically" },
  { test: /mappings.*\.json$/, icon: "book", name: "Geyser mapping", colour: "aqua", lore: "For Geyser on a proxy" },
];

fetch(`https://api.github.com/repos/${REPO}/releases/latest`)
  .then(response => (response.ok ? response.json() : null))
  .then(release => {
    if (!release || !release.tag_name) return;
    document.querySelectorAll("[data-version]").forEach(el => { el.textContent = release.tag_name; });
    const assets = release.assets || [];
    const jar = assets.find(a => ASSET_KINDS[0].test.test(a.name));
    if (jar) {
      document.querySelectorAll(".download-jar").forEach(a => {
        a.href = jar.browser_download_url;
        a.setAttribute("download", "");
      });
    }
    const date = new Date(release.published_at).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
    document.querySelector("[data-release-note]").textContent =
      `${release.tag_name}, released ${date}${jar ? ` · ${megabytes(jar.size)}` : ""} · straight from GitHub`;

    const grid = document.querySelector("[data-assets]");
    const all = grid.firstElementChild;
    const slots = [];
    for (const kind of ASSET_KINDS) {
      const asset = assets.find(a => kind.test.test(a.name));
      if (!asset) continue;
      const link = document.createElement("a");
      link.className = "slot asset";
      link.href = asset.browser_download_url;
      link.dataset.tip = `${kind.name}|${kind.colour}`;
      link.dataset.lore = `${asset.name}|${megabytes(asset.size)}|${kind.lore}`;
      link.setAttribute("aria-label", `Download ${kind.name}, ${asset.name}`);
      if (kind.icon === "img") {
        link.innerHTML = '<img src="images/portal_gun_3d.png" alt="">';
      } else {
        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("aria-hidden", "true");
        drawIcon(svg, kind.icon);
        link.append(svg);
      }
      bindTooltip(link);
      slots.push(link);
    }
    grid.prepend(...slots);
    const columns = getComputedStyle(grid).gridTemplateColumns.split(" ").length;
    const filled = slots.length + 1;
    for (let i = filled; i < Math.ceil(filled / columns) * columns; i++) {
      grid.insertBefore(Object.assign(document.createElement("span"), { className: "slot asset empty" }), all);
    }
  })
  .catch(() => {});
