#!/usr/bin/env python3
"""Generates the PorticleGun resource packs from one model definition.

Writes the Java pack (src/main/resourcepack/java), the Bedrock pack (src/main/resourcepack/bedrock) and the Geyser
item mapping (src/main/resourcepack/geyser). Re-run after changing the model:

    python3 scripts/generate_resourcepack.py [--preview <dir>]

--preview additionally renders side/top/front views as PNGs to check the shape without starting the game.
"""

import argparse
import json
import math
import os
import struct
import zlib

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "src", "main", "resourcepack")

NAMESPACE = "porticlegun"
ITEM = "portal_gun"
IDENTIFIER = f"{NAMESPACE}:{ITEM}"
BASE_ITEM = "minecraft:brewing_stand"

# default portal colours (gold / dark_blue in config.yml), shown when the item carries no colours
DEFAULT_TINTS = [0xFFA500, 0x0000AA]

# Bedrock packs need stable UUIDs, never change them (bump the version instead)
BEDROCK_HEADER_UUID = "6f2a1c3e-8b4d-4e7a-9c15-2d8f0b7e4a61"
BEDROCK_MODULE_UUID = "a3d95b72-1e6c-4f08-b2a4-7c9e5d13f8b0"
BEDROCK_VERSION = [1, 0, 0]

# --- texture atlas (32x32 px) --------------------------------------------------------------------------------------
# region of each material in pixels: (x, y, size)
TEXTURE_SIZE = 32
REGIONS = {
    "body": (0, 0, 8),
    "dark": (8, 0, 8),
    "metal": (16, 0, 8),
    "glow": (24, 0, 8),  # grey scale, tinted on Java
    "glow_primary": (0, 8, 8),  # pre-coloured for Bedrock (no tints)
    "glow_secondary": (8, 8, 8),
    "accent": (16, 8, 8),
}


def hex_rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def shade(rgb, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in rgb)


def paint_atlas():
    pixels = [[(0, 0, 0, 0)] * TEXTURE_SIZE for _ in range(TEXTURE_SIZE)]

    def region(name, painter):
        x0, y0, size = REGIONS[name]
        for y in range(size):
            for x in range(size):
                edge = x in (0, size - 1) or y in (0, size - 1)
                pixels[y0 + y][x0 + x] = (*painter(x, y, size, edge), 255)

    body = hex_rgb(0xE6E9EC)
    region("body", lambda x, y, s, e: shade(body, 0.86 if e else (1.03 if y < 2 else 1.0)))
    dark = hex_rgb(0x2B3038)
    region("dark", lambda x, y, s, e: shade(dark, 1.25 if (x + y) % 3 == 0 else 1.0))
    metal_top, metal_bottom = hex_rgb(0xB3BBC4), hex_rgb(0x6C7580)
    region(
        "metal",
        lambda x, y, s, e: tuple(round(a + (b - a) * y / (s - 1)) for a, b in zip(metal_top, metal_bottom)),
    )

    def glow(color):
        def painter(x, y, s, e):
            distance = math.hypot(x - (s - 1) / 2, y - (s - 1) / 2) / (s / 2)
            return shade(color, 1.0 - 0.25 * min(1.0, distance))

        return painter

    region("glow", glow((255, 255, 255)))
    region("glow_primary", glow(hex_rgb(DEFAULT_TINTS[0])))
    region("glow_secondary", glow(hex_rgb(0x2D4BFF)))  # lighter than #0000AA so it still reads as a light
    accent = hex_rgb(0xAEB5BE)
    region("accent", lambda x, y, s, e: shade(accent, 0.9 if e else 1.0))
    return pixels


# --- model ----------------------------------------------------------------------------------------------------------
# Java model space: barrel along +X (front at x=16), top at +Y, centred around z=8.
# material "glow0"/"glow1" are lit by the primary/secondary portal colour.
ELEMENTS = [
    {"name": "rear_body", "material": "body", "from": [0, 5, 5], "to": [7, 11, 11]},
    {"name": "front_body", "material": "body", "from": [7, 5.5, 5.5], "to": [11, 10.5, 10.5]},
    {"name": "bottom_plate", "material": "accent", "from": [1, 4.5, 6], "to": [6.5, 5, 10]},
    {"name": "top_fin", "material": "body", "from": [2, 11, 6.5], "to": [9, 12.5, 9.5]},
    {"name": "fin_light", "material": "glow1", "from": [3, 12.5, 7.25], "to": [8, 13, 8.75]},
    {
        "name": "grip",
        "material": "dark",
        "from": [3, 0.5, 6.5],
        "to": [6, 5, 9.5],
        "rotation": {"axis": "z", "angle": -22.5, "origin": [4.5, 5, 8]},
    },
    {"name": "core_band", "material": "glow0", "from": [8, 5.2, 5.2], "to": [10, 10.8, 10.8]},
    {"name": "emitter_ring", "material": "dark", "from": [11, 6, 6], "to": [12.5, 10, 10]},
    {"name": "emitter_crystal", "material": "glow0", "from": [12.5, 7, 7], "to": [13.5, 9, 9]},
    {
        "name": "prong_top",
        "material": "metal",
        "from": [12, 10, 7.5],
        "to": [16, 11, 8.5],
        "rotation": {"axis": "z", "angle": -22.5, "origin": [12, 10.5, 8]},
    },
    {
        "name": "prong_left",
        "material": "metal",
        "from": [12, 6.5, 5],
        "to": [16, 7.5, 6],
        "rotation": {"axis": "y", "angle": -22.5, "origin": [12, 7, 5.5]},
    },
    {
        "name": "prong_right",
        "material": "metal",
        "from": [12, 6.5, 10],
        "to": [16, 7.5, 11],
        "rotation": {"axis": "y", "angle": 22.5, "origin": [12, 7, 10.5]},
    },
    {"name": "rear_cap", "material": "dark", "from": [-0.5, 6.5, 6.5], "to": [0, 9.5, 9.5]},
]

FACES = ["north", "east", "south", "west", "up", "down"]


def java_material(material):
    if material == "glow0":
        return "glow", 0
    if material == "glow1":
        return "glow", 1
    return material, None


def bedrock_material(material):
    return {"glow0": "glow_primary", "glow1": "glow_secondary"}.get(material, material)


def region_uv16(name):
    x, y, size = REGIONS[name]
    scale = 16 / TEXTURE_SIZE
    return [x * scale, y * scale, (x + size) * scale, (y + size) * scale]


def java_model():
    elements = []
    for element in ELEMENTS:
        material, tint = java_material(element["material"])
        face = {"uv": region_uv16(material), "texture": "#gun"}
        if tint is not None:
            face["tintindex"] = tint
        entry = {
            "name": element["name"],
            "from": element["from"],
            "to": element["to"],
            "faces": {direction: dict(face) for direction in FACES},
        }
        if "rotation" in element:
            entry["rotation"] = element["rotation"]
        if tint is not None:
            entry["light_emission"] = 15
        elements.append(entry)

    return {
        "textures": {"gun": f"{NAMESPACE}:item/{ITEM}", "particle": f"{NAMESPACE}:item/{ITEM}"},
        "elements": elements,
        "gui_light": "front",
        # barrel along +X: like the vanilla handheld sword (whose tip is +X) a y rotation of -90 points it forward
        "display": {
            "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.7, 0.7, 0.7]},
            "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [15, -30, 0], "translation": [0, 0.5, 0], "scale": [0.85, 0.85, 0.85]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.9, 0.9, 0.9]},
            "head": {"rotation": [0, -90, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
        },
    }


def java_item_definition():
    return {
        "model": {
            "type": "minecraft:model",
            "model": f"{NAMESPACE}:item/{ITEM}",
            "tints": [
                {"type": "minecraft:custom_model_data", "index": index, "default": color}
                for index, color in enumerate(DEFAULT_TINTS)
            ],
        }
    }


def java_pack_mcmeta():
    return {
        "pack": {
            "description": "PorticleGun: 3D portal gun",
            # 1.21.4 (46) up to current versions; min/max_format is read by 1.21.9+, the rest by older clients
            "pack_format": 46,
            "supported_formats": {"min_inclusive": 46, "max_inclusive": 99},
            "min_format": 46,
            "max_format": 99,
        }
    }


# --- Bedrock --------------------------------------------------------------------------------------------------------
def bedrock_geometry():
    cubes = []
    for element in ELEMENTS:
        start, end = element["from"], element["to"]
        size = [end[i] - start[i] for i in range(3)]
        # Bedrock mirrors the x axis compared to Java models and centres x/z at 0
        cube = {"origin": [8 - end[0], start[1], start[2] - 8], "size": size}
        if "rotation" in element:
            rotation = element["rotation"]
            axis = "xyz".index(rotation["axis"])
            angles = [0, 0, 0]
            angles[axis] = rotation["angle"] if axis == 0 else -rotation["angle"]
            origin = rotation["origin"]
            cube["pivot"] = [8 - origin[0], origin[1], origin[2] - 8]
            cube["rotation"] = angles
        x, y, region_size = REGIONS[bedrock_material(element["material"])]
        cube["uv"] = {direction: {"uv": [x, y], "uv_size": [region_size, region_size]} for direction in FACES}
        cubes.append(cube)

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{NAMESPACE}.{ITEM}",
                    "texture_width": TEXTURE_SIZE,
                    "texture_height": TEXTURE_SIZE,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 3,
                    "visible_bounds_offset": [0, 0.5, 0],
                },
                "bones": [
                    {
                        "name": "porticlegun_root",
                        "pivot": [0, 8, 0],
                        "binding": "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)",
                    },
                    {"name": "porticlegun_gun", "parent": "porticlegun_root", "pivot": [0, 8, 0], "cubes": cubes},
                ],
            }
        ],
    }


def bedrock_attachable():
    contexts = ["firstperson_main_hand", "thirdperson_main_hand", "firstperson_off_hand", "thirdperson_off_hand"]
    return {
        "format_version": "1.10.0",
        "minecraft:attachable": {
            "description": {
                "identifier": IDENTIFIER,
                "materials": {"default": "entity_alphatest", "enchanted": "entity_alphatest_glint"},
                "textures": {
                    "default": f"textures/entity/{NAMESPACE}/{ITEM}",
                    "enchanted": "textures/misc/enchanted_item_glint",
                },
                "geometry": {"default": f"geometry.{NAMESPACE}.{ITEM}"},
                "scripts": {
                    "pre_animation": [
                        "v.main_hand = c.item_slot == 'main_hand';",
                        "v.off_hand = c.item_slot == 'off_hand';",
                    ],
                    "animate": [
                        {"thirdperson_main_hand": "v.main_hand && !c.is_first_person"},
                        {"firstperson_main_hand": "v.main_hand && c.is_first_person"},
                        {"thirdperson_off_hand": "v.off_hand && !c.is_first_person"},
                        {"firstperson_off_hand": "v.off_hand && c.is_first_person"},
                    ],
                },
                "animations": {name: f"animation.{NAMESPACE}.{ITEM}.{name}" for name in contexts},
                "render_controllers": ["controller.render.item_default"],
            }
        },
    }


def bedrock_animations():
    # positions of the gun relative to the hand bone; tune these with a Bedrock client if the gun sits oddly
    poses = {
        "firstperson_main_hand": {"rotation": [90, 60, -40], "position": [4, 10, 4], "scale": [1.2, 1.2, 1.2]},
        "firstperson_off_hand": {"rotation": [90, -60, 40], "position": [-4, 10, 4], "scale": [1.2, 1.2, 1.2]},
        "thirdperson_main_hand": {"rotation": [90, 90, 0], "position": [0, 13, -3], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_off_hand": {"rotation": [90, -90, 0], "position": [0, 13, -3], "scale": [0.8, 0.8, 0.8]},
    }
    return {
        "format_version": "1.8.0",
        "animations": {
            f"animation.{NAMESPACE}.{ITEM}.{name}": {"loop": True, "bones": {"porticlegun_root": pose}}
            for name, pose in poses.items()
        },
    }


def bedrock_manifest():
    return {
        "format_version": 2,
        "header": {
            "name": "PorticleGun",
            "description": "3D portal gun for PorticleGun (Geyser)",
            "uuid": BEDROCK_HEADER_UUID,
            "version": BEDROCK_VERSION,
            "min_engine_version": [1, 21, 0],
        },
        "modules": [{"type": "resources", "uuid": BEDROCK_MODULE_UUID, "version": BEDROCK_VERSION}],
    }


def bedrock_item_texture():
    return {
        "resource_pack_name": "PorticleGun",
        "texture_name": "atlas.items",
        "texture_data": {IDENTIFIER: {"textures": [f"textures/items/{ITEM}"]}},
    }


def geyser_mappings():
    return {
        "format_version": 2,
        "items": {
            BASE_ITEM: [
                {
                    "type": "definition",
                    "model": IDENTIFIER,
                    "bedrock_identifier": IDENTIFIER,
                    "display_name": "PorticleGun",
                    "bedrock_options": {"display_handheld": True},
                }
            ]
        },
    }


# --- rendering (icon and preview) -----------------------------------------------------------------------------------
def rotate(point, rotation):
    if rotation is None:
        return point
    angle = math.radians(rotation["angle"])
    c, s = math.cos(angle), math.sin(angle)
    x, y, z = (point[i] - rotation["origin"][i] for i in range(3))
    if rotation["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rotation["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return [x + rotation["origin"][0], y + rotation["origin"][1], z + rotation["origin"][2]]


def corners(element):
    f, t = element["from"], element["to"]
    points = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
    return [rotate(p, element.get("rotation")) for p in points]


def convex_hull(points):
    points = sorted(set(points))
    if len(points) <= 2:
        return points

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in points:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(points):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def inside(polygon, x, y):
    result = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            result = not result
        j = i
    return result


def material_color(material, tinted):
    atlas_material = bedrock_material(material) if tinted else java_material(material)[0]
    x, y, size = REGIONS[atlas_material]
    return ATLAS[y + size // 2][x + size // 2][:3]


def render(view, scale, width_units, height_units, offset, outline=True):
    """Orthographic render. view: 'side' (from +Z), 'top' (from +Y) or 'front' (from +X)."""
    axes = {"side": (0, 1, 2), "top": (0, 2, 1), "front": (2, 1, 0)}[view]
    flip_v = view == "top"
    width, height = round(width_units * scale), round(height_units * scale)
    image = [[(0, 0, 0, 0)] * width for _ in range(height)]
    owner = [[None] * width for _ in range(height)]
    polygons = []
    for index, element in enumerate(ELEMENTS):
        points = corners(element)
        projected = [(p[axes[0]] - offset[0], p[axes[1]] - offset[1]) for p in points]
        depth = max(p[axes[2]] for p in points)
        polygons.append((depth, index, convex_hull(projected), element))
    for depth, index, polygon, element in sorted(polygons, key=lambda item: item[0]):
        color = material_color(element["material"], True)
        for py in range(height):
            v = (py + 0.5) / scale
            v = v if flip_v else height_units - v
            for px in range(width):
                if inside(polygon, (px + 0.5) / scale, v):
                    image[py][px] = (*color, 255)
                    owner[py][px] = index
    if outline:
        for py in range(height):
            for px in range(width):
                if owner[py][px] is None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = px + dx, py + dy
                    if not (0 <= nx < width and 0 <= ny < height) or owner[ny][nx] != owner[py][px]:
                        image[py][px] = (*shade(image[py][px][:3], 0.72), 255)
                        break
    return image


def write_png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as file:
        file.write(png)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as file:
        json.dump(data, file, indent=2)
        file.write("\n")


ATLAS = paint_atlas()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="directory for preview renders")
    args = parser.parse_args()

    java = os.path.join(OUT, "java")
    write_json(os.path.join(java, "pack.mcmeta"), java_pack_mcmeta())
    write_json(os.path.join(java, "assets", NAMESPACE, "items", f"{ITEM}.json"), java_item_definition())
    write_json(os.path.join(java, "assets", NAMESPACE, "models", "item", f"{ITEM}.json"), java_model())
    write_png(os.path.join(java, "assets", NAMESPACE, "textures", "item", f"{ITEM}.png"), ATLAS)

    icon = render("side", 2, 17, 17, (-0.5, -2))
    write_png(os.path.join(java, "pack.png"), render("side", 4, 17, 17, (-0.5, -2)))

    bedrock = os.path.join(OUT, "bedrock")
    write_json(os.path.join(bedrock, "manifest.json"), bedrock_manifest())
    write_json(os.path.join(bedrock, "attachables", f"{ITEM}.json"), bedrock_attachable())
    write_json(os.path.join(bedrock, "models", "entity", f"{ITEM}.geo.json"), bedrock_geometry())
    write_json(os.path.join(bedrock, "animations", f"{ITEM}.animation.json"), bedrock_animations())
    write_json(os.path.join(bedrock, "textures", "item_texture.json"), bedrock_item_texture())
    write_png(os.path.join(bedrock, "textures", "entity", NAMESPACE, f"{ITEM}.png"), ATLAS)
    write_png(os.path.join(bedrock, "textures", "items", f"{ITEM}.png"), icon)
    write_png(os.path.join(bedrock, "pack_icon.png"), render("side", 4, 17, 17, (-0.5, -2)))

    write_json(os.path.join(OUT, "geyser", f"{NAMESPACE}_mappings.json"), geyser_mappings())

    if args.preview:
        write_png(os.path.join(args.preview, "side.png"), render("side", 16, 17, 15, (-0.5, -1)))
        write_png(os.path.join(args.preview, "top.png"), render("top", 16, 17, 12, (-0.5, 2)))
        write_png(os.path.join(args.preview, "front.png"), render("front", 16, 12, 15, (2, -1)))
        enlarged = [[p for p in row for _ in range(8)] for row in icon for _ in range(8)]
        write_png(os.path.join(args.preview, "icon_x8.png"), enlarged)


if __name__ == "__main__":
    main()
