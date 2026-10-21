#!/usr/bin/env python3
"""Generates the PorticleGun resource packs from one model definition.

Writes the Java pack (src/main/resourcepack/java), the Bedrock pack (src/main/resourcepack/bedrock) and the Geyser
item mapping (src/main/resourcepack/geyser). Re-run after changing the model:

    python3 scripts/generate_resourcepack.py [--preview <dir>] [--showcase]

--preview additionally renders the model from several angles as PNGs to check it without starting the game,
--showcase renders images/portal_gun_3d.png for the README.
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
# Bedrock icon shorthand in item_texture.json; set explicitly in the Geyser mapping
ICON = f"{NAMESPACE}_{ITEM}"
BASE_ITEM = "minecraft:brewing_stand"

# default portal colours (gold / dark_blue in config.yml), shown when the item carries no colours
DEFAULT_TINTS = [0xFFA500, 0x0000AA]

# Bedrock packs need stable UUIDs, never change them (bump the version instead)
BEDROCK_HEADER_UUID = "6f2a1c3e-8b4d-4e7a-9c15-2d8f0b7e4a61"
BEDROCK_MODULE_UUID = "a3d95b72-1e6c-4f08-b2a4-7c9e5d13f8b0"
BEDROCK_VERSION = [1, 1, 0]

# --- texture atlas (32x32 px, one 8x8 tile per material) ------------------------------------------------------------
TEXTURE_SIZE = 32
REGIONS = {
    "white": (0, 0, 8),
    "dark": (8, 0, 8),
    "glow": (16, 0, 8),  # grey scale, tinted with the portal colours on Java
    "glow_primary": (24, 0, 8),  # pre-coloured for Bedrock, which has no item tints
    "glow_secondary": (0, 8, 8),
}
BEDROCK_PRIMARY = 0xFF9A1F
BEDROCK_SECONDARY = 0x36C2FF


def hex_rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def shade(rgb, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in rgb)


def paint_atlas():
    pixels = [[(0, 0, 0, 0)] * TEXTURE_SIZE for _ in range(TEXTURE_SIZE)]

    def tile(name, painter):
        x0, y0, size = REGIONS[name]
        for y in range(size):
            for x in range(size):
                pixels[y0 + y][x0 + x] = (*painter(x, y, size - 1), 255)

    # every tile is one "voxel": a slightly bevelled square, so the blocks read as separate bricks
    def brick(base, light, dark):
        def painter(x, y, last):
            if x == 0 or y == 0:
                return light
            if x == last or y == last:
                return dark
            return base

        return painter

    tile("white", brick(hex_rgb(0xECEEF1), hex_rgb(0xFFFFFF), hex_rgb(0xC3C8CF)))
    tile("dark", brick(hex_rgb(0x24272C), hex_rgb(0x3B4048), hex_rgb(0x15171A)))

    def glow(color):
        def painter(x, y, last):
            distance = max(abs(x - last / 2), abs(y - last / 2)) / (last / 2)
            return shade(color, 1.0 - 0.3 * distance)

        return painter

    tile("glow", glow((255, 255, 255)))
    tile("glow_primary", glow(hex_rgb(BEDROCK_PRIMARY)))
    tile("glow_secondary", glow(hex_rgb(BEDROCK_SECONDARY)))
    return pixels


# --- model ----------------------------------------------------------------------------------------------------------
# Designed in "gun space": barrel along +X from the rear pod (x=0) to the claw tips (x~31), top at +Y, centred at
# z=8. OFFSET moves it so its centre sits at the model centre (8, 8, 8) that display transforms rotate around.
# Materials: white, dark, glow0 (primary portal colour), glow1 (secondary portal colour).
OFFSET = (-7.8, 0, 0)


def box(name, material, start, end):
    return {"name": name, "material": material, "from": list(start), "to": list(end)}


ELEMENTS = [
    # rear pod: a voxel ball, widest in the middle and stepping in towards both ends. The boxes never overlap, so no
    # two outer faces lie on top of each other (that would flicker in game).
    *[
        part
        for x0, x1 in ((2, 8),)
        for part in (
            box("pod_mid_core", "white", (x0, 4, 4), (x1, 12, 12)),
            box("pod_mid_up", "white", (x0, 12, 4), (x1, 13, 12)),
            box("pod_mid_down", "white", (x0, 3, 4), (x1, 4, 12)),
            box("pod_mid_left", "white", (x0, 4, 3), (x1, 12, 4)),
            box("pod_mid_right", "white", (x0, 4, 12), (x1, 12, 13)),
            box("pod_mid_top", "white", (x0, 13, 5), (x1, 14, 11)),
            box("pod_mid_bottom", "white", (x0, 2, 5), (x1, 3, 11)),
            box("pod_mid_far_left", "white", (x0, 5, 2), (x1, 11, 3)),
            box("pod_mid_far_right", "white", (x0, 5, 13), (x1, 11, 14)),
        )
    ],
    *[
        part
        for name, x0, x1 in (("back", 1, 2), ("front", 8, 9))
        for part in (
            box(f"pod_{name}_ring", "white", (x0, 4, 4), (x1, 12, 12)),
            box(f"pod_{name}_ring_up", "white", (x0, 12, 5), (x1, 13, 11)),
            box(f"pod_{name}_ring_down", "white", (x0, 3, 5), (x1, 4, 11)),
            box(f"pod_{name}_ring_left", "white", (x0, 5, 3), (x1, 11, 4)),
            box(f"pod_{name}_ring_right", "white", (x0, 5, 12), (x1, 11, 13)),
        )
    ],
    box("pod_back_end", "white", (0, 5, 5), (1, 11, 11)),
    box("pod_front_end", "white", (9, 5, 5), (10, 11, 11)),
    box("pod_window", "glow1", (3.5, 14, 6.5), (6.5, 14.4, 9.5)),
    box("pod_rear_plug", "dark", (-0.4, 6.5, 6.5), (0, 9.5, 9.5)),
    box("collar", "dark", (10, 4.5, 4.5), (12, 11.5, 11.5)),
    # barrel with a glowing side window and a hump on top
    box("barrel", "dark", (12, 5, 5), (19, 11, 11)),
    box("barrel_window", "glow1", (13, 6, 4.6), (18, 10, 11.4)),
    box("hump_low", "dark", (15, 11, 5.5), (21, 12.5, 10.5)),
    box("hump_high", "dark", (16.5, 12.5, 6.5), (19.5, 13.5, 9.5)),
    # front head with framed emitter, side windows and white side plates
    box("head", "dark", (19, 4, 4), (25, 12, 12)),
    box("head_window_left", "glow1", (20, 6.5, 3.6), (23, 9.5, 4)),
    box("head_window_right", "glow1", (20, 6.5, 12), (23, 9.5, 12.4)),
    box("emitter", "glow1", (25, 5.5, 5.5), (25.5, 10.5, 10.5)),
    box("emitter_frame_bottom", "dark", (25, 4, 4), (26, 5.5, 12)),
    box("emitter_frame_top", "dark", (25, 10.5, 4), (26, 12, 12)),
    box("emitter_frame_left", "dark", (25, 5.5, 4), (26, 10.5, 5.5)),
    box("emitter_frame_right", "dark", (25, 5.5, 10.5), (26, 10.5, 12)),
    box("plate_left", "white", (17, 2.5, 3), (21, 6, 5)),
    box("plate_right", "white", (17, 2.5, 11), (21, 6, 13)),
    # upper claw, stepping up from the hump and curling forward
    box("claw_top_1", "dark", (17, 13.5, 7), (19, 16.5, 9)),
    box("claw_top_2", "dark", (18, 16.5, 7), (22, 18.5, 9)),
    box("claw_top_3", "white", (21.5, 17.5, 6.5), (24.5, 19.5, 9.5)),
    box("claw_top_4", "dark", (24, 16.5, 7), (27, 18.5, 9)),
    box("claw_top_5", "dark", (27, 14, 7), (29, 17, 9)),
    box("claw_top_tip", "glow0", (29, 13.5, 7), (30.5, 15.5, 9)),
    # lower claw, the mirror image hanging below the head
    box("claw_bottom_1", "dark", (19, -0.5, 7), (21, 4, 9)),
    box("claw_bottom_2", "dark", (19.5, -2.5, 7), (23.5, -0.5, 9)),
    box("claw_bottom_3", "white", (23, -3.5, 6.5), (26, -1.5, 9.5)),
    box("claw_bottom_4", "dark", (25.5, -2.5, 7), (28.5, -0.5, 9)),
    box("claw_bottom_5", "dark", (28.5, -1, 7), (30.5, 2, 9)),
    box("claw_bottom_tip", "glow0", (30.5, 0.5, 7), (32, 2.5, 9)),
]

FACES = ["north", "east", "south", "west", "up", "down"]


def check_model():
    """Fails if two boxes have overlapping outer faces on the same plane, which flickers in game."""
    import itertools

    def faces(element):
        a, b = element["from"], element["to"]
        for axis in range(3):
            others = [i for i in range(3) if i != axis]
            for direction, value in ((-1, a[axis]), (1, b[axis])):
                yield axis, direction, value, [(a[i], b[i]) for i in others]

    problems = []
    for first, second in itertools.combinations(ELEMENTS, 2):
        for f1 in faces(first):
            for f2 in faces(second):
                if f1[:2] == f2[:2] and abs(f1[2] - f2[2]) < 1e-9:
                    overlap = all(min(r1[1], r2[1]) - max(r1[0], r2[0]) > 1e-9 for r1, r2 in zip(f1[3], f2[3]))
                    if overlap:
                        problems.append(f"{first['name']} / {second['name']}")
    if problems:
        raise SystemExit("coplanar overlapping faces: " + ", ".join(problems))
    for element in ELEMENTS:
        start, end = placed(element)
        if min(start) < -16 or max(end) > 32:
            raise SystemExit(f"{element['name']} is outside the -16..32 range Java models allow")


def placed(element):
    start = [element["from"][i] + OFFSET[i] for i in range(3)]
    end = [element["to"][i] + OFFSET[i] for i in range(3)]
    return start, end


def java_material(material):
    return {"glow0": ("glow", 0), "glow1": ("glow", 1)}.get(material, (material, None))


def bedrock_material(material):
    return {"glow0": "glow_primary", "glow1": "glow_secondary"}.get(material, material)


def region_uv16(name):
    x, y, size = REGIONS[name]
    scale = 16 / TEXTURE_SIZE
    return [x * scale, y * scale, (x + size) * scale, (y + size) * scale]


# Display transforms. The barrel lies along +X like the tip of the vanilla handheld sword, so a y rotation of -90
# points it forward. Bedrock animations are derived from these values.
DISPLAY = {
    "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.42] * 3},
    "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.42] * 3},
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.38] * 3},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.38] * 3},
    "gui": {"rotation": [20, -35, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
    "head": {"rotation": [0, -90, 0], "translation": [0, 10, 0], "scale": [0.5] * 3},
}


def java_model():
    elements = []
    for element in ELEMENTS:
        material, tint = java_material(element["material"])
        face = {"uv": region_uv16(material), "texture": "#gun"}
        if tint is not None:
            face["tintindex"] = tint
        start, end = placed(element)
        entry = {
            "name": element["name"],
            "from": start,
            "to": end,
            "faces": {direction: dict(face) for direction in FACES},
        }
        if tint is not None:
            entry["light_emission"] = 15
        elements.append(entry)

    return {
        "textures": {"gun": f"{NAMESPACE}:item/{ITEM}", "particle": f"{NAMESPACE}:item/{ITEM}"},
        "elements": elements,
        "gui_light": "front",
        "display": DISPLAY,
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
# Bone layout and hand poses follow the attachables that the common Java-to-Bedrock converter generates for Geyser:
# a root bone bound to the hand, plus one bone per rotation axis that replays the Java display transform.
ROOT_BONE = NAMESPACE
AXIS_BONES = [f"{NAMESPACE}_x", f"{NAMESPACE}_y", f"{NAMESPACE}_z"]


def bedrock_geometry():
    cubes = []
    for element in ELEMENTS:
        start, end = placed(element)
        size = [round(end[i] - start[i], 4) for i in range(3)]
        # Bedrock mirrors the x axis compared to Java models and centres x/z at 0
        cube = {"origin": [round(8 - end[0], 4), start[1], round(start[2] - 8, 4)], "size": size}
        x, y, region_size = REGIONS[bedrock_material(element["material"])]
        cube["uv"] = {direction: {"uv": [x, y], "uv_size": [region_size, region_size]} for direction in FACES}
        cubes.append(cube)

    bones = [
        {
            "name": ROOT_BONE,
            "pivot": [0, 8, 0],
            "binding": "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)",
        }
    ]
    parent = ROOT_BONE
    for bone in AXIS_BONES:
        bones.append({"name": bone, "parent": parent, "pivot": [0, 8, 0]})
        parent = bone
    bones[-1]["cubes"] = cubes

    return {
        "format_version": "1.16.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{NAMESPACE}.{ITEM}",
                    "texture_width": TEXTURE_SIZE,
                    "texture_height": TEXTURE_SIZE,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 4.5,
                    "visible_bounds_offset": [0, 0.75, 0],
                },
                "bones": bones,
            }
        ],
    }


CONTEXTS = {
    # context: (java display key, root pose, mirrored for the left hand)
    "thirdperson_main_hand": ("thirdperson_righthand", {"rotation": [90, 0, 0], "position": [0, 13, -3]}, False),
    "thirdperson_off_hand": ("thirdperson_lefthand", {"rotation": [90, 0, 0], "position": [0, 13, -3]}, True),
    "firstperson_main_hand": (
        "firstperson_righthand",
        {"rotation": [90, 60, -40], "position": [4, 10, 4], "scale": 1.5},
        False,
    ),
    "firstperson_off_hand": (
        "firstperson_lefthand",
        {"rotation": [90, 60, -40], "position": [4, 10, 4], "scale": 1.5},
        True,
    ),
    "head": ("head", {"position": [0, 19.9, 0], "scale": 0.625}, False),
}


def bedrock_attachable():
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
                        "v.head = c.item_slot == 'head';",
                    ],
                    "animate": [
                        {"thirdperson_main_hand": "v.main_hand && !c.is_first_person"},
                        {"thirdperson_off_hand": "v.off_hand && !c.is_first_person"},
                        {"firstperson_main_hand": "v.main_hand && c.is_first_person"},
                        {"firstperson_off_hand": "v.off_hand && c.is_first_person"},
                        {"head": "v.head"},
                    ],
                },
                "animations": {name: f"animation.{NAMESPACE}.{ITEM}.{name}" for name in CONTEXTS},
                "render_controllers": ["controller.render.item_default"],
            }
        },
    }


def bedrock_animations():
    animations = {}
    for name, (java_key, root_pose, left) in CONTEXTS.items():
        display = DISPLAY[java_key]
        rx, ry, rz = display["rotation"]
        tx, ty, tz = display["translation"]
        x_bone = {"rotation": [-rx, 0, 0], "position": [tx if left else -tx, ty, tz], "scale": display["scale"]}
        bones = {
            ROOT_BONE: root_pose,
            AXIS_BONES[0]: x_bone,
            AXIS_BONES[1]: {"rotation": [0, ry if left else -ry, 0]},
            AXIS_BONES[2]: {"rotation": [0, 0, -rz if left else rz]},
        }
        animations[f"animation.{NAMESPACE}.{ITEM}.{name}"] = {"loop": True, "bones": bones}
    return {"format_version": "1.8.0", "animations": animations}


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
        "texture_data": {ICON: {"textures": [f"textures/items/{ITEM}"]}},
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
                    # without an explicit icon Geyser looks for "porticlegun.portal_gun" in item_texture.json
                    "bedrock_options": {"icon": ICON, "display_handheld": True},
                }
            ]
        },
    }


# --- rendering (icons and previews) ---------------------------------------------------------------------------------
FACE_SHADE = {"up": 1.0, "down": 0.55, "north": 0.8, "south": 0.8, "east": 0.68, "west": 0.68}


def face_quads(start, end):
    (x0, y0, z0), (x1, y1, z1) = start, end
    return {
        "north": ([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], (0, 0, -1)),
        "south": ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1)),
        "west": ([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0)),
        "east": ([(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], (1, 0, 0)),
        "up": ([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], (0, 1, 0)),
        "down": ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
    }


def make_view(yaw, pitch):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def transform(point):
        x, y, z = point[0] - 8, point[1] - 8, point[2] - 8
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    return transform


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


def material_color(material):
    x, y, size = REGIONS[bedrock_material(material)]
    return ATLAS[y + size // 2][x + size // 2][:3]


def render(yaw, pitch, size, margin=1, outline=True):
    """Orthographic render from the given angle, scaled to fit a size x size image."""
    transform = make_view(yaw, pitch)
    faces = []
    for index, element in enumerate(ELEMENTS):
        start, end = placed(element)
        for direction, (quad, normal) in face_quads(start, end).items():
            n = transform((normal[0] + 8, normal[1] + 8, normal[2] + 8))
            if n[2] <= 1e-6:
                continue  # facing away
            points = [transform(p) for p in quad]
            faces.append((sum(p[2] for p in points) / 4, index, direction, points, element["material"]))

    xs = [p[0] for f in faces for p in f[3]]
    ys = [p[1] for f in faces for p in f[3]]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    scale = (size - 2 * margin) / span
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2

    image = [[(0, 0, 0, 0)] * size for _ in range(size)]
    owner = [[None] * size for _ in range(size)]
    for depth, index, direction, points, material in sorted(faces, key=lambda f: f[0]):
        polygon = [((p[0] - cx) * scale + size / 2, (cy - p[1]) * scale + size / 2) for p in points]
        color = material_color(material)
        if not material.startswith("glow"):
            color = shade(color, FACE_SHADE[direction])
        min_x = max(0, int(min(p[0] for p in polygon)))
        max_x = min(size - 1, int(max(p[0] for p in polygon)) + 1)
        min_y = max(0, int(min(p[1] for p in polygon)))
        max_y = min(size - 1, int(max(p[1] for p in polygon)) + 1)
        for py in range(min_y, max_y + 1):
            for px in range(min_x, max_x + 1):
                if inside(polygon, px + 0.5, py + 0.5):
                    image[py][px] = (*color, 255)
                    owner[py][px] = index

    if outline:
        result = [row[:] for row in image]
        for py in range(size):
            for px in range(size):
                if owner[py][px] is None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = px + dx, py + dy
                    if not (0 <= nx < size and 0 <= ny < size) or owner[ny][nx] is None:
                        # lights stay bright at the edge, everything else gets a dark rim
                        if not ELEMENTS[owner[py][px]]["material"].startswith("glow"):
                            result[py][px] = (*shade(image[py][px][:3], 0.55), 255)
                        break
        image = result
    return image


def render_showcase(yaw, pitch, size, supersample=3, margin=0.06):
    """High quality render for the README: textured faces, smooth edges, transparent background."""
    transform = make_view(yaw, pitch)
    full = size * supersample
    faces = []
    for element in ELEMENTS:
        start, end = placed(element)
        for direction, (quad, normal) in face_quads(start, end).items():
            n = transform((normal[0] + 8, normal[1] + 8, normal[2] + 8))
            if n[2] <= 1e-6:
                continue
            points = [transform(p) for p in quad]
            faces.append((sum(p[2] for p in points) / 4, direction, points, element["material"]))

    xs = [p[0] for f in faces for p in f[2]]
    ys = [p[1] for f in faces for p in f[2]]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    scale = full * (1 - 2 * margin) / span
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2

    canvas = [[None] * full for _ in range(full)]
    for depth, direction, points, material in sorted(faces, key=lambda f: f[0]):
        polygon = [((p[0] - cx) * scale + full / 2, (cy - p[1]) * scale + full / 2) for p in points]
        # faces are parallelograms after the orthographic projection: map pixels back to texture coordinates
        (ox, oy), (ax, ay), _, (bx, by) = polygon
        ux, uy, vx, vy = ax - ox, ay - oy, bx - ox, by - oy
        det = ux * vy - uy * vx
        if abs(det) < 1e-9:
            continue
        tx0, ty0, tile = REGIONS[bedrock_material(material)]
        glowing = material.startswith("glow")
        factor = 1.0 if glowing else FACE_SHADE[direction]
        min_x = max(0, int(min(p[0] for p in polygon)))
        max_x = min(full - 1, int(max(p[0] for p in polygon)) + 1)
        min_y = max(0, int(min(p[1] for p in polygon)))
        max_y = min(full - 1, int(max(p[1] for p in polygon)) + 1)
        for py in range(min_y, max_y + 1):
            for px in range(min_x, max_x + 1):
                dx, dy = px + 0.5 - ox, py + 0.5 - oy
                u = (dx * vy - dy * vx) / det
                v = (ux * dy - uy * dx) / det
                if 0 <= u <= 1 and 0 <= v <= 1:
                    texel = ATLAS[ty0 + min(tile - 1, int((1 - v) * tile))][tx0 + min(tile - 1, int(u * tile))]
                    canvas[py][px] = shade(texel[:3], factor)

    image = []
    area = supersample * supersample
    for y in range(size):
        row = []
        for x in range(size):
            samples = [
                canvas[y * supersample + j][x * supersample + i]
                for j in range(supersample)
                for i in range(supersample)
                if canvas[y * supersample + j][x * supersample + i] is not None
            ]
            if not samples:
                row.append((0, 0, 0, 0))
                continue
            color = tuple(round(sum(s[c] for s in samples) / len(samples)) for c in range(3))
            row.append((*color, round(255 * len(samples) / area)))
        image.append(row)
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
ICON_VIEW = (-20, 15)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="directory for preview renders")
    parser.add_argument("--showcase", action="store_true", help="also render images/portal_gun_3d.png for the README")
    args = parser.parse_args()
    check_model()

    java = os.path.join(OUT, "java")
    write_json(os.path.join(java, "pack.mcmeta"), java_pack_mcmeta())
    write_json(os.path.join(java, "assets", NAMESPACE, "items", f"{ITEM}.json"), java_item_definition())
    write_json(os.path.join(java, "assets", NAMESPACE, "models", "item", f"{ITEM}.json"), java_model())
    write_png(os.path.join(java, "assets", NAMESPACE, "textures", "item", f"{ITEM}.png"), ATLAS)
    write_png(os.path.join(java, "pack.png"), render(*ICON_VIEW, 128, margin=6))

    bedrock = os.path.join(OUT, "bedrock")
    write_json(os.path.join(bedrock, "manifest.json"), bedrock_manifest())
    write_json(os.path.join(bedrock, "attachables", f"{ITEM}.json"), bedrock_attachable())
    write_json(os.path.join(bedrock, "models", "entity", f"{ITEM}.geo.json"), bedrock_geometry())
    write_json(os.path.join(bedrock, "animations", f"{ITEM}.animation.json"), bedrock_animations())
    write_json(os.path.join(bedrock, "textures", "item_texture.json"), bedrock_item_texture())
    write_png(os.path.join(bedrock, "textures", "entity", NAMESPACE, f"{ITEM}.png"), ATLAS)
    write_png(os.path.join(bedrock, "textures", "items", f"{ITEM}.png"), render(*ICON_VIEW, 32))
    write_png(os.path.join(bedrock, "pack_icon.png"), render(*ICON_VIEW, 128, margin=6))

    write_json(os.path.join(OUT, "geyser", f"{NAMESPACE}_mappings.json"), geyser_mappings())

    if args.showcase:
        write_png(os.path.join(ROOT, "images", "portal_gun_3d.png"), render_showcase(-35, 22, 720))

    if args.preview:
        views = {"iso": (-35, 25), "iso_back": (145, 25), "side": (0, 0), "top": (0, 90), "rear": (90, 0)}
        for name, (yaw, pitch) in views.items():
            write_png(os.path.join(args.preview, f"{name}.png"), render(yaw, pitch, 320, margin=12))
        icon = render(*ICON_VIEW, 32)
        enlarged = [[p for p in row for _ in range(8)] for row in icon for _ in range(8)]
        write_png(os.path.join(args.preview, "icon_x8.png"), enlarged)


if __name__ == "__main__":
    main()
