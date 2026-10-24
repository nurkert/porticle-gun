#!/usr/bin/env python3
"""Generates the PorticleGun resource packs from one model definition.

Writes the Java pack (src/main/resourcepack/java), the Bedrock pack (src/main/resourcepack/bedrock) and the Geyser
item mapping (src/main/resourcepack/geyser). Re-run after changing the model:

    python3 scripts/generate_resourcepack.py [--preview <dir>] [--showcase]

--preview additionally renders the models from several angles as PNGs to check them without starting the game,
--showcase renders the README images in images/.
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
# one texture atlas shared by all 3D models
ATLAS_NAME = "atlas"

# default portal colours (gold / dark_blue in config.yml), shown when the item carries no colours
DEFAULT_TINTS = [0xFFA500, 0x0000AA]

# Bedrock packs need stable UUIDs, never change them (bump the version instead)
BEDROCK_HEADER_UUID = "6f2a1c3e-8b4d-4e7a-9c15-2d8f0b7e4a61"
BEDROCK_MODULE_UUID = "a3d95b72-1e6c-4f08-b2a4-7c9e5d13f8b0"
BEDROCK_VERSION = [1, 8, 0]

# --- texture atlas (32x32 px, one 8x8 tile per material) ------------------------------------------------------------
TEXTURE_SIZE = 64
REGIONS = {
    "white": (0, 0, 8),
    "dark": (8, 0, 8),
    "glow": (16, 0, 8),  # grey scale, tinted with the portal colours on Java
    "glow_primary": (24, 0, 8),  # pre-coloured for Bedrock, which has no item tints
    "glow_secondary": (0, 8, 8),
    # interdimensional gun
    "grey": (8, 8, 8),
    "steel": (16, 8, 8),
    "liquid": (24, 8, 8),  # portal fluid, always green
    "glass": (0, 16, 8),
    "screen": (8, 16, 8),
    "knob": (16, 16, 8),
    # rift shard and portal fluid
    "rock": (24, 16, 8),
    "crystal": (0, 24, 8),
    "crystal_tip": (8, 24, 8),  # glows
    "cork": (16, 24, 8),
    "bottle": (24, 24, 8),
    # rift compass
    "brass": (40, 0, 8),
    "compass_face": (48, 0, 8),
}
EMISSIVE = {"liquid", "crystal_tip"}
RIFT_GREEN = 0x39FF5A
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
    tile("grey", brick(hex_rgb(0xD3D6D8), hex_rgb(0xE9EBEC), hex_rgb(0xA9AEB2)))
    def vent(x, y, last):
        if x in (0, last) or y in (0, last):
            return hex_rgb(0x9DA2A6)
        return hex_rgb(0x6E7378) if x % 2 == 0 else hex_rgb(0x868B90)

    tile("steel", vent)
    tile("liquid", glow(hex_rgb(RIFT_GREEN)))
    tile("glass", brick(hex_rgb(0xA8E8A0), hex_rgb(0xD2F7CC), hex_rgb(0x7CC474)))

    def screen(x, y, last):
        if x in (0, last) or y in (0, last):
            return hex_rgb(0x8E949B)
        return hex_rgb(0xFF5A4A) if 2 <= y <= last - 2 and x % 3 != 0 else hex_rgb(0x9E1A14)

    tile("screen", screen)
    tile("knob", brick(hex_rgb(0x2A2D31), hex_rgb(0x44484E), hex_rgb(0x17191B)))
    tile("rock", brick(hex_rgb(0x3A2A4F), hex_rgb(0x53406B), hex_rgb(0x251933)))
    tile("crystal", brick(hex_rgb(0xA77EE6), hex_rgb(0xD9C2FF), hex_rgb(0x6E45B0)))
    tile("crystal_tip", glow(hex_rgb(0x8CFFA0)))
    tile("cork", brick(hex_rgb(0x9C6A3C), hex_rgb(0xB98552), hex_rgb(0x6B4423)))
    tile("bottle", brick(hex_rgb(0xCFE9EE), hex_rgb(0xF2FDFF), hex_rgb(0x9CC4CC)))
    tile("brass", brick(hex_rgb(0xC9A23F), hex_rgb(0xE8C764), hex_rgb(0x8E6E22)))
    tile("compass_face", brick(hex_rgb(0x1F2A24), hex_rgb(0x2C3B33), hex_rgb(0x141B17)))
    return pixels


# --- models ---------------------------------------------------------------------------------------------------------
# Every gun is designed in "gun space": barrel along +X from the rear (x=0) to the front, top at +Y, centred at z=8.
# A model's offset moves it so its centre sits at the model centre (8, 8, 8) that display transforms rotate around.


def box(name, material, start, end, rotation=None):
    """A box of the model. rotation: optional (axis, angle, origin); Java before 1.21.11 allows only -45, -22.5, 0,
    22.5 and 45 degrees (models with other angles need a legacy variant, see PORTAL_GUN_LEGACY_ELEMENTS)."""
    element = {"name": name, "material": material, "from": list(start), "to": list(end)}
    if rotation:
        axis, angle, origin = rotation
        element["rotation"] = {"axis": axis, "angle": angle, "origin": list(origin)}
    return element


def rotated(point, rotation, offset):
    """Rotates a placed point around a placed rotation origin like Minecraft does (counter-clockwise when looking
    along the axis towards the origin, i.e. the right-hand rule)."""
    if not rotation:
        return point
    origin = [rotation["origin"][i] + offset[i] for i in range(3)]
    angle = math.radians(rotation["angle"])
    c, s_ = math.cos(angle), math.sin(angle)
    x, y, z = (point[i] - origin[i] for i in range(3))
    if rotation["axis"] == "x":
        y, z = y * c - z * s_, y * s_ + z * c
    elif rotation["axis"] == "y":
        x, z = x * c + z * s_, -x * s_ + z * c
    else:
        x, y = x * c - y * s_, x * s_ + y * c
    return [x + origin[0], y + origin[1], z + origin[2]]


# The three claws in front sit 120 degrees apart around the barrel axis (y = z = 8), one on top and two below at
# 30 degrees under the horizontal, so they form a perfect triangle seen from the front. Each claw is designed once in
# its own plane: x along the barrel, r away from the axis, w half its thickness. It curls up from a mount on the barrel,
# forward and back in towards the emitter. Free rotation angles need Java 1.21.11 (pack format 75); older clients get
# the same claws at 22.5 degrees (see PORTAL_GUN_LEGACY_ELEMENTS and the overlay in java_pack_mcmeta).
CLAW_PARTS = [
    # name, material, x from, x to, r from, r to, half width
    ("mount_low", "dark", 15, 21, 3, 4.5, 2.5),
    ("mount_high", "dark", 16.5, 19.5, 4.5, 5.5, 1.5),
    ("1", "dark", 17, 19, 5.5, 8.5, 1),
    ("2", "dark", 18, 22, 8.5, 10.5, 1),
    ("3", "white", 21.5, 24.5, 9.5, 11.5, 1.5),
    ("4", "dark", 24, 27, 8.5, 10.5, 1),
    ("5", "dark", 27, 29, 6, 9, 1),
    ("tip", "glow0", 29, 30.5, 5.5, 7.5, 1),
]
CLAW_ANGLE = 30
CLAW_LEGACY_ANGLE = 22.5


def claws(angle):
    """The top claw as plain boxes, the two lower ones built pointing sideways (+z and -z) and turned down by the angle
    around the barrel axis: exact copies of the top claw turned by 120 degrees when the angle is 30."""
    parts = []
    for name, material, x0, x1, r0, r1, w in CLAW_PARTS:
        parts.append(box(f"claw_top_{name}", material, (x0, 8 + r0, 8 - w), (x1, 8 + r1, 8 + w)))
        parts.append(box(f"claw_left_{name}", material, (x0, 8 - w, 8 + r0), (x1, 8 + w, 8 + r1),
                         ("x", angle, (16, 8, 8))))
        parts.append(box(f"claw_right_{name}", material, (x0, 8 - w, 8 - r1), (x1, 8 + w, 8 - r0),
                         ("x", -angle, (16, 8, 8))))
    return parts


# PorticleGun. Materials: white, dark, glow0 (primary portal colour), glow1 (secondary portal colour).
def portal_gun_elements(claw_angle):
    return [
        # rear pod: a voxel ball, widest in the middle and stepping in towards both ends, open at the back for the hand.
        # The boxes never overlap, so no two outer faces lie on top of each other (that would flicker in game).
        box("pod_mid_up", "white", (2, 12, 4), (8, 13, 12)),
        box("pod_mid_down", "white", (2, 3, 4), (8, 4, 12)),
        box("pod_mid_left", "white", (2, 4, 3), (8, 12, 4)),
        box("pod_mid_right", "white", (2, 4, 12), (8, 12, 13)),
        box("pod_mid_top", "white", (2, 13, 5), (8, 14, 11)),
        box("pod_mid_bottom", "white", (2, 2, 5), (8, 3, 11)),
        box("pod_mid_far_left", "white", (2, 5, 2), (8, 11, 3)),
        box("pod_mid_far_right", "white", (2, 5, 13), (8, 11, 14)),
        # the hand opening: a dark lined cavity from the back to a glowing plate
        box("pod_cavity_top", "dark", (2, 11, 4), (8, 12, 12)),
        box("pod_cavity_bottom", "dark", (2, 4, 4), (8, 5, 12)),
        box("pod_cavity_left", "dark", (2, 5, 4), (8, 11, 5)),
        box("pod_cavity_right", "dark", (2, 5, 11), (8, 11, 12)),
        box("pod_cavity_end", "glow1", (7, 5, 5), (8, 11, 11)),
        # back ring: a white frame around the opening
        box("pod_back_frame_top", "white", (1, 11, 4), (2, 12, 12)),
        box("pod_back_frame_bottom", "white", (1, 4, 4), (2, 5, 12)),
        box("pod_back_frame_left", "white", (1, 5, 4), (2, 11, 5)),
        box("pod_back_frame_right", "white", (1, 5, 11), (2, 11, 12)),
        box("pod_back_ring_up", "white", (1, 12, 5), (2, 13, 11)),
        box("pod_back_ring_down", "white", (1, 3, 5), (2, 4, 11)),
        box("pod_back_ring_left", "white", (1, 5, 3), (2, 11, 4)),
        box("pod_back_ring_right", "white", (1, 5, 12), (2, 11, 13)),
        box("pod_front_ring", "white", (8, 4, 4), (9, 12, 12)),
        box("pod_front_ring_up", "white", (8, 12, 5), (9, 13, 11)),
        box("pod_front_ring_down", "white", (8, 3, 5), (9, 4, 11)),
        box("pod_front_ring_left", "white", (8, 5, 3), (9, 11, 4)),
        box("pod_front_ring_right", "white", (8, 5, 12), (9, 11, 13)),
        box("pod_front_end", "white", (9, 5, 5), (10, 11, 11)),
        box("pod_window", "glow1", (3.5, 14, 6.5), (6.5, 14.4, 9.5)),
        box("collar", "dark", (10, 4.5, 4.5), (12, 11.5, 11.5)),
        # barrel with a glowing side window
        box("barrel", "dark", (12, 5, 5), (19, 11, 11)),
        box("barrel_window", "glow1", (13, 6, 4.6), (18, 10, 11.4)),
        # front head with framed emitter and side windows
        box("head", "dark", (19, 4, 4), (25, 12, 12)),
        box("head_window_left", "glow1", (20, 6.5, 3.6), (23, 9.5, 4)),
        box("head_window_right", "glow1", (20, 6.5, 12), (23, 9.5, 12.4)),
        box("emitter", "glow1", (25, 5.5, 5.5), (25.5, 10.5, 10.5)),
        box("emitter_frame_bottom", "dark", (25, 4, 4), (26, 5.5, 12)),
        box("emitter_frame_top", "dark", (25, 10.5, 4), (26, 12, 12)),
        box("emitter_frame_left", "dark", (25, 5.5, 4), (26, 10.5, 5.5)),
        box("emitter_frame_right", "dark", (25, 5.5, 10.5), (26, 10.5, 12)),
        *claws(claw_angle),
    ]


PORTAL_GUN_ELEMENTS = portal_gun_elements(CLAW_ANGLE)
PORTAL_GUN_LEGACY_ELEMENTS = portal_gun_elements(CLAW_LEGACY_ANGLE)


def rift_gun_elements(level):
    """Interdimensional gun with its portal fluid tank filled to the given level (0 = empty, 4 = full).

    The shape of the classic sci-fi portal device in PorticleGun's voxel style: a long, flat body with stepped edges,
    an open front holding three green lenses, a voxel glass dome holding the portal fluid, a red display and a dial at
    the back, vents along both sides and a stepped grip with a round end, angled down and back.
    Materials: grey, steel (vents), glass, screen (red display), knob, liquid (glowing portal fluid).
    """
    elements = [
        # body with stepped top and bottom edges
        box("body", "grey", (2, 4.5, 4), (18.5, 7.5, 12)),
        box("body_top", "grey", (2, 7.5, 4.5), (18.5, 8, 11.5)),
        box("body_bottom", "grey", (2, 4, 4.5), (18.5, 4.5, 11.5)),
        # frame around the open front and three lenses side by side
        # the frame follows the stepped edges of the body, so no open corners are left at the front
        box("front_top", "grey", (18.5, 7.5, 4.5), (20.5, 8, 11.5)),
        box("front_bottom", "grey", (18.5, 4, 4.5), (20.5, 4.5, 11.5)),
        box("front_left", "grey", (18.5, 4.5, 4), (20.5, 7.5, 4.5)),
        box("front_right", "grey", (18.5, 4.5, 11.5), (20.5, 7.5, 12)),
        *[
            box(f"lens_{index}", "liquid", (18.5, 5, z0), (19.6, 7, z0 + 2))
            for index, z0 in enumerate((4.9, 7, 9.1))
        ],
        # vents along both sides, red display and dial at the back
        box("vent_left", "steel", (6, 5, 3.6), (17, 7, 4)),
        box("vent_right", "steel", (6, 5, 12), (17, 7, 12.4)),
        box("display", "screen", (5, 8, 6), (9, 8.3, 10)),
        box("dial", "knob", (2.4, 8, 9.4), (3.8, 9.4, 10.8)),
        # one slanted grip, 45 degrees down and back, with its details aligned to it: a collar where it meets the
        # body, inlaid lines along both sides and a thicker rounded end with a cap. Its upper end reaches into the
        # body, so the slanted end face is hidden inside.
        *[
            box(name, material, start, end, ("z", 45, (4.5, 4, 8)))
            for name, material, start, end in (
                ("grip", "grey", (-3, 2.5, 6.3), (7, 5.5, 9.7)),
                ("grip_collar", "steel", (2.2, 2.3, 6.15), (3.4, 5.7, 9.85)),
                ("grip_line_left", "steel", (-2, 3.5, 6.2), (1.6, 4.5, 6.3)),
                ("grip_line_right", "steel", (-2, 3.5, 9.7), (1.6, 4.5, 9.8)),
                ("grip_end", "grey", (-5, 2.1, 6.0), (-2.5, 5.9, 10.0)),
                ("grip_end_cap", "steel", (-5.5, 2.7, 6.6), (-5, 5.3, 9.4)),
            )
        ],
        # the dome sits on a ring
        box("dome_ring", "grey", (12, 8, 5.5), (17, 9, 10.5)),
    ]
    # a voxel dome: a core with a bulge on each side, glowing portal fluid at the bottom and glass above it
    height = [0, 1.5, 3, 4.5, 6][level]
    for material, y0, y1 in (("liquid", 9, 9 + height), ("glass", 9 + height, 15)):
        if y1 - y0 <= 0:
            continue
        elements += [
            box(f"dome_{material}_core", material, (12.5, y0, 6), (16.5, y1, 10)),
            box(f"dome_{material}_left", material, (13, y0, 5.5), (16, y1, 6)),
            box(f"dome_{material}_right", material, (13, y0, 10), (16, y1, 10.5)),
            box(f"dome_{material}_back", material, (12, y0, 6.5), (12.5, y1, 9.5)),
            box(f"dome_{material}_front", material, (16.5, y0, 6.5), (17, y1, 9.5)),
        ]
    elements += [
        box("dome_top", "liquid" if level == 4 else "glass", (13, 15, 6.5), (16, 15.8, 9.5)),
        box("dome_tip", "glass", (13.5, 15.8, 7), (15.5, 16.4, 9)),
    ]
    return elements


RIFT_GUN_TANK_LEVELS = 4

FACES = ["north", "east", "south", "west", "up", "down"]


def check_model(model):
    """Fails if two boxes have overlapping outer faces on the same plane, which flickers in game."""
    import itertools

    def faces(element):
        a, b = element["from"], element["to"]
        for axis in range(3):
            others = [i for i in range(3) if i != axis]
            for direction, value in ((-1, a[axis]), (1, b[axis])):
                yield axis, direction, value, [(a[i], b[i]) for i in others]

    problems = []
    axis_aligned = [element for element in model["elements"] if "rotation" not in element]
    for first, second in itertools.combinations(axis_aligned, 2):
        for f1 in faces(first):
            for f2 in faces(second):
                if f1[:2] == f2[:2] and abs(f1[2] - f2[2]) < 1e-9:
                    overlap = all(min(r1[1], r2[1]) - max(r1[0], r2[0]) > 1e-9 for r1, r2 in zip(f1[3], f2[3]))
                    if overlap:
                        problems.append(f"{first['name']} / {second['name']}")
    if problems:
        raise SystemExit(f"{model['item']}: coplanar overlapping faces: " + ", ".join(problems))
    for element in model["elements"]:
        start, end = placed(element, model["offset"])
        corners = [
            rotated([x, y, z], element.get("rotation"), model["offset"])
            for x in (start[0], end[0]) for y in (start[1], end[1]) for z in (start[2], end[2])
        ]
        if min(min(c) for c in corners) < -16 or max(max(c) for c in corners) > 32:
            raise SystemExit(f"{element['name']} is outside the -16..32 range Java models allow")


def placed(element, offset):
    start = [element["from"][i] + offset[i] for i in range(3)]
    end = [element["to"][i] + offset[i] for i in range(3)]
    return start, end


def java_material(material):
    return {"glow0": ("glow", 0), "glow1": ("glow", 1)}.get(material, (material, None))


def bedrock_material(material):
    return {"glow0": "glow_primary", "glow1": "glow_secondary"}.get(material, material)


def region_uv16(name):
    x, y, size = REGIONS[name]
    scale = 16 / TEXTURE_SIZE
    return [x * scale, y * scale, (x + size) * scale, (y + size) * scale]


# Display transforms. The barrel lies along +X; in the hand slots a y rotation of +90 (right hand) / -90 (left hand)
# points it away from the player (-90 made players hold it the wrong way round in 1.3.1). Bedrock animations are
# derived from these values.
PORTAL_GUN_DISPLAY = {
    "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.42] * 3},
    "firstperson_lefthand": {"rotation": [0, -90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.42] * 3},
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.38] * 3},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.38] * 3},
    "gui": {"rotation": [20, -35, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
    "head": {"rotation": [0, -90, 0], "translation": [0, 10, 0], "scale": [0.5] * 3},
}

RIFT_GUN_DISPLAY = {
    "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.6] * 3},
    "firstperson_lefthand": {"rotation": [0, -90, 0], "translation": [1.5, 2.5, 1.5], "scale": [0.6] * 3},
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.5] * 3},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.5] * 3},
    "gui": {"rotation": [20, -35, 0], "translation": [0, 0, 0], "scale": [0.62] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.65] * 3},
    "head": {"rotation": [0, -90, 0], "translation": [0, 10, 0], "scale": [0.6] * 3},
}

# A model: Java item model plus, through Geyser, a Bedrock attachable. "variants" are extra Java models the item
# definition switches between by the first custom_model_data float (the rift gun's tank level).
PORTAL_GUN = {
    "item": "portal_gun",
    "name": "PorticleGun",
    "base": "minecraft:brewing_stand",
    "elements": PORTAL_GUN_ELEMENTS,
    # written to the base pack for clients before free rotation angles; the overlay has the exact model
    "legacy_elements": PORTAL_GUN_LEGACY_ELEMENTS,
    "offset": (-7.8, 0, 0),
    "display": PORTAL_GUN_DISPLAY,
    "tints": DEFAULT_TINTS,
}
RIFT_GUN = {
    "item": "interdimensional_gun",
    "name": "Interdimensional Gun",
    "base": "minecraft:warped_fungus_on_a_stick",
    "elements": rift_gun_elements(RIFT_GUN_TANK_LEVELS),
    "variants": [rift_gun_elements(level) for level in range(RIFT_GUN_TANK_LEVELS + 1)],
    "offset": (-1, 2, 0),
    "display": RIFT_GUN_DISPLAY,
    "tints": None,
}
GUNS = [PORTAL_GUN, RIFT_GUN]

# Rift shard and portal fluid: 3D in hand, on the ground and in item frames, the pixel art icon in inventories, like
# vanilla does for the trident and the spyglass.
SMALL_ITEM_DISPLAY = {
    "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [1, 3, 1], "scale": [0.6] * 3},
    "firstperson_lefthand": {"rotation": [0, 25, 0], "translation": [1, 3, 1], "scale": [0.6] * 3},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55] * 3},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55] * 3},
    "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.8] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5] * 3},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9] * 3},
    "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [0.8] * 3},
}


def rift_shard_elements():
    """A cluster of three leaning crystals on a chunk of end rock; a green core shows through the main crystal."""
    lean_right = ("z", -22.5, (8, 1.5, 8))
    lean_left = ("z", 22.5, (5.75, 1.5, 7.25))
    lean_back = ("x", 22.5, (9.25, 1.5, 10))  # backwards, away from the main crystal
    return [
        box("rock", "rock", (5, 0, 5.5), (11, 2, 10.5)),
        box("rock_front", "rock", (6, 0, 4.5), (10, 1.5, 5.5)),
        box("crystal_main", "crystal", (7, 1.5, 7), (9, 10, 9), lean_right),
        box("crystal_main_core", "liquid", (7.6, 2, 6.9), (8.4, 9.5, 9.1), lean_right),
        box("crystal_main_tip", "crystal_tip", (7.5, 10, 7.5), (8.5, 11.5, 8.5), lean_right),
        box("crystal_left", "crystal", (5, 1.5, 6.5), (6.5, 7, 8), lean_left),
        box("crystal_left_tip", "crystal_tip", (5.3, 7, 6.8), (6.2, 8, 7.7), lean_left),
        box("crystal_back", "crystal", (8.5, 1.5, 9.25), (10, 6, 10.75), lean_back),
        box("crystal_back_tip", "crystal_tip", (8.8, 6, 9.55), (9.7, 7, 10.45), lean_back),
    ]


def portal_fluid_elements():
    """A round, corked bottle of glowing portal fluid, the vanilla potion bottle in voxels."""
    elements = []
    # glowing fluid fills the lower part of the round body, clear glass the rest
    for material, y0, y1 in (("liquid", 1, 4.6), ("bottle", 4.6, 6)):
        elements += [
            box(f"body_{material}_core", material, (6, y0, 6), (10, y1, 10)),
            box(f"body_{material}_front", material, (6.5, y0, 5.5), (9.5, y1, 6)),
            box(f"body_{material}_back", material, (6.5, y0, 10), (9.5, y1, 10.5)),
            box(f"body_{material}_left", material, (5.5, y0, 6.5), (6, y1, 9.5)),
            box(f"body_{material}_right", material, (10, y0, 6.5), (10.5, y1, 9.5)),
        ]
    return elements + [
        box("bottle_bottom", "bottle", (6.5, 0.5, 6.5), (9.5, 1, 9.5)),
        box("bottle_shoulder", "bottle", (6.5, 6, 6.5), (9.5, 6.8, 9.5)),
        box("bottle_neck", "bottle", (7.3, 6.8, 7.3), (8.7, 8.8, 8.7)),
        box("bottle_lip", "bottle", (7, 8.8, 7), (9, 9.3, 9)),
        box("cork", "cork", (7.4, 9.3, 7.4), (8.6, 10.6, 8.6)),
        box("glint", "white", (6.6, 2.2, 5.45), (7.3, 5.4, 5.5)),
    ]


def rift_compass_elements():
    """A round brass compass: a voxel disc case, a dark face, a glowing green needle and a rift crystal on its rim."""
    elements = []
    for row in range(3, 13):
        z = row + 0.5
        cells = [math.hypot((col + 0.5 - 8) / 5, (z - 8) / 5) <= 1.0 for col in range(16)]
        col = 0
        while col < 16:
            if not cells[col]:
                col += 1
                continue
            start = col
            while col < 16 and cells[col]:
                col += 1
            elements.append(box(f"case_{row}_{start}", "brass", (start, 2, row), (col, 4, row + 1)))
    elements += [
        box("face", "compass_face", (5, 4, 5), (11, 4.3, 11)),
        box("needle_north", "liquid", (7.6, 4.3, 5.4), (8.4, 4.8, 8)),
        box("needle_south", "crystal", (7.6, 4.3, 8), (8.4, 4.8, 10.6)),
        box("pin", "brass", (7.4, 4.8, 7.4), (8.6, 5.2, 8.6)),
        box("hinge", "brass", (7, 2.5, 2.2), (9, 4.5, 3)),
        box("crystal", "crystal", (7.4, 4.5, 2.1), (8.6, 7, 3.1), ("x", -22.5, (8, 4.5, 2.6))),
        box("crystal_tip", "crystal_tip", (7.6, 7, 2.3), (8.4, 8, 2.9), ("x", -22.5, (8, 4.5, 2.6))),
    ]
    return elements


RIFT_COMPASS = {
    "item": "rift_compass",
    "name": "Rift Compass",
    "base": "minecraft:paper",
    "elements": rift_compass_elements(),
    "offset": (0, 3, 0),
    "display": SMALL_ITEM_DISPLAY,
    "tints": None,
    "flat_icon": True,
}

RIFT_SHARD = {
    "item": "rift_shard",
    "name": "Rift Shard",
    "base": "minecraft:paper",
    "elements": rift_shard_elements(),
    "offset": (0, 2, 0),
    "display": SMALL_ITEM_DISPLAY,
    "tints": None,
    "flat_icon": True,
}
PORTAL_FLUID = {
    "item": "portal_fluid",
    "name": "Portal Fluid",
    "base": "minecraft:paper",
    "elements": portal_fluid_elements(),
    "offset": (0, 2.5, 0),
    "display": SMALL_ITEM_DISPLAY,
    "tints": None,
    "flat_icon": True,
}
# every 3D item; guns are held like guns, the small items show their pixel art icon in inventories
MODELS = [PORTAL_GUN, RIFT_GUN, RIFT_SHARD, PORTAL_FLUID, RIFT_COMPASS]

# flat items, drawn as pixel art (see FLAT_ITEM_ART)
FLAT_ITEMS = [
    {"item": "rift_shard", "name": "Rift Shard", "base": "minecraft:paper"},
    {"item": "portal_fluid", "name": "Portal Fluid", "base": "minecraft:paper"},
    {"item": "rift_compass", "name": "Rift Compass", "base": "minecraft:paper"},
]


def identifier(item):
    return f"{NAMESPACE}:{item}"


def icon(item):
    """Bedrock icon shorthand in item_texture.json, set explicitly in the Geyser mapping."""
    return f"{NAMESPACE}_{item}"


def java_model(model, elements=None):
    entries = []
    for element in elements if elements is not None else model["elements"]:
        material, tint = java_material(element["material"])
        face = {"uv": region_uv16(material) if material in REGIONS else [0, 0, 16, 16], "texture": "#gun"}
        if tint is not None:
            face["tintindex"] = tint
        start, end = placed(element, model["offset"])
        entry = {
            "name": element["name"],
            "from": start,
            "to": end,
            "faces": {direction: dict(face) for direction in FACES},
        }
        if "rotation" in element:
            rotation = element["rotation"]
            entry["rotation"] = {
                "angle": rotation["angle"],
                "axis": rotation["axis"],
                "origin": [rotation["origin"][i] + model["offset"][i] for i in range(3)],
            }
        if tint is not None or material in EMISSIVE:
            entry["light_emission"] = 15
        entries.append(entry)

    return {
        "textures": {"gun": f"{NAMESPACE}:item/{ATLAS_NAME}", "particle": f"{NAMESPACE}:item/{ATLAS_NAME}"},
        "elements": entries,
        "gui_light": "front",
        "display": model["display"],
    }


def java_item_definition(model):
    item = model["item"]
    if model.get("flat_icon"):
        # 3D everywhere but in inventories, where the animated pixel art icon is shown
        return {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:display_context",
                "cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{item}_icon"}}],
                "fallback": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{item}"},
            }
        }
    if "variants" in model:
        # the plugin sets the first custom_model_data float to the variant number (rift gun: tank level)
        return {
            "model": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:custom_model_data",
                "index": 0,
                "entries": [
                    {"threshold": level, "model": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{item}_{level}"}}
                    for level in range(1, len(model["variants"]))
                ],
                "fallback": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{item}_0"},
            }
        }
    definition = {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{item}"}
    if model.get("tints"):
        definition["tints"] = [
            {"type": "minecraft:custom_model_data", "index": index, "default": color}
            for index, color in enumerate(model["tints"])
        ]
    return {"model": definition}


FREE_ROTATION_OVERLAY = "free_rotation"


def java_pack_mcmeta():
    return {
        "pack": {
            "description": "PorticleGun: 3D portal gun",
            # 1.21.4 (46) up to current versions; min/max_format is read by 1.21.9+, the rest by older clients
            "pack_format": 46,
            "supported_formats": {"min_inclusive": 46, "max_inclusive": 99},
            "min_format": 46,
            "max_format": 99,
        },
        # 1.21.11+ (pack format 75) allows any rotation angle; it gets the models with exact angles from here.
        # Clients from 1.21.9 (65) on reject "formats" in overlays unless some overlay also covers formats below 65,
        # while older clients need it: the empty "legacy" overlay for 46-64 keeps "formats" valid for both.
        "overlays": {
            "entries": [
                {
                    "directory": "legacy",
                    "formats": {"min_inclusive": 46, "max_inclusive": 64},
                    "min_format": 46,
                    "max_format": 64,
                },
                {
                    "directory": FLAT_BANNERS_OVERLAY,
                    "formats": {"min_inclusive": FLAT_BANNERS_FORMAT, "max_inclusive": 99},
                    "min_format": FLAT_BANNERS_FORMAT,
                    "max_format": 99,
                },
                {
                    "directory": FREE_ROTATION_OVERLAY,
                    "formats": {"min_inclusive": 75, "max_inclusive": 99},
                    "min_format": 75,
                    "max_format": 99,
                }
            ]
        },
    }


# --- Bedrock --------------------------------------------------------------------------------------------------------
# The attachable, geometry and animations reproduce what java2bedrock (https://github.com/Kas-tle/java2bedrock.sh)
# generates for 3D Java items, the converter most Geyser servers use: a root bone bound to the hand, one bone per
# rotation axis replaying the Java display transform, the same base poses, material and UV conventions.
ROOT_BONE = NAMESPACE
AXIS_BONES = [f"{NAMESPACE}_x", f"{NAMESPACE}_y", f"{NAMESPACE}_z"]
ATTACHABLE_MATERIAL = "entity_alphatest_one_sided"
# UVs are given in a 16x16 space like in the converter, inset slightly so neighbouring tiles never bleed in
UV_SPACE = 16
UV_INSET = 0.016


def roundit(value):
    return round(value * 10000) / 10000


def bedrock_face_uv(material, direction):
    x, y, size = REGIONS[bedrock_material(material)]
    factor = UV_SPACE / TEXTURE_SIZE
    u0, v0, u1, v1 = x * factor, y * factor, (x + size) * factor, (y + size) * factor
    if direction in ("up", "down"):
        # the converter flips top and bottom faces
        return {
            "uv": [roundit(u1 - UV_INSET), roundit(v1 - UV_INSET)],
            "uv_size": [roundit(u0 - u1 + UV_INSET), roundit(v0 - v1 + UV_INSET)],
        }
    return {
        "uv": [roundit(u0 + UV_INSET), roundit(v0 + UV_INSET)],
        "uv_size": [roundit(u1 - u0 - UV_INSET), roundit(v1 - v0 - UV_INSET)],
    }


def bedrock_geometry(model):
    cubes = []
    for element in model["elements"]:
        start, end = placed(element, model["offset"])
        cube = {
            # Bedrock mirrors the x axis compared to Java models and centres x/z at 0
            "origin": [roundit(-end[0] + 8), roundit(start[1]), roundit(start[2] - 8)],
            "size": [roundit(end[i] - start[i]) for i in range(3)],
            "uv": {direction: bedrock_face_uv(element["material"], direction) for direction in FACES},
        }
        if "rotation" in element:
            # same conversion as java2bedrock: x and y angles flip with the mirrored x axis
            rotation = element["rotation"]
            ox, oy, oz = (rotation["origin"][i] + model["offset"][i] for i in range(3))
            cube["pivot"] = [roundit(-ox + 8), roundit(oy), roundit(oz - 8)]
            angle = rotation["angle"]
            cube["rotation"] = {"x": [-angle, 0, 0], "y": [0, -angle, 0], "z": [0, 0, angle]}[rotation["axis"]]
        cubes.append(cube)

    bones = [
        {
            "name": ROOT_BONE,
            "binding": "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)",
            "pivot": [0, 8, 0],
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
                    "identifier": f"geometry.{NAMESPACE}.{model['item']}",
                    "texture_width": UV_SPACE,
                    "texture_height": UV_SPACE,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 4.5,
                    "visible_bounds_offset": [0, 0.75, 0],
                },
                "bones": bones,
            }
        ],
    }


def animation_name(model, context):
    return f"animation.{NAMESPACE}.{model['item']}.{context}"


def bedrock_attachable(model):
    return {
        "format_version": "1.10.0",
        "minecraft:attachable": {
            "description": {
                "identifier": identifier(model["item"]),
                "materials": {"default": ATTACHABLE_MATERIAL, "enchanted": ATTACHABLE_MATERIAL},
                "textures": {
                    "default": f"textures/entity/{NAMESPACE}/{ATLAS_NAME}",
                    "enchanted": "textures/misc/enchanted_item_glint",
                },
                "geometry": {"default": f"geometry.{NAMESPACE}.{model['item']}"},
                "scripts": {
                    "pre_animation": [
                        "v.main_hand = c.item_slot == 'main_hand';",
                        "v.off_hand = c.item_slot == 'off_hand';",
                        "v.head = c.item_slot == 'head';",
                    ],
                    "animate": [
                        {"thirdperson_main_hand": "v.main_hand && !c.is_first_person"},
                        {"thirdperson_off_hand": "v.off_hand && !c.is_first_person"},
                        {"thirdperson_head": "v.head && !c.is_first_person"},
                        {"firstperson_main_hand": "v.main_hand && c.is_first_person"},
                        {"firstperson_off_hand": "v.off_hand && c.is_first_person"},
                        {"firstperson_head": "c.is_first_person && v.head"},
                    ],
                },
                "animations": {
                    "thirdperson_main_hand": animation_name(model, "thirdperson_main_hand"),
                    "thirdperson_off_hand": animation_name(model, "thirdperson_off_hand"),
                    "thirdperson_head": animation_name(model, "head"),
                    "firstperson_main_hand": animation_name(model, "firstperson_main_hand"),
                    "firstperson_off_hand": animation_name(model, "firstperson_off_hand"),
                    "firstperson_head": animation_name(model, "disable"),
                },
                "render_controllers": ["controller.render.item_default"],
            }
        },
    }


def axis_bones(display, position):
    rx, ry, rz = display["rotation"]
    return {
        AXIS_BONES[0]: {"rotation": [-rx, 0, 0], "position": position, "scale": display["scale"]},
        AXIS_BONES[1]: {"rotation": [0, -ry, 0]},
        AXIS_BONES[2]: {"rotation": [0, 0, rz]},
    }


def bedrock_animations(model):
    display_values = model["display"]
    third_person_root = {"rotation": [90, 0, 0], "position": [0, 13, -3]}
    first_person_root = {"rotation": [90, 60, -40], "position": [4, 10, 4], "scale": 1.5}

    def pose(java_key, root, mirror_x, negate_z):
        display = display_values[java_key]
        tx, ty, tz = display["translation"]
        position = [tx if mirror_x else -tx, ty, -tz if negate_z else tz]
        return {"loop": True, "bones": {ROOT_BONE: root, **axis_bones(display, position)}}

    head = display_values["head"]
    hx, hy, hz = head["translation"]
    head_bones = axis_bones(head, [roundit(-hx * 0.625), roundit(hy * 0.625), roundit(hz * 0.625)])
    head_bones[AXIS_BONES[0]]["scale"] = [roundit(value * 0.625) for value in head["scale"]]

    return {
        "format_version": "1.8.0",
        "animations": {
            animation_name(model, "thirdperson_main_hand"): pose(
                "thirdperson_righthand", third_person_root, False, False
            ),
            animation_name(model, "thirdperson_off_hand"): pose("thirdperson_lefthand", third_person_root, True, False),
            animation_name(model, "head"): {
                "loop": True,
                "bones": {ROOT_BONE: {"position": [0, 19.9, 0]}, **head_bones},
            },
            animation_name(model, "firstperson_main_hand"): pose(
                "firstperson_righthand", first_person_root, False, True
            ),
            animation_name(model, "firstperson_off_hand"): pose("firstperson_lefthand", first_person_root, True, True),
            animation_name(model, "disable"): {
                "loop": True,
                "override_previous_animation": True,
                "bones": {ROOT_BONE: {"scale": 0}},
            },
        },
    }


def bedrock_manifest():
    return {
        "format_version": 2,
        "header": {
            "name": "PorticleGun",
            "description": "3D guns and items of PorticleGun (Geyser)",
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
        "texture_data": {
            icon(entry["item"]): {"textures": [f"textures/items/{entry['item']}"]} for entry in MODELS
        },
    }


def geyser_mappings():
    items = {}
    for entry in MODELS:
        items.setdefault(entry["base"], []).append({
            "type": "definition",
            "model": identifier(entry["item"]),
            "bedrock_identifier": identifier(entry["item"]),
            "display_name": entry["name"],
            # without an explicit icon Geyser looks for "porticlegun.<item>" in item_texture.json
            # in the Bedrock creative inventory: the guns with the equipment, the rest with the items
            "bedrock_options": {"icon": icon(entry["item"]), "display_handheld": entry in GUNS,
                                "creative_category": "equipment" if entry in GUNS else "items"},
        })
    return {"format_version": 2, "items": items}


# --- portal status overlay (HUD) -----------------------------------------------------------------------------------
# Two half rings around the crosshair, one per portal, filled while the portal is open. They are glyphs in the default
# font on Private Use Area code points (far away from Bedrock's built-in E0xx/E1xx icons). Java merges font files from
# all packs, so this only adds characters. The plugin sends them as a title, which Minecraft draws around the screen
# centre, and colours each ring with the portal colour. Keep in sync with PortalHud.java.
HUD_GLYPH_WIDTH, HUD_GLYPH_HEIGHT = 8, 16  # drawn at double resolution, shown 8 font pixels high


def crescent(filled):
    """The left ring: a thick arc band, either only its outline (portal closed) or filled (portal open)."""

    def inside(x, y):
        if not 1 <= y <= HUD_GLYPH_HEIGHT - 2:
            return False  # cut off the thin tips
        distance = math.hypot(x + 0.5 - 8.0, y + 0.5 - 8.0)
        return 3.7 <= distance <= 8.0 and x < HUD_GLYPH_WIDTH

    rows = []
    for y in range(HUD_GLYPH_HEIGHT):
        row = ""
        for x in range(HUD_GLYPH_WIDTH):
            neighbours = ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))
            edge = any(not 0 <= nx < HUD_GLYPH_WIDTH or not inside(nx, ny) for nx, ny in neighbours)
            row += "#" if inside(x, y) and (filled or edge) else "."
        rows.append(row)
    return rows


def mirrored(rows):
    return [row[::-1] for row in rows]


HUD_GLYPHS = {
    "\ue9a0": crescent(False),  # left ring, portal closed
    "\ue9a1": crescent(True),  # left ring, portal open
    "\ue9a2": mirrored(crescent(False)),  # right ring, portal closed
    "\ue9a3": mirrored(crescent(True)),  # right ring, portal open
}
HUD_GAP = "\ue9a4"  # space between the rings, so they sit left and right of the crosshair
HUD_GAP_ADVANCE = 7
# Minecraft centres a line including the 1 pixel spacing after its last glyph; one leading pixel balances that, so
# the rings sit symmetrically: left ring from -8 to -4, right ring from 4 to 8 title pixels around the crosshair
HUD_LEAD = "\ue9a5"
HUD_FONT_HEIGHT = 8
# titles are drawn 4x scaled with the text line starting 10 units above the screen centre; an ascent of 1 centres an
# 8 pixel high glyph on the crosshair
HUD_ASCENT = 1


def hud_glyph_strip():
    width = HUD_GLYPH_WIDTH
    pixels = [[(0, 0, 0, 0)] * (width * len(HUD_GLYPHS)) for _ in range(HUD_GLYPH_HEIGHT)]
    for index, rows in enumerate(HUD_GLYPHS.values()):
        for y, row in enumerate(rows):
            for x, value in enumerate(row):
                if value == "#":
                    pixels[y][index * width + x] = (255, 255, 255, 255)
    return pixels


def java_font(drawn_settings=False):
    return {
        "providers": [
            {
                "type": "bitmap",
                "file": f"{NAMESPACE}:font/portal_hud.png",
                "ascent": HUD_ASCENT,
                "height": HUD_FONT_HEIGHT,
                "chars": ["".join(HUD_GLYPHS)],
            },
            {"type": "space", "advances": {HUD_GAP: HUD_GAP_ADVANCE, HUD_LEAD: 1}},
        ]
        + tank_font_providers()
        + settings_font_providers(drawn_settings)
        + menu_font_providers()
    }


def bedrock_glyph_sheet():
    """Bedrock draws U+E9xx from font/glyph_E9.png, a 16x16 grid of 16 pixel cells."""
    size = 256
    pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]
    strip = hud_glyph_strip()
    for index, char in enumerate(HUD_GLYPHS):
        low = ord(char) & 0xFF
        cx, cy = (low % 16) * 16, (low // 16) * 16
        for y in range(HUD_GLYPH_HEIGHT):
            for x in range(HUD_GLYPH_WIDTH):
                pixels[cy + y][cx + x] = strip[y][index * HUD_GLYPH_WIDTH + x]
    return pixels


# --- tank menu (custom chest background) ----------------------------------------------------------------------------
# The rift gun's "fill tank" chest draws its own background: a glyph in the chest title, shifted to the window's top
# left corner, covers the title bar and the three chest rows; the player inventory below stays vanilla. Its outer rim
# repeats the vanilla window rim, so it joins the vanilla part seamlessly. A second glyph draws the tank gauge's fill.
# Only this chest's title contains the glyphs, so no other menu changes. Keep in sync with TankGui.java.
TANK_GUI_WIDTH, TANK_GUI_HEIGHT = 176, 72
TANK_GUI = ""
TANK_GAUGE_LEVELS = 12
TANK_GAUGE_GLYPHS = [chr(0xE9B1 + level) for level in range(TANK_GAUGE_LEVELS + 1)]
TANK_GAUGE_X, TANK_GAUGE_Y, TANK_GAUGE_W, TANK_GAUGE_H = 154, 19, 12, 50
# titles start 8 pixels right of the window; one glyph advances by its width plus one pixel
TANK_SHIFT_START = ""  # to the window's left edge
TANK_SHIFT_GAUGE = ""  # from behind the background to the gauge
TANK_SHIFT_TEXT = ""  # from behind the gauge back to where the title text starts
TANK_SPACES = {
    TANK_SHIFT_START: -8,
    TANK_SHIFT_GAUGE: TANK_GAUGE_X - (TANK_GUI_WIDTH + 1),
    TANK_SHIFT_TEXT: 8 - (TANK_GAUGE_X + TANK_GAUGE_W + 1),
}
# the title text's line: its glyphs are 7 pixels above the baseline, which is 6 + 7 pixels below the window's top
TANK_TITLE_BASELINE = 13

VANILLA_BLACK = (0, 0, 0, 255)
VANILLA_WHITE = (255, 255, 255, 255)
VANILLA_FACE = (198, 198, 198, 255)
VANILLA_SHADOW = (85, 85, 85, 255)
VANILLA_DARK = (55, 55, 55, 255)
VANILLA_SLOT = (139, 139, 139, 255)


def rgba(value, alpha=255):
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def vanilla_slot(px, x0, y0, rim=None, bottom=None):
    """A vanilla slot (dark top left, white bottom right, grey inside); a rim colours its edges instead."""
    dark, light = VANILLA_DARK, VANILLA_WHITE
    if rim is not None:
        dark, light = rgba(rim), mix(rgba(rim), VANILLA_WHITE, 0.55)
    y1 = y0 + 17 if bottom is None else bottom
    for y in range(y0, y1 + 1):
        for x in range(x0, x0 + 18):
            px[y][x] = VANILLA_SLOT
    for x in range(x0, x0 + 17):
        px[y0][x] = dark
    for y in range(y0, y1):
        px[y][x0] = dark
    for x in range(x0 + 1, x0 + 18):
        px[y1][x] = light
    for y in range(y0 + 1, y1 + 1):
        px[y][x0 + 17] = light


def draw_art(px, x0, y0, art, colour):
    for dy, line in enumerate(art):
        for dx, ch in enumerate(line):
            if ch == "#":
                px[y0 + dy][x0 + dx] = colour


def tank_gui_background():
    w, h = TANK_GUI_WIDTH, TANK_GUI_HEIGHT
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    vanilla_rim(px)

    def rect(x0, y0, x1, y1, colour):
        for y in range(max(0, y0), min(h, y1 + 1)):
            for x in range(max(0, x0), min(w, x1 + 1)):
                px[y][x] = colour

    green = RIFT_GREEN
    # input: a slot with a green rim and faint outlines of what goes in (a bottle and a compass)
    ix, iy = 7 + 1 * 18, 17 + 1 * 18
    vanilla_slot(px, ix, iy, rim=0x2BBF4A)
    faint = rgba(0x7A7A7A)
    draw_art(px, ix + 2, iy + 2, [
        "...##....",
        "...##....",
        "..####...",
        ".#....#..",
        "#......#.",
        "#......#.",
        "#......#.",
        ".######..",
    ], faint)
    draw_art(px, ix + 9, iy + 9, [".###.", "#...#", "#.#.#", "#...#", ".###."], faint)

    # glass pipes carrying the fluid from the input to the button and on to the gun
    def pipe(x0, x1, y):
        rect(x0, y - 3, x1, y - 3, VANILLA_DARK)
        rect(x0, y + 3, x1, y + 3, VANILLA_WHITE)
        rect(x0, y - 2, x1, y + 2, rgba(0x1F5A2E))
        rect(x0, y - 1, x1, y - 1, mix(rgba(green), rgba(0x1F5A2E), 0.5))
        rect(x0, y, x1, y, rgba(green))
        for x in range(x0 + 2, x1, 5):
            px[y - 2][x] = rgba(0xBFFFD0)

    pipe(43, 60, 43)
    pipe(97, 114, 43)

    # the load button over two slots: raised and green, with an arrow pointing to the gun
    bx0, by0, bx1, by1 = 61, 35, 96, 52
    for y in range(by0, by1 + 1):
        rect(bx0, y, bx1, y, mix(rgba(0x3FD86A), rgba(0x168A3C), (y - by0) / (by1 - by0)))
    rect(bx0, by0, bx1, by0, rgba(0xB6FFC9))
    rect(bx0, by0, bx0, by1, rgba(0xB6FFC9))
    rect(bx0, by1, bx1, by1, rgba(0x0B4A20))
    rect(bx1, by0, bx1, by1, rgba(0x0B4A20))
    rect(bx0 - 1, by0 - 1, bx1 + 1, by0 - 1, VANILLA_BLACK)
    rect(bx0 - 1, by1 + 1, bx1 + 1, by1 + 1, VANILLA_BLACK)
    rect(bx0 - 1, by0, bx0 - 1, by1, VANILLA_BLACK)
    rect(bx1 + 1, by0, bx1 + 1, by1, VANILLA_BLACK)
    arrow = [
        "..........##......",
        "..........###.....",
        "..........####....",
        "#################.",
        "##################",
        "#################.",
        "..........####....",
        "..........###.....",
        "..........##......",
    ]
    ax, ay = bx0 + (bx1 - bx0 + 1 - len(arrow[0])) // 2, by0 + (by1 - by0 + 1 - len(arrow)) // 2
    draw_art(px, ax + 1, ay + 1, arrow, rgba(0x0B4A20))
    draw_art(px, ax, ay, arrow, rgba(0xFFFFFF))

    # the gun in a slot with a portal blue rim, its compass status below (the bottom row ends on the rim's face)
    vanilla_slot(px, 7 + 6 * 18, 17 + 1 * 18, rim=0x2E9BD6)
    vanilla_slot(px, 7 + 6 * 18, 17 + 2 * 18)

    # back: a small raised button in the top left corner, like a vanilla button
    kx0, ky0, kx1, ky1 = 9, 19, 22, 32
    rect(kx0 - 1, ky0 - 1, kx1 + 1, ky1 + 1, VANILLA_BLACK)
    rect(kx0, ky0, kx1, ky1, rgba(0x8B8B8B))
    rect(kx0, ky0, kx1, ky0, rgba(0xDADADA))
    rect(kx0, ky0, kx0, ky1, rgba(0xDADADA))
    rect(kx0, ky1, kx1, ky1, VANILLA_SHADOW)
    rect(kx1, ky0, kx1, ky1, VANILLA_SHADOW)
    back_arrow = ["...#....", "..##....", ".#######", "########", ".#######", "..##....", "...#...."]
    bxa, bya = kx0 + (kx1 - kx0 + 1 - 8) // 2, ky0 + (ky1 - ky0 + 1 - 7) // 2
    draw_art(px, bxa + 1, bya + 1, back_arrow, VANILLA_SHADOW)
    draw_art(px, bxa, bya, back_arrow, rgba(0xFFFFFF))

    # the tank gauge: a recessed glass tube with ticks, like a tall slot; the fill is a separate glyph
    gx0, gy0 = TANK_GAUGE_X - 2, TANK_GAUGE_Y - 2
    gx1, gy1 = TANK_GAUGE_X + TANK_GAUGE_W + 1, TANK_GAUGE_Y + TANK_GAUGE_H + 1
    rect(gx0, gy0, gx1, gy1, VANILLA_SLOT)
    rect(gx0, gy0, gx1 - 1, gy0, VANILLA_DARK)
    rect(gx0, gy0, gx0, gy1 - 1, VANILLA_DARK)
    rect(gx0 + 1, gy1, gx1, gy1, VANILLA_WHITE)
    rect(gx1, gy0 + 1, gx1, gy1, VANILLA_WHITE)
    rect(TANK_GAUGE_X, TANK_GAUGE_Y, TANK_GAUGE_X + TANK_GAUGE_W - 1, TANK_GAUGE_Y + TANK_GAUGE_H - 1, rgba(0x2B2F2C))
    for i in range(1, 4):
        y = TANK_GAUGE_Y + TANK_GAUGE_H * i // 4
        rect(TANK_GAUGE_X, y, TANK_GAUGE_X + 2, y, rgba(0x6B6B6B))
    rect(TANK_GAUGE_X + 1, TANK_GAUGE_Y + 2, TANK_GAUGE_X + 1, TANK_GAUGE_Y + TANK_GAUGE_H - 3, rgba(0x3A403C))
    return px


def tank_gauge_fill(level):
    w, h = TANK_GAUGE_W, TANK_GAUGE_H
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    filled = round(h * level / TANK_GAUGE_LEVELS)
    for y in range(h - filled, h):
        depth = (y - (h - filled)) / max(1, filled)
        for x in range(w):
            colour = mix(rgba(0x8CFF9E), rgba(0x139A3A), min(1.0, depth * 1.3))
            if x in (0, w - 1):
                colour = mix(colour, rgba(0x0A120E), 0.35)
            px[y][x] = colour
    if filled:
        for x in range(1, w - 1):
            px[h - filled][x] = rgba(0xE4FFE9)  # meniscus
        for bx, by in ((3, 0.35), (7, 0.6), (5, 0.82), (9, 0.2)):
            y = h - 1 - round((filled - 3) * by)
            if h - filled + 2 <= y < h:
                px[y][bx] = rgba(0xD6FFDF)
    return px


def tank_gauge_strip():
    strip = [[] for _ in range(TANK_GAUGE_H)]
    for level in range(TANK_GAUGE_LEVELS + 1):
        for y, row in enumerate(tank_gauge_fill(level)):
            strip[y].extend(row)
    return strip


def tank_font_providers():
    return [
        {
            "type": "bitmap",
            "file": f"{NAMESPACE}:font/tank_gui.png",
            "ascent": TANK_TITLE_BASELINE,
            "height": TANK_GUI_HEIGHT,
            "chars": [TANK_GUI],
        },
        {
            "type": "bitmap",
            "file": f"{NAMESPACE}:font/tank_gauge.png",
            "ascent": TANK_TITLE_BASELINE - TANK_GAUGE_Y,
            "height": TANK_GAUGE_H,
            "chars": ["".join(TANK_GAUGE_GLYPHS)],
        },
        {"type": "space", "advances": TANK_SPACES},
    ]


def vanilla_rim(px):
    """The vanilla window rim with rounded top corners, as in the chest and dispenser textures."""
    w = len(px[0])
    for y in range(len(px)):
        if y == 0:
            for x in range(2, 173):
                px[0][x] = VANILLA_BLACK
        elif y == 1:
            px[1][1] = VANILLA_BLACK
            for x in range(2, 173):
                px[1][x] = VANILLA_WHITE
            px[1][173] = VANILLA_BLACK
        elif y == 2:
            px[2][0] = VANILLA_BLACK
            for x in range(1, 173):
                px[2][x] = VANILLA_WHITE
            px[2][173] = VANILLA_FACE
            px[2][174] = VANILLA_BLACK
        else:
            px[y][0] = VANILLA_BLACK
            for x in range(1, w - 1):
                px[y][x] = VANILLA_FACE
            for x in range(1, 4 if y == 3 else 3):
                px[y][x] = VANILLA_WHITE
            px[y][173] = px[y][174] = VANILLA_SHADOW
            px[y][175] = VANILLA_BLACK



# --- settings menu (drawn dropper background) ------------------------------------------------------------------------
# For Java players with the pack the settings menu draws its own background like the tank menu: plain vanilla grey
# without the nine slots, a long recessed bar behind each colour column (up arrow, colour, down arrow), so the flat
# banners read as two sliders, and a socket behind each of the three buttons in the middle column. It needs the flat
# banners, so the glyph only draws in the flat_banners overlay; below, it is an empty space of the same advance and
# the menu stays vanilla. The dropper centres its title: SettingsGui.java moves to the window's edge and back with the
# shifts, so the whole title is 0 wide and the text centred on its own. Keep in sync with SettingsGui.java.
SETTINGS_GUI = "\ue9d0"
SETTINGS_SHIFTS = {chr(0xE9D1 + bit): -(1 << bit) for bit in range(8)}  # -1, -2, -4 ... -128
SETTINGS_GUI_HEIGHT = 72
SETTINGS_SLOT_X, SETTINGS_SLOT_Y = 62, 17  # the dropper's first item, slots 18 pixels apart
SETTINGS_BANNER_INSET, SETTINGS_BANNER_WIDTH = 4, 8  # a flat banner covers 8x16 pixels in the middle of its slot


def settings_gui_background():
    w, h = TANK_GUI_WIDTH, SETTINGS_GUI_HEIGHT
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    vanilla_rim(px)

    def recess(x0, y0, x1, y1, inside):
        """A recess like a vanilla slot's: dark rim top left, white bottom right, around x0..x1, y0..y1."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[y][x] = inside
        for x in range(x0 - 1, x1 + 1):
            px[y0 - 1][x] = VANILLA_DARK
        for y in range(y0 - 1, y1 + 1):
            px[y][x0 - 1] = VANILLA_DARK
        for x in range(x0, x1 + 2):
            px[y1 + 1][x] = VANILLA_WHITE
        for y in range(y0, y1 + 2):
            px[y][x1 + 1] = VANILLA_WHITE
        px[y0 - 1][x1 + 1] = px[y1 + 1][x0 - 1] = VANILLA_SLOT

    def banner_x(column):
        return SETTINGS_SLOT_X + 18 * column + SETTINGS_BANNER_INSET

    # the colour sliders: one bar through all three rows, between the banners in the black of the arrows' background
    # (the black dye's banner colour), so the arrows and the gaps join into one bar
    for column in (0, 2):
        x0 = banner_x(column)
        recess(x0, SETTINGS_SLOT_Y, x0 + SETTINGS_BANNER_WIDTH - 1, SETTINGS_SLOT_Y + 2 * 18 + 15, rgba(0x1D1D21))
    # the buttons: connection, close portals, shape
    x0 = banner_x(1)
    for row in (0, 1, 2):
        y0 = SETTINGS_SLOT_Y + 18 * row
        recess(x0, y0, x0 + SETTINGS_BANNER_WIDTH - 1, y0 + 15, VANILLA_SLOT)
    return px


def settings_font_providers(drawn):
    background = {"type": "space", "advances": {SETTINGS_GUI: TANK_GUI_WIDTH + 1}}
    if drawn:
        background = {
            "type": "bitmap",
            "file": f"{NAMESPACE}:font/settings_gui.png",
            "ascent": TANK_TITLE_BASELINE,
            "height": SETTINGS_GUI_HEIGHT,
            "chars": [SETTINGS_GUI],
        }
    return [background, {"type": "space", "advances": SETTINGS_SHIFTS}]


# --- command menus (drawn chest backgrounds) --------------------------------------------------------------------------
# The /porticlegun menus for Java players with the pack, drawn like the tank menu (a glyph in the chest title over the
# window's top half): the overview with recessed slots for the items, raised buttons for their recipes below and one
# for the admin menu in the bottom right corner, and the recipe page as a crafting table (3x3 grid, arrow, large result slot, back button). Keep in
# sync with MenuGui.java.
MENU_OVERVIEW = "\ue9e0"  # all items: five slots, four recipe buttons
MENU_OVERVIEW_ADMIN = "\ue9e1"  # the same with the admin button
MENU_SINGLE = "\ue9e2"  # only the PorticleGun
MENU_SINGLE_ADMIN = "\ue9e3"
MENU_RECIPE = "\ue9e4"
MENU_HEIGHT = 72
MENU_COLUMNS = [0, 2, 4, 6, 8]  # the items, a free column between them; their recipe buttons below


def chest_cell(column, row):
    """The top left corner of a chest slot's 18 pixel frame."""
    return 7 + 18 * column, 17 + 18 * row


def raised_button(px, x0, y0, size=18):
    """A raised vanilla button filling a slot's frame: black outline, light top left, dark bottom right."""
    x1, y1 = x0 + size - 1, y0 + size - 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            px[y][x] = VANILLA_BLACK if edge else rgba(0x8B8B8B)
    for x in range(x0 + 1, x1):
        px[y0 + 1][x] = rgba(0xDADADA)
        px[y1 - 1][x] = VANILLA_SHADOW
    for y in range(y0 + 1, y1):
        px[y][x0 + 1] = rgba(0xDADADA)
        px[y][x1 - 1] = VANILLA_SHADOW
    px[y1 - 1][x0 + 1] = px[y0 + 1][x1 - 1] = rgba(0x8B8B8B)
    # rounded like vanilla buttons
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        px[y][x] = VANILLA_FACE


def menu_overview_background(columns, admin):
    px = [[(0, 0, 0, 0)] * TANK_GUI_WIDTH for _ in range(MENU_HEIGHT)]
    vanilla_rim(px)
    for index, column in enumerate(columns):
        vanilla_slot(px, *chest_cell(column, 0))
        if index < 4:  # the rift shard has no recipe
            raised_button(px, *chest_cell(column, 1))
    if admin:
        raised_button(px, *chest_cell(8, 2))
    return px


def menu_recipe_background():
    px = [[(0, 0, 0, 0)] * TANK_GUI_WIDTH for _ in range(MENU_HEIGHT)]
    vanilla_rim(px)
    # back: the same small button as in the tank menu
    kx0, ky0, kx1, ky1 = 9, 19, 22, 32
    for y in range(ky0 - 1, ky1 + 2):
        for x in range(kx0 - 1, kx1 + 2):
            px[y][x] = VANILLA_BLACK
    for y in range(ky0, ky1 + 1):
        for x in range(kx0, kx1 + 1):
            px[y][x] = rgba(0x8B8B8B)
    for x in range(kx0, kx1 + 1):
        px[ky0][x] = rgba(0xDADADA)
        px[ky1][x] = VANILLA_SHADOW
    for y in range(ky0, ky1 + 1):
        px[y][kx0] = rgba(0xDADADA)
        px[y][kx1] = VANILLA_SHADOW
    back_arrow = ["...#....", "..##....", ".#######", "########", ".#######", "..##....", "...#...."]
    bxa, bya = kx0 + (kx1 - kx0 + 1 - 8) // 2, ky0 + (ky1 - ky0 + 1 - 7) // 2
    draw_art(px, bxa + 1, bya + 1, back_arrow, VANILLA_SHADOW)
    draw_art(px, bxa, bya, back_arrow, rgba(0xFFFFFF))
    # the crafting grid
    for row in range(3):
        for column in range(1, 4):
            vanilla_slot(px, *chest_cell(column, row))
    # the arrow of the vanilla crafting table, between grid and result
    arrow = [
        "..............##......",
        "..............###.....",
        "..............####....",
        "..............#####...",
        "####################..",
        "#####################.",
        "######################",
        "#####################.",
        "####################..",
        "..............#####...",
        "..............####....",
        "..............###.....",
        "..............##......",
    ]
    draw_art(px, 89, 37, arrow, VANILLA_SLOT)
    # the result: a large slot around the item in column 6, middle row
    rx0, ry0 = 8 + 18 * 6 - 5, 18 + 18 - 5
    size = 26
    for y in range(ry0, ry0 + size):
        for x in range(rx0, rx0 + size):
            px[y][x] = VANILLA_SLOT
    for x in range(rx0, rx0 + size - 1):
        px[ry0][x] = VANILLA_DARK
    for y in range(ry0, ry0 + size - 1):
        px[y][rx0] = VANILLA_DARK
    for x in range(rx0 + 1, rx0 + size):
        px[ry0 + size - 1][x] = VANILLA_WHITE
    for y in range(ry0 + 1, ry0 + size):
        px[y][rx0 + size - 1] = VANILLA_WHITE
    return px


MENU_BACKGROUNDS = {
    MENU_OVERVIEW: ("menu_overview", lambda: menu_overview_background(MENU_COLUMNS, False)),
    MENU_OVERVIEW_ADMIN: ("menu_overview_admin", lambda: menu_overview_background(MENU_COLUMNS, True)),
    MENU_SINGLE: ("menu_single", lambda: menu_overview_background([4], False)),
    MENU_SINGLE_ADMIN: ("menu_single_admin", lambda: menu_overview_background([4], True)),
    MENU_RECIPE: ("menu_recipe", menu_recipe_background),
}


def menu_font_providers():
    return [
        {
            "type": "bitmap",
            "file": f"{NAMESPACE}:font/{name}.png",
            "ascent": TANK_TITLE_BASELINE,
            "height": MENU_HEIGHT,
            "chars": [char],
        }
        for char, (name, _) in MENU_BACKGROUNDS.items()
    ]


# --- flat banners in the settings menu --------------------------------------------------------------------------------
# The settings menu shows its banners (colours, arrows, close, shape) flat and from the front, without pole: like a
# wall banner, filling the slot. The plugin points those banners (only there, only for players with the pack) at
# porticlegun:gui_banner_<colour>. "attachment": "wall" is read by 26.3+ clients (pack format 97), so the flat look
# comes from an overlay; older clients get the vanilla banner from the same item definitions in the base pack.
# The wall banner spans y -20.5..19.5 model pixels around x 0 (BannerModel/BannerFlagModel), scaled by 2/3 and moved
# like vanilla's banner items; the display centres it in the slot.
FLAT_BANNERS_OVERLAY = "flat_banners"
FLAT_BANNERS_FORMAT = 97
DYE_COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
               "purple", "blue", "brown", "green", "red", "black"]
BANNER_ITEM_TRANSFORMATION = {
    "left_rotation": [0.0, 0.0, 0.0, 1.0],
    "right_rotation": [0.0, 0.0, 0.0, 1.0],
    "scale": [2 / 3, -2 / 3, -2 / 3],
    "translation": [0.5, 0.0, 0.5],
}
FLAT_BANNER_SCALE = 0.6  # 20x40 model pixels * 2/3 * 0.6 = exactly 8x16 slot pixels


def gui_banner_definition(colour, flat):
    banner = {"type": "minecraft:banner", "color": colour}
    if flat:
        banner["attachment"] = "wall"
    return {
        "model": {
            "type": "minecraft:special",
            "base": f"{NAMESPACE}:item/gui_banner" if flat else "minecraft:item/template_banner",
            "model": banner,
            "transformation": BANNER_ITEM_TRANSFORMATION,
        }
    }


def flat_banner_model():
    # the wall banner's middle, in the item's space (the transformation flips the model vertically); the display
    # lifts it from there to the slot's centre
    centre = ((-20.5 / 16) + (19.5 / 16)) / 2 * -(2 / 3)
    lift = round((0.5 - centre) * 16 * FLAT_BANNER_SCALE, 3)
    return {
        "gui_light": "front",
        "textures": {"particle": "minecraft:block/oak_planks"},
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, lift, 0], "scale": [FLAT_BANNER_SCALE] * 3},
        },
    }


# --- flat items and the rift portal ---------------------------------------------------------------------------------
# 16x16 like every vanilla item, so they sit naturally next to vanilla and texture pack items: dark outline, light
# from the top left, few shades per colour. The portal fluid uses the silhouette of a vanilla potion bottle. Java
# animates them (frames stacked vertically); Bedrock cannot animate item icons and shows the first frame.
FLAT_ITEM_FRAMES = 8
FLAT_ITEM_PALETTES = {
    "rift_shard": {
        "o": 0x1D0F2E, "l": 0xE3CCFF, "L": 0xB58CF0, "G": 0x7CFF8A, "P": 0x7B45C2, "D": 0x4A2580, "w": 0xFFFFFF,
    },
    "portal_fluid": {
        "o": 0x26363B, "g": 0xA9C7CF, "w": 0xFFFFFF, "h": 0xC8FFB0, "l": 0x8CFF82, "L": 0x58E85F, "M": 0x2DB84A,
        "D": 0x13803A, "c": 0x9C6A3C, "C": 0x6B4423, "b": 0xE4FFD8,
    },
}
FLAT_ITEM_ART = {
    # a splinter of the space between dimensions: lavender shell, glowing green core
    "rift_shard": [
        "................",
        "...........oo...",
        "..........olwo..",
        ".........olLLo..",
        "........olLGLo..",
        ".......olLGGo...",
        "......olLGGPo...",
        ".....olLGGPo....",
        "....olLGGPo.....",
        "...olLGGPo......",
        "...oLGGPo.......",
        "..oLGPPo........",
        "..oPPPo.........",
        ".oPPDo..........",
        ".oooo...........",
        "................",
    ],
    # glowing green portal fluid in a corked vanilla-style potion bottle
    "portal_fluid": [
        "................",
        "......cccc......",
        ".....oCccCo.....",
        "......oggo......",
        "......ogwo......",
        ".....oglLgo.....",
        "....oglLLLgo....",
        "...oghLLLLLMo...",
        "...ohLLLLLLMo...",
        "..oghLLLLLMMDo..",
        "..ogLLLLLMMMDo..",
        "..ogLLLLMMMDDo..",
        "...oMLLMMMDDo...",
        "...ooMMMDDDoo...",
        ".....oooooo.....",
        "................",
    ],
}
FLUID = set("hlLMD")
FLAT_ITEM_PALETTES["rift_compass"] = {
    "o": 0x2B1F0A, "b": 0xE8C764, "B": 0xC9A23F, "d": 0x8E6E22, "f": 0x1F2A24, "F": 0x2C3B33, "g": 0x7CFF8A,
    "G": 0x39FF5A, "p": 0xB58CF0, "w": 0xFFFFFF, "c": 0xE3CCFF,
}
FLAT_ITEM_ART["rift_compass"] = [
    "................",
    ".......cc.......",
    "......opco......",
    ".....oooooo.....",
    "...oobbbbBBoo...",
    "..obbFFFFFFBdo..",
    "..obFFfffffFdo..",
    ".obFFffffffFFdo.",
    ".obFfffffffffdo.",
    ".obFffffffffFdo.",
    ".obFFffffffFFdo.",
    "..obFFfffffFdo..",
    "..oBBFFFFFFddo..",
    "...ooBBdddddo...",
    ".....oooooo.....",
    "................",
]


def flat_item_frames(item):
    art = FLAT_ITEM_ART[item]
    palette = {key: hex_rgb(value) for key, value in FLAT_ITEM_PALETTES[item].items()}
    frames = []
    for frame in range(FLAT_ITEM_FRAMES):
        phase = frame / FLAT_ITEM_FRAMES
        grid = [list(row) for row in art]
        colors = {}
        if item == "rift_shard":
            # a shimmer runs up the green core, a glint travels along the lit edge
            edge = sorted(((x, y) for y, row in enumerate(art) for x, c in enumerate(row) if c == "l"),
                          key=lambda p: p[0] - p[1])
            gx, gy = edge[int(phase * len(edge)) % len(edge)]
            grid[gy][gx] = "w"
            for y, row in enumerate(art):
                for x, c in enumerate(row):
                    if c == "G":
                        wave = math.cos(2 * math.pi * ((x - y) / 18 - phase))
                        colors[(x, y)] = shade(palette["G"], 0.8 + 0.3 * max(0.0, wave))
        elif item == "rift_compass":
            # the needle spins around the centre of the face, its tip glowing green
            angle = 2 * math.pi * phase
            cx, cy = 7.5, 9.0
            for step in range(1, 4):
                tip = (round(cx + math.sin(angle) * step), round(cy - math.cos(angle) * step))
                tail = (round(cx - math.sin(angle) * step), round(cy + math.cos(angle) * step))
                for (x, y), char in ((tip, "G" if step < 3 else "g"), (tail, "p")):
                    if art[y][x] == "f":
                        grid[y][x] = char
            grid[9][7] = "w"
        else:
            # bubbles rise through the fluid, which pulses gently
            pulse = 1 + 0.06 * math.sin(2 * math.pi * phase)
            for y, row in enumerate(art):
                for x, c in enumerate(row):
                    if c in FLUID:
                        colors[(x, y)] = shade(palette[c], pulse)
            for bubble_x, offset in ((6, 0.0), (9, 0.5)):
                bubble_y = 12 - int(((phase + offset) % 1) * 7)
                if art[bubble_y][bubble_x] in FLUID:
                    grid[bubble_y][bubble_x] = "b"
        pixels = []
        for y, row in enumerate(grid):
            line = []
            for x, c in enumerate(row):
                if c == ".":
                    line.append((0, 0, 0, 0))
                    continue
                # animated colours only apply where the base art is unchanged (not on glints and bubbles)
                color = colors.get((x, y), palette[c]) if c == art[y][x] else palette[c]
                line.append((*color, 255))
            pixels.append(line)
        frames.append(pixels)
    return frames


def flat_item_texture(item):
    """The first frame, used for Bedrock icons and renders."""
    return flat_item_frames(item)[0]


def java_flat_item_model(item):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NAMESPACE}:item/{item}"}}


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


def rotate_face(quad, normal, rotation, offset):
    if not rotation:
        return quad, normal
    origin = [rotation["origin"][i] + offset[i] for i in range(3)]
    tip = rotated([origin[i] + normal[i] for i in range(3)], rotation, offset)
    return [rotated(p, rotation, offset) for p in quad], tuple(tip[i] - origin[i] for i in range(3))


def face_pixels(polygon, points, size):
    """Pixels covered by a projected face (a parallelogram), with their depth and texture coordinates."""
    (ox, oy), (ax, ay), _, (bx, by) = polygon
    ux, uy, vx, vy = ax - ox, ay - oy, bx - ox, by - oy
    det = ux * vy - uy * vx
    if abs(det) < 1e-9:
        return
    z0, za, zb = points[0][2], points[1][2], points[3][2]
    min_x = max(0, int(min(p[0] for p in polygon)))
    max_x = min(size - 1, int(max(p[0] for p in polygon)) + 1)
    min_y = max(0, int(min(p[1] for p in polygon)))
    max_y = min(size - 1, int(max(p[1] for p in polygon)) + 1)
    for py in range(min_y, max_y + 1):
        for px in range(min_x, max_x + 1):
            dx, dy = px + 0.5 - ox, py + 0.5 - oy
            u = (dx * vy - dy * vx) / det
            v = (ux * dy - uy * dx) / det
            if 0 <= u <= 1 and 0 <= v <= 1:
                yield px, py, z0 + u * (za - z0) + v * (zb - z0), u, v


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


def glowing(material):
    return material.startswith("glow") or material in EMISSIVE


def material_color(material):
    x, y, size = REGIONS[bedrock_material(material)]
    return ATLAS[y + size // 2][x + size // 2][:3]


def render(yaw, pitch, size, margin=1, outline=True, model=PORTAL_GUN):
    """Orthographic render from the given angle, scaled to fit a size x size image."""
    transform = make_view(yaw, pitch)
    elements = model["elements"]
    faces = []
    for index, element in enumerate(elements):
        start, end = placed(element, model["offset"])
        for direction, (quad, normal) in face_quads(start, end).items():
            quad, normal = rotate_face(quad, normal, element.get("rotation"), model["offset"])
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
    depths = [[-math.inf] * size for _ in range(size)]
    for depth, index, direction, points, material in faces:
        polygon = [((p[0] - cx) * scale + size / 2, (cy - p[1]) * scale + size / 2) for p in points]
        color = material_color(material)
        if not glowing(material):
            color = shade(color, FACE_SHADE[direction])
        for px, py, z, u, v in face_pixels(polygon, points, size):
            if z > depths[py][px]:
                depths[py][px] = z
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
                        if not glowing(elements[owner[py][px]]["material"]):
                            result[py][px] = (*shade(image[py][px][:3], 0.55), 255)
                        break
        image = result
    return image


def showcase_faces(yaw, pitch, model):
    """The visible faces of a model seen from yaw/pitch: (depth, direction, projected points, material)."""
    transform = make_view(yaw, pitch)
    faces = []
    for element in model["elements"]:
        start, end = placed(element, model["offset"])
        for direction, (quad, normal) in face_quads(start, end).items():
            quad, normal = rotate_face(quad, normal, element.get("rotation"), model["offset"])
            n = transform((normal[0] + 8, normal[1] + 8, normal[2] + 8))
            if n[2] <= 1e-6:
                continue
            points = [transform(p) for p in quad]
            faces.append((sum(p[2] for p in points) / 4, direction, points, element["material"]))
    return faces


def showcase_fit(views, model):
    """Framing (centre x, centre y, span) that holds the model in every (yaw, pitch) view, so a turntable keeps
    one scale instead of zooming in and out while it turns."""
    points = [p for yaw, pitch in views for face in showcase_faces(yaw, pitch, model) for p in face[2]]
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2, max(max(xs) - min(xs), max(ys) - min(ys))


def render_showcase(yaw, pitch, size, supersample=3, margin=0.06, tints=None, model=PORTAL_GUN, fit=None):
    """High quality render for the README: textured faces, smooth edges, transparent background.

    tints: optional (primary, secondary) RGB ints; the lights are then coloured like Java tints them in game.
    fit: fixed framing from showcase_fit; by default the model fills the image in this one view.
    """
    full = size * supersample
    faces = showcase_faces(yaw, pitch, model)
    cx, cy, span = fit or showcase_fit([(yaw, pitch)], model)
    scale = full * (1 - 2 * margin) / span

    canvas = [[None] * full for _ in range(full)]
    depths = [[-math.inf] * full for _ in range(full)]
    for depth, direction, points, material in faces:
        polygon = [((p[0] - cx) * scale + full / 2, (cy - p[1]) * scale + full / 2) for p in points]
        tint = None
        if tints and material in ("glow0", "glow1"):
            tint = hex_rgb(tints[0 if material == "glow0" else 1])
            tx0, ty0, tile = REGIONS["glow"]
        else:
            tx0, ty0, tile = REGIONS[bedrock_material(material)]
        factor = 1.0 if glowing(material) else FACE_SHADE[direction]
        for px, py, z, u, v in face_pixels(polygon, points, full):
            if z <= depths[py][px]:
                continue
            texel = ATLAS[ty0 + min(tile - 1, int((1 - v) * tile))][tx0 + min(tile - 1, int(u * tile))]
            color = shade(texel[:3], factor)
            if tint:
                color = shade(tuple(c * t // 255 for c, t in zip(texel[:3], tint)), factor)
            depths[py][px] = z
            canvas[py][px] = color

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


# portal colour pairs (particle colours from config.yml): the default gold/dark_blue, then red/green,
# light_purple/aqua and yellow/white
COLOUR_VARIANTS = [(0xFFA500, 0x0000AA), (0xFF0000, 0x00FF00), (0xFF00FF, 0x00FFFF), (0xFFFF00, 0xFFFFFF)]


def render_colour_variants(size=360):
    """One row of guns in different portal colours, as each player's gun is tinted in game."""
    renders = [render_showcase(-35, 22, size, tints=pair) for pair in COLOUR_VARIANTS]
    return [sum((render[y] for render in renders), []) for y in range(size)]


def render_divider(width=1200, height=8):
    """Section divider for the README: the default primary colour fading into the secondary one."""
    left, right = hex_rgb(BEDROCK_PRIMARY), hex_rgb(BEDROCK_SECONDARY)
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            t = x / (width - 1)
            color = tuple(round(a + (b - a) * t) for a, b in zip(left, right))
            # fade out towards both ends and the top/bottom edge
            edge = min(1.0, min(x, width - 1 - x) / (width * 0.12))
            vertical = 1.0 - abs(y - (height - 1) / 2) / (height / 2)
            row.append((*color, round(255 * edge * max(0.0, vertical) ** 0.6)))
        rows.append(row)
    return rows


def render_hud_preview(primary=0xFFA500, secondary=0x3F7BFF, unit=8, width=560, height=220):
    """Mock-up of the overlay: the rings as Minecraft draws them in a title (4x scale, with drop shadow)."""
    cx, cy = width // 2, height // 2
    rows = []
    for y in range(height):
        t = y / (height - 1)
        top, bottom = (54, 93, 140), (138, 176, 214)
        rows.append([(*[round(a + (b - a) * t) for a, b in zip(top, bottom)], 255) for _ in range(width)])

    def plot(x, y, color):
        if 0 <= x < width and 0 <= y < height:
            rows[y][x] = (*color, 255)

    # crosshair (Minecraft draws it inverted; white reads well on the sky)
    arm = unit * 2 // 3 * 4
    for d in range(-arm, arm + 1):
        for w in range(-unit // 3, unit // 3 + 1):
            plot(cx + d, cy + w, (240, 240, 240))
            plot(cx + w, cy + d, (240, 240, 240))

    glyphs = list(HUD_GLYPHS.values())
    pixel = unit / 2  # glyphs are drawn at double resolution
    # left ring (open, primary) from -8 units, right ring (closed, secondary) from +4 units, 8 units high
    for rows_art, left_unit, color in ((glyphs[1], -8, hex_rgb(primary)), (glyphs[2], 4, hex_rgb(secondary))):
        for shadow in (True, False):
            offset = unit if shadow else 0
            tint = shade(color, 0.25) if shadow else color
            for gy, line in enumerate(rows_art):
                for gx, value in enumerate(line):
                    if value != "#":
                        continue
                    x0 = round(cx + left_unit * unit + gx * pixel) + offset
                    y0 = round(cy - 4 * unit + gy * pixel) + offset
                    for yy in range(math.ceil(pixel)):
                        for xx in range(math.ceil(pixel)):
                            plot(x0 + xx, y0 + yy, tint)
    return rows


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


def enlarge(pixels, factor):
    return [[p for p in row for _ in range(factor)] for row in pixels for _ in range(factor)]


def render_rift_showcase(size=360):
    """The interdimensional gun from empty to full tank, as the tank drains in game."""
    renders = [
        render_showcase(-35, 22, size, model={**RIFT_GUN, "elements": elements}) for elements in RIFT_GUN["variants"]
    ]
    return [sum((render[y] for render in renders), []) for y in range(size)]


def render_items_showcase(size=300, gap=50):
    """The new items side by side, all in 3D: rift shard, portal fluid and rift compass."""
    images = [
        render_showcase(-35, 22, size, model=RIFT_SHARD),
        render_showcase(-35, 22, size, model=PORTAL_FLUID),
        render_showcase(-35, 35, size, model=RIFT_COMPASS),
    ]
    rows = []
    for y in range(size):
        row = []
        for index, image in enumerate(images):
            if index:
                row += [(0, 0, 0, 0)] * gap
            row += image[y]
        rows.append(row)
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="directory for preview renders")
    parser.add_argument("--showcase", action="store_true", help="also render the README images in images/")
    args = parser.parse_args()
    for model in MODELS:
        for elements in model.get("variants", [model["elements"]]) + [model.get("legacy_elements")]:
            if elements:
                check_model({**model, "elements": elements})

    java = os.path.join(OUT, "java")
    java_assets = os.path.join(java, "assets", NAMESPACE)
    write_json(os.path.join(java, "pack.mcmeta"), java_pack_mcmeta())
    write_png(os.path.join(java_assets, "textures", "item", f"{ATLAS_NAME}.png"), ATLAS)
    for model in MODELS:
        item = model["item"]
        write_json(os.path.join(java_assets, "items", f"{item}.json"), java_item_definition(model))
        if "variants" in model:
            for level, elements in enumerate(model["variants"]):
                write_json(os.path.join(java_assets, "models", "item", f"{item}_{level}.json"),
                           java_model(model, elements))
        elif "legacy_elements" in model:
            write_json(os.path.join(java_assets, "models", "item", f"{item}.json"),
                       java_model(model, model["legacy_elements"]))
            write_json(os.path.join(java, FREE_ROTATION_OVERLAY, "assets", NAMESPACE, "models", "item", f"{item}.json"),
                       java_model(model))
        else:
            write_json(os.path.join(java_assets, "models", "item", f"{item}.json"), java_model(model))
    for entry in FLAT_ITEMS:
        item = entry["item"]
        write_json(os.path.join(java_assets, "models", "item", f"{item}_icon.json"), java_flat_item_model(item))
        write_png(os.path.join(java_assets, "textures", "item", f"{item}.png"),
                  [row for frame in flat_item_frames(item) for row in frame])
        write_json(os.path.join(java_assets, "textures", "item", f"{item}.png.mcmeta"), {"animation": {"frametime": 3}})
    write_png(os.path.join(java, "pack.png"), render(*ICON_VIEW, 128, margin=6))
    write_json(os.path.join(java, "assets", "minecraft", "font", "default.json"), java_font())
    write_png(os.path.join(java_assets, "textures", "font", "portal_hud.png"), hud_glyph_strip())
    write_png(os.path.join(java_assets, "textures", "font", "tank_gui.png"), tank_gui_background())
    write_png(os.path.join(java_assets, "textures", "font", "tank_gauge.png"), tank_gauge_strip())
    write_png(os.path.join(java_assets, "textures", "font", "settings_gui.png"), settings_gui_background())
    for name, draw in MENU_BACKGROUNDS.values():
        write_png(os.path.join(java_assets, "textures", "font", f"{name}.png"), draw())
    write_json(os.path.join(java, FLAT_BANNERS_OVERLAY, "assets", "minecraft", "font", "default.json"),
               java_font(drawn_settings=True))
    for colour in DYE_COLOURS:
        write_json(os.path.join(java_assets, "items", f"gui_banner_{colour}.json"), gui_banner_definition(colour, False))
        write_json(os.path.join(java, FLAT_BANNERS_OVERLAY, "assets", NAMESPACE, "items", f"gui_banner_{colour}.json"),
                   gui_banner_definition(colour, True))
    write_json(os.path.join(java, FLAT_BANNERS_OVERLAY, "assets", NAMESPACE, "models", "item", "gui_banner.json"),
               flat_banner_model())
    # buttons and frame slots of the tank menu: items that draw nothing, so only the background shows
    write_json(os.path.join(java_assets, "items", "gui_blank.json"), {"model": {"type": "minecraft:empty"}})

    bedrock = os.path.join(OUT, "bedrock")
    write_json(os.path.join(bedrock, "manifest.json"), bedrock_manifest())
    write_png(os.path.join(bedrock, "textures", "entity", NAMESPACE, f"{ATLAS_NAME}.png"), ATLAS)
    for model in MODELS:
        item = model["item"]
        write_json(os.path.join(bedrock, "attachables", f"{item}.json"), bedrock_attachable(model))
        write_json(os.path.join(bedrock, "models", "entity", f"{item}.geo.json"), bedrock_geometry(model))
        write_json(os.path.join(bedrock, "animations", f"{item}.animation.json"), bedrock_animations(model))
        icon_pixels = flat_item_texture(item) if model.get("flat_icon") else render(*ICON_VIEW, 32, model=model)
        write_png(os.path.join(bedrock, "textures", "items", f"{item}.png"), icon_pixels)
    write_json(os.path.join(bedrock, "textures", "item_texture.json"), bedrock_item_texture())
    write_png(os.path.join(bedrock, "pack_icon.png"), render(*ICON_VIEW, 128, margin=6))
    write_png(os.path.join(bedrock, "font", "glyph_E9.png"), bedrock_glyph_sheet())

    write_json(os.path.join(OUT, "geyser", f"{NAMESPACE}_mappings.json"), geyser_mappings())

    if args.showcase:
        images = os.path.join(ROOT, "images")
        write_png(os.path.join(images, "portal_gun_3d.png"), render_showcase(-35, 22, 720))
        write_png(os.path.join(images, "portal_gun_colors.png"), render_colour_variants())
        write_png(os.path.join(images, "divider.png"), render_divider())
        write_png(os.path.join(images, "portal_hud.png"), render_hud_preview())
        write_png(os.path.join(images, "interdimensional_gun_3d.png"), render_showcase(-35, 22, 720, model=RIFT_GUN))
        write_png(os.path.join(images, "interdimensional_gun_tank.png"), render_rift_showcase())
        write_png(os.path.join(images, "rift_items.png"), render_items_showcase())

    if args.preview:
        views = {"iso": (-35, 25), "iso_back": (145, 25), "side": (0, 0), "top": (0, 90), "rear": (90, 0)}
        for gun in MODELS:
            for name, (yaw, pitch) in views.items():
                write_png(os.path.join(args.preview, f"{gun['item']}_{name}.png"),
                          render(yaw, pitch, 320, margin=12, model=gun))
            write_png(os.path.join(args.preview, f"{gun['item']}_icon_x8.png"),
                      enlarge(render(*ICON_VIEW, 32, model=gun), 8))


if __name__ == "__main__":
    main()
