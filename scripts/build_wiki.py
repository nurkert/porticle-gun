#!/usr/bin/env python3
"""Builds the wiki of the GitHub Pages site from the Markdown documentation.

Every page in docs/, the getting started and command sections of the README and the changelog become HTML pages in
<site>/wiki/, in the look of the website (site/style.css, site/wiki/wiki.css), with a sidebar, a search over every
heading and links between the pages rewritten from .md to .html. Run by .github/workflows/pages.yml on every change to
the documentation; locally:

    pip install markdown==3.7
    python3 scripts/build_wiki.py _site        # then open _site/wiki/index.html
"""

import html
import json
import os
import re
import sys

import markdown

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "https://github.com/nurkert/porticle-gun"
BLOB = f"{REPO}/blob/main/"

# the sidebar: groups of (page, title, source, short description); a source is a file or (README, [section, ...])
PAGES = [
    ("Start", [
        ("index", "Overview", None, "What PorticleGun is and where everything is written down"),
        ("getting-started", "Getting started", ("README.md", ["Quick start", "Controls"]),
         "Installing, crafting the gun and the controls on Java and Bedrock"),
        ("commands", "Commands and permissions", ("README.md", ["Commands and permissions"]),
         "Every command, every permission and the /porticlegun menu"),
    ]),
    ("Guns", [
        ("gravity-gun", "Gravity gun", "docs/gravity-gun.md",
         "Carrying blocks and mobs, what travels with a block, where it is put down"),
        ("interdimensional-gun", "Interdimensional gun", "docs/interdimensional-gun.md",
         "Destinations, placement, tank, drops and recipes"),
    ]),
    ("Server", [
        ("configuration", "Configuration", "docs/configuration.md", "Every option in config.yml, custom portal colours"),
        ("resourcepack", "Resource pack and 3D model", "docs/resourcepack.md",
         "How the model and the drawn menus are delivered, self-hosting"),
        ("bedrock", "Bedrock and Geyser", "docs/bedrock.md", "Setup, proxies, what Bedrock players get"),
        ("worldguard", "WorldGuard", "docs/worldguard.md", "Region flags for portals and the gravity gun"),
        ("localization", "Languages", "docs/localization.md", "Editing texts, adding languages"),
    ]),
    ("Project", [
        ("changelog", "Changelog", "CHANGELOG.md", "Every release and what changed"),
        ("development", "Development", "docs/development.md", "Building, tests, smoke tests, releasing"),
    ]),
]
ALL = [page for _, pages in PAGES for page in pages]


def slugify(value, separator="-"):
    """GitHub's heading anchors, so links like configuration.md#gravity-gun keep working."""
    value = re.sub(r"<[^>]+>", "", value).strip().lower()
    value = re.sub(r"[^\w\- ]", "", value)
    return value.replace(" ", separator)


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as file:
        return file.read()


def readme_sections(titles):
    """The README's level-2 sections with these titles, in their order, without the divider images."""
    text = read("README.md")
    parts = re.split(r"(?m)^## ", text)
    chosen = []
    for title in titles:
        for part in parts[1:]:
            if part.split("\n", 1)[0].strip() == title:
                chosen.append("## " + part)
    body = "\n".join(chosen)
    return re.sub(r'<p align="center"><img src="images/divider\.png"[^>]*></p>\s*', "", body)


# where the README's sections ended up, for links like README.md#controls
README_ANCHORS = {}
for page, _, source, _ in ALL:
    if isinstance(source, tuple):
        for section in re.findall(r"(?m)^#{2,3} (.+)$", readme_sections(source[1])):
            README_ANCHORS[slugify(section)] = page
DOC_PAGES = {source: page for page, _, source, _ in ALL if isinstance(source, str)}


def rewrite_link(target, source_dir):
    """A link as the wiki needs it: other pages as .html, images from the site, everything else on GitHub."""
    if re.match(r"^(https?:|mailto:|#)", target):
        return target
    path, _, anchor = target.partition("#")
    if re.fullmatch(r"[\w-]+\.html", path):
        return target  # already a wiki page (the overview's cards)
    resolved = os.path.normpath(os.path.join(source_dir, path)) if path else ""
    if resolved == "README.md":
        page = README_ANCHORS.get(anchor)
        if page:
            return f"{page}.html#{anchor}"
        return f"index.html" if not anchor else f"{REPO}#{anchor}"
    if resolved in DOC_PAGES:
        return f"{DOC_PAGES[resolved]}.html" + (f"#{anchor}" if anchor else "")
    if resolved.startswith("images/"):
        return "../" + resolved
    return BLOB + resolved + (f"#{anchor}" if anchor else "")


def outdent_fences(text):
    """Python-Markdown cannot nest a fenced code block in a list item: such a block moves out of the list."""

    def outdent(match):
        indent = match.group(1)
        return "\n".join(line[len(indent):] if line.startswith(indent) else line for line in match.group(0).split("\n"))

    return re.sub(r"(?m)^( +)```[^\n]*\n.*?^\1```", outdent, text, flags=re.S)


def render(text, source_dir):
    text = outdent_fences(text)
    md = markdown.Markdown(
        extensions=["tables", "fenced_code", "sane_lists", "toc", "md_in_html"],
        extension_configs={"toc": {"slugify": slugify, "permalink": "#", "permalink_title": "Link to this section"}},
    )
    body = md.convert(text)
    body = re.sub(
        r'(href|src)="([^"]+)"',
        lambda m: f'{m.group(1)}="{html.escape(rewrite_link(html.unescape(m.group(2)), source_dir), quote=True)}"',
        body,
    )
    # links written as file names read as the page they lead to
    titles = {f"{page}.html": title for page, title, _, _ in ALL}
    body = re.sub(
        r'<a href="([\w-]+\.html)(#[^"]*)?">(?:<code>)?[\w./-]+\.md(?:#[\w-]+)?(?:</code>)?</a>',
        lambda m: f'<a href="{m.group(1)}{m.group(2) or ""}">{html.escape(titles.get(m.group(1), m.group(1)))}</a>',
        body,
    )
    # long keys like interdimensional-gun.tank.capacity may break after their dots
    body = re.sub(r"<code>([^<]*)</code>", lambda m: "<code>" + re.sub(r"(?<=\w)([./])(?=\w)", r"\1<wbr>", m.group(1)) + "</code>", body)
    body = re.sub(r"<pre><code([^>]*)>(.*?)</code></pre>", lambda m: f"<pre><code{m.group(1)}>" + m.group(2).replace("<wbr>", "") + "</code></pre>", body, flags=re.S)
    body = re.sub(r"<table>.*?</table>", label_cells, body, flags=re.S)
    return body, md.toc_tokens


def label_cells(match):
    """Each cell knows its column's name, so phones can show a row as a card; wide tables scroll in their slot."""
    table = match.group(0)
    names = [re.sub(r"<[^>]+>", "", name).strip() for name in re.findall(r"<th[^>]*>(.*?)</th>", table, re.S)]

    def row(row_match):
        cells = iter(names)
        return re.sub(
            r"<td([^>]*)>",
            lambda cell: f'<td{cell.group(1)} data-label="{html.escape(next(cells, ""), quote=True)}">',
            row_match.group(0),
        )

    table = re.sub(r"<tr>.*?</tr>", row, table, flags=re.S)
    return f'<div class="table">{table}</div>'


def headings(tokens, page, title):
    found = []
    for token in tokens:
        found.append({"p": page, "t": title, "h": re.sub(r"<[^>]+>", "", token["name"]), "a": token["id"]})
        found.extend(headings(token["children"], page, title))
    return found


def first_paragraph(body):
    match = re.search(r"<p>(.*?)</p>", body, re.S)
    return re.sub(r"<[^>]+>", "", match.group(1)).strip() if match else ""


CURRENT = ' aria-current="page"'


def sidebar(current):
    groups = []
    for group, pages in PAGES:
        links = "".join(
            f'<li><a href="{page}.html"{CURRENT if page == current else ""}>{html.escape(title)}</a></li>'
            for page, title, _, _ in pages
        )
        groups.append(f'<p class="nav-group">{group}</p><ul>{links}</ul>')
    return "".join(groups)


def toc(tokens):
    items = [t for token in tokens for t in (token["children"] if token["level"] == 1 else [token])]
    if len(items) < 2:
        return ""
    links = "".join(f'<li><a href="#{t["id"]}">{t["name"]}</a></li>' for t in items)
    return f'<nav class="on-page" aria-label="On this page"><p class="nav-group">On this page</p><ul>{links}</ul></nav>'


def neighbours(page):
    names = [p for p, _, _, _ in ALL]
    index = names.index(page)
    parts = []
    if index > 0:
        prev = ALL[index - 1]
        parts.append(f'<a class="mc-button" href="{prev[0]}.html" rel="prev"><span aria-hidden="true">&lt;</span> {html.escape(prev[1])}</a>')
    if index < len(ALL) - 1:
        nxt = ALL[index + 1]
        parts.append(f'<a class="mc-button next" href="{nxt[0]}.html" rel="next">{html.escape(nxt[1])} <span aria-hidden="true">&gt;</span></a>')
    return f'<nav class="pager" aria-label="More pages">{"".join(parts)}</nav>'


def template(page, title, description, body, tokens, source):
    if isinstance(source, tuple):
        source_link = f'<a href="{BLOB}README.md">README.md</a>'
    elif source:
        source_link = f'<a href="{BLOB}{source}">{source}</a>'
    else:
        source_link = f'<a href="{BLOB}docs">docs/</a>'
    page_title = "PorticleGun Wiki" if page == "index" else f"{title} · PorticleGun Wiki"
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light only">
  <title>{html.escape(page_title)}</title>
  <meta name="description" content="{html.escape(description, quote=True)}">
  <meta name="theme-color" content="#1a1416">
  <link rel="icon" href="../images/porticlegun_logo.png">
  <link rel="preload" href="../assets/fonts/Monocraft-Bold.woff2" as="font" type="font/woff2" crossorigin>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Rubik:wght@400;500;600&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="../style.css">
  <link rel="stylesheet" href="wiki.css">
</head>
<body class="wiki">
  <a class="skip" href="#content">Skip to content</a>
  <header class="top">
    <a class="brand" href="../"><img src="../images/porticlegun_logo.png" alt="" width="30" height="30">PorticleGun</a>
    <div class="top-actions">
      <a class="mc-button small" href="index.html">Wiki</a>
      <a class="mc-button small gh" href="{REPO}">GitHub</a>
    </div>
  </header>
  <div class="wiki-layout">
    <aside class="window wiki-nav">
      <div class="search">
        <label class="sr" for="wiki-search">Search the wiki</label>
        <input id="wiki-search" type="search" placeholder="Search the wiki" autocomplete="off" spellcheck="false">
        <ul class="results" id="wiki-results" hidden></ul>
      </div>
      <details class="nav-menu" open>
        <summary>Pages</summary>
        <nav aria-label="Wiki pages">{sidebar(page)}</nav>
      </details>
    </aside>
    <main class="window wiki-page" id="content">
      <article class="prose">
        {body}
      </article>
      {neighbours(page)}
      <p class="source">Written down in {source_link} · <a href="{REPO}/edit/main/{source if isinstance(source, str) else 'README.md'}">Improve this page</a></p>
    </main>
    <aside class="wiki-toc">{toc(tokens)}</aside>
  </div>
  <footer class="wiki-footer">
    <p>PorticleGun is open source under the <a href="{BLOB}LICENSE">GNU GPL v3.0</a>. Not an official Minecraft product, not affiliated with Mojang or Microsoft.</p>
  </footer>
  <script src="search-index.js" defer></script>
  <script src="wiki.js" defer></script>
</body>
</html>
"""


def overview():
    readme = read("README.md")
    features = re.search(r"(?ms)^## Features\n(.*?)(?=^<p align=\"center\"><img src=\"images/divider)", readme)
    cards = []
    for group, pages in PAGES:
        for page, title, _, description in pages:
            if page != "index":
                cards.append(
                    f'<a class="card slot" href="{page}.html"><span class="card-title">{html.escape(title)}</span>'
                    f'<span class="card-text">{html.escape(description)}</span></a>'
                )
    text = (
        "# PorticleGun Wiki\n\n"
        "A portal gun, an interdimensional gun and a gravity gun for Minecraft servers, with their own 3D models for "
        "Java and Bedrock players. This wiki is built from the documentation in the repository, so it always matches "
        "the latest version.\n\n"
        f'<div class="cards" markdown="0">{"".join(cards)}</div>\n\n'
        "## Features\n" + (features.group(1) if features else "")
    )
    return text


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: build_wiki.py <site directory>")
    out = os.path.join(sys.argv[1], "wiki")
    os.makedirs(out, exist_ok=True)
    index = []
    for page, title, source, description in ALL:
        if source is None:
            text, source_dir = overview(), ""
        elif isinstance(source, tuple):
            text, source_dir = f"# {title}\n\n" + readme_sections(source[1]), ""
        else:
            text, source_dir = read(source), os.path.dirname(source)
        body, tokens = render(text, source_dir)
        index.extend(headings(tokens, page, title))
        with open(os.path.join(out, f"{page}.html"), "w", encoding="utf-8") as file:
            file.write(template(page, title, description or first_paragraph(body), body, tokens, source))
    with open(os.path.join(out, "search-index.js"), "w", encoding="utf-8") as file:
        file.write("window.WIKI_INDEX = " + json.dumps(index, ensure_ascii=False, separators=(",", ":")) + ";\n")
    print(f"Wiki: {len(ALL)} pages, {len(index)} headings in {out}")


if __name__ == "__main__":
    main()
