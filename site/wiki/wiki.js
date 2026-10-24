"use strict";

/* the wiki: search over every heading (search-index.js, written by scripts/build_wiki.py) and the "on this page" marker */

(function () {
  const input = document.getElementById("wiki-search");
  const list = document.getElementById("wiki-results");
  const index = window.WIKI_INDEX || [];
  let selected = -1;

  function normalise(value) {
    return value.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  }

  function search(query) {
    const words = normalise(query).split(/\s+/).filter(Boolean);
    if (!words.length) return [];
    const scored = [];
    for (const entry of index) {
      const heading = normalise(entry.h);
      const page = normalise(entry.t);
      if (!words.every((word) => heading.includes(word) || page.includes(word))) continue;
      let score = 0;
      for (const word of words) {
        if (heading.startsWith(word)) score += 3;
        else if (heading.includes(word)) score += 2;
        else score += 1;
      }
      scored.push({ entry, score });
    }
    return scored.sort((a, b) => b.score - a.score).slice(0, 12).map((item) => item.entry);
  }

  function show(results) {
    list.replaceChildren();
    selected = -1;
    if (!input.value.trim()) {
      list.hidden = true;
      return;
    }
    if (!results.length) {
      const empty = document.createElement("li");
      empty.className = "empty";
      empty.textContent = "Nothing found";
      list.append(empty);
    }
    for (const entry of results) {
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.href = `${entry.p}.html#${entry.a}`;
      link.textContent = entry.h;
      if (entry.h !== entry.t) {
        const page = document.createElement("small");
        page.textContent = entry.t;
        link.append(page);
      }
      item.append(link);
      list.append(item);
    }
    list.hidden = false;
  }

  function move(step) {
    const links = [...list.querySelectorAll("a")];
    if (!links.length) return;
    selected = (selected + step + links.length) % links.length;
    links.forEach((link, i) => link.setAttribute("aria-selected", String(i === selected)));
    links[selected].scrollIntoView({ block: "nearest" });
  }

  if (input && list) {
    input.addEventListener("input", () => show(search(input.value)));
    input.addEventListener("keydown", (event) => {
      if (event.key === "ArrowDown") { event.preventDefault(); move(1); }
      else if (event.key === "ArrowUp") { event.preventDefault(); move(-1); }
      else if (event.key === "Enter") {
        const links = list.querySelectorAll("a");
        const target = links[Math.max(selected, 0)];
        if (target) window.location.href = target.href;
      } else if (event.key === "Escape") {
        input.value = "";
        show([]);
      }
    });
    document.addEventListener("click", (event) => {
      if (!event.target.closest(".search")) list.hidden = true;
    });
    // "/" jumps to the search field, like on GitHub
    document.addEventListener("keydown", (event) => {
      if (event.key === "/" && document.activeElement !== input && !event.target.closest("input, textarea")) {
        event.preventDefault();
        input.focus();
      }
    });
  }

  // the page list starts folded on phones, so the page itself comes first
  const menu = document.querySelector(".nav-menu");
  if (menu && window.matchMedia("(max-width: 820px)").matches) menu.open = false;

  // "on this page": marks the section being read
  const links = [...document.querySelectorAll(".on-page a")];
  const targets = links.map((link) => document.getElementById(decodeURIComponent(link.hash.slice(1)))).filter(Boolean);
  if (targets.length && "IntersectionObserver" in window) {
    const visible = new Set();
    const observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) visible.add(entry.target);
        else visible.delete(entry.target);
      }
      const current = targets.find((target) => visible.has(target));
      if (current) {
        links.forEach((link) => link.classList.toggle("active", link.hash === `#${current.id}`));
      }
    }, { rootMargin: "-72px 0px -60% 0px" });
    targets.forEach((target) => observer.observe(target));
  }
})();
