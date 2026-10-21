#!/usr/bin/env python3
"""Renders the crafting recipe images of the interdimensional gun and portal fluid for the README and docs.

    python3 scripts/render_recipes.py <minecraft client jar>

Needs Pillow. The crafting window is taken from the PorticleGun recipe screenshot (images/crafting_recipe.png), the
vanilla ingredient textures from the given client jar, and the PorticleGun items are rendered by
generate_resourcepack.py.
"""

import os
import sys
import zipfile
from io import BytesIO

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import generate_resourcepack as pack  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
IMAGES = os.path.join(ROOT, "images")

# measured in images/crafting_recipe.png (10 image pixels per GUI pixel)
SLOT_COLUMNS = (95, 275, 455)
SLOT_ROWS = (170, 350, 530)
SLOT_SIZE = 160
RESULT = (995, 310, 1235, 550)
WINDOW_WIDTH = 1330  # the crafting window without the tooltip next to it
SLOT_GREY = (139, 139, 139, 255)
TOOLTIP_LEFT, TOOLTIP_TOP, TOOLTIP_BOTTOM = 1250, 230, 640
EDGE_SAMPLE_ROW = 150


def from_pixels(pixels):
    image = Image.new("RGBA", (len(pixels[0]), len(pixels)))
    image.putdata([p for row in pixels for p in row])
    return image


def vanilla(jar, path):
    with zipfile.ZipFile(jar) as archive:
        return Image.open(BytesIO(archive.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def flat(texture):
    """An item as the inventory draws it: the 16x16 texture, one texture pixel per GUI pixel."""
    return texture.resize((SLOT_SIZE, SLOT_SIZE), Image.NEAREST)


def iso_block(texture):
    """A block as the inventory draws it: an isometric cube with lit top, medium left and dark right face."""
    size = SLOT_SIZE
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    tex = texture.load()
    # corners of the cube in the slot
    top, left, right, middle = (size / 2, 8), (12, size * 0.27), (size - 12, size * 0.27), (size / 2, size * 0.5)
    bottom_left, bottom_right, bottom = (12, size * 0.74), (size - 12, size * 0.74), (size / 2, size - 8)
    faces = [
        (left, top, middle, 1.0),  # origin, u axis end, v axis end
        (left, middle, bottom_left, 0.8),
        (middle, right, bottom, 0.62),
    ]
    for origin, u_end, v_end, light in faces:
        ux, uy = u_end[0] - origin[0], u_end[1] - origin[1]
        vx, vy = v_end[0] - origin[0], v_end[1] - origin[1]
        det = ux * vy - uy * vx
        for py in range(size):
            for px in range(size):
                dx, dy = px + 0.5 - origin[0], py + 0.5 - origin[1]
                u = (dx * vy - dy * vx) / det
                v = (ux * dy - uy * dx) / det
                if 0 <= u < 1 and 0 <= v < 1:
                    r, g, b, a = tex[int(u * 16), int(v * 16)]
                    pixels[px, py] = (round(r * light), round(g * light), round(b * light), a)
    return image


def recipe(grid, result):
    window = Image.open(os.path.join(IMAGES, "crafting_recipe.png")).convert("RGBA").crop((0, 0, WINDOW_WIDTH, 785))
    # the tooltip of the screenshot covers the window's right edge: rebuild it from a row above the tooltip
    edge = window.crop((TOOLTIP_LEFT, EDGE_SAMPLE_ROW, WINDOW_WIDTH, EDGE_SAMPLE_ROW + 1))
    for y in range(TOOLTIP_TOP, TOOLTIP_BOTTOM):
        window.paste(edge, (TOOLTIP_LEFT, y))
    for row, y in enumerate(SLOT_ROWS):
        for column, x in enumerate(SLOT_COLUMNS):
            window.paste(SLOT_GREY, (x, y, x + SLOT_SIZE, y + SLOT_SIZE))
            icon = grid[row][column]
            if icon is not None:
                window.alpha_composite(icon, (x + (SLOT_SIZE - icon.width) // 2, y + (SLOT_SIZE - icon.height) // 2))
    x0, y0, x1, y1 = RESULT
    window.paste(SLOT_GREY, RESULT)
    window.alpha_composite(result, (x0 + (x1 - x0 - result.width) // 2, y0 + (y1 - y0 - result.height) // 2))
    return window


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    jar = sys.argv[1]
    shard = flat(from_pixels(pack.flat_item_texture("rift_shard")))
    fluid = flat(from_pixels(pack.flat_item_texture("portal_fluid")))
    porticle_gun = from_pixels(pack.render_showcase(-35, 22, SLOT_SIZE))
    rift_gun = from_pixels(pack.render_showcase(-35, 22, 220, model=pack.RIFT_GUN))

    gun = recipe(
        [
            [shard, flat(vanilla(jar, "item/nether_star")), shard],
            [flat(vanilla(jar, "item/echo_shard")), porticle_gun, flat(vanilla(jar, "item/echo_shard"))],
            [iso_block(vanilla(jar, "block/iron_block")), fluid, iso_block(vanilla(jar, "block/iron_block"))],
        ],
        rift_gun,
    )
    gun.save(os.path.join(IMAGES, "crafting_interdimensional_gun.png"), optimize=True)

    fluid_recipe = recipe(
        [
            [shard, shard, shard],
            [flat(vanilla(jar, "item/glass_bottle")), flat(vanilla(jar, "item/glowstone_dust")), None],
            [None, None, None],
        ],
        flat(from_pixels(pack.flat_item_texture("portal_fluid"))),
    )
    fluid_recipe.save(os.path.join(IMAGES, "crafting_portal_fluid.png"), optimize=True)


if __name__ == "__main__":
    main()
