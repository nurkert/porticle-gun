#!/usr/bin/env python3
"""Validates the generated Bedrock pack and Geyser mapping.

    python3 scripts/validate_bedrock_pack.py [--schemas <Minecraft-bedrock-json-schemas checkout>]

Checks every cross reference Bedrock and Geyser resolve at runtime (icon, attachable, geometry, animations, bones,
textures, UVs), because a broken reference does not produce an error anywhere: the item is simply invisible. With
--schemas, every file is also validated against the Blockception Bedrock JSON schemas
(https://github.com/Blockception/Minecraft-bedrock-json-schemas), which needs the jsonschema package.
"""

import argparse
import json
import os
import re
import struct
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
PACK = os.path.join(ROOT, "src", "main", "resourcepack", "bedrock")
MAPPINGS = os.path.join(ROOT, "src", "main", "resourcepack", "geyser", "porticlegun_mappings.json")

SCHEMAS = {
    "manifest.json": "general/manifest.json",
    "attachables/*.json": "resource/attachables/attachables.json",
    "models/entity/*.json": "resource/models/entity/model_entity.json",
    "animations/*.json": "resource/animations/actor_animation.json",
    "textures/item_texture.json": "resource/textures/item_texture.json",
}

errors = []


def error(message):
    errors.append(message)


def load(path):
    with open(path, encoding="utf-8") as file:
        return json.load(file)


def png_size(path):
    with open(path, "rb") as file:
        header = file.read(24)
    if header[:8] != b"\x89PNG\r\n\x1a\n":
        error(f"{os.path.relpath(path, PACK)} is not a PNG")
        return None
    return struct.unpack(">II", header[16:24])


def texture_file(reference):
    path = os.path.join(PACK, reference + ".png")
    if not os.path.isfile(path):
        error(f"texture '{reference}' does not exist in the pack")
        return None
    return path


def files(pattern):
    directory, name = os.path.split(pattern)
    regex = re.compile("^" + re.escape(name).replace(r"\*", ".*") + "$")
    folder = os.path.join(PACK, directory)
    if not os.path.isdir(folder):
        return []
    return [os.path.join(folder, f) for f in sorted(os.listdir(folder)) if regex.match(f)]


def check_manifest():
    manifest = load(os.path.join(PACK, "manifest.json"))
    uuid = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    header, modules = manifest.get("header", {}), manifest.get("modules", [])
    ids = [header.get("uuid")] + [module.get("uuid") for module in modules]
    for value in ids:
        if not isinstance(value, str) or not uuid.match(value):
            error(f"manifest uuid '{value}' is not a lower case UUID")
    if len(set(ids)) != len(ids):
        error("manifest header and module UUIDs must differ")
    if not any(module.get("type") == "resources" for module in modules):
        error("manifest has no resources module")


def check_references():
    mappings = load(MAPPINGS)
    if mappings.get("format_version") != 2:
        error("Geyser mapping must use format_version 2")
    definitions = [d for items in mappings.get("items", {}).values() for d in items]

    item_texture = load(os.path.join(PACK, "textures", "item_texture.json"))
    if item_texture.get("texture_name") != "atlas.items":
        error("item_texture.json must define atlas.items")
    texture_data = item_texture.get("texture_data", {})

    attachables = {}
    for path in files("attachables/*.json"):
        description = load(path)["minecraft:attachable"]["description"]
        attachables[description["identifier"]] = description

    geometries = {}
    for path in files("models/entity/*.json"):
        for geometry in load(path)["minecraft:geometry"]:
            geometries[geometry["description"]["identifier"]] = geometry

    animations = {}
    for path in files("animations/*.json"):
        animations.update(load(path)["animations"])

    for definition in definitions:
        identifier = definition.get("bedrock_identifier", "")
        if ":" not in identifier or identifier.startswith("minecraft:"):
            error(f"bedrock_identifier '{identifier}' needs its own namespace")

        # the icon Geyser looks up: explicit, or the identifier with ':' -> '.' and '/' -> '_'
        icon = definition.get("bedrock_options", {}).get("icon")
        icon = icon or identifier.replace(":", ".").replace("/", "_")
        if icon not in texture_data:
            error(f"icon '{icon}' of {identifier} is missing in item_texture.json (the item would be invisible)")
        else:
            textures = texture_data[icon]["textures"]
            for reference in [textures] if isinstance(textures, str) else textures:
                texture_file(reference)

        attachable = attachables.get(identifier)
        if attachable is None:
            # flat items (shards, bottles) are meant to be icons; guns need their 3D attachable
            if identifier.endswith("_gun"):
                error(f"no attachable for {identifier}, it would be shown as a flat icon")
            continue
        check_attachable(identifier, attachable, geometries, animations)


def check_attachable(identifier, attachable, geometries, animations):
    for key, reference in attachable.get("textures", {}).items():
        if not reference.startswith("textures/misc/"):  # vanilla textures like the enchantment glint
            texture_file(reference)

    geometry_id = attachable.get("geometry", {}).get("default")
    geometry = geometries.get(geometry_id)
    if geometry is None:
        error(f"attachable {identifier} uses unknown geometry '{geometry_id}'")
        return

    bones = {bone["name"]: bone for bone in geometry["bones"]}
    if not any("binding" in bone for bone in bones.values()):
        error(f"geometry '{geometry_id}' has no bone bound to the hand")
    for bone in bones.values():
        if bone.get("parent") and bone["parent"] not in bones:
            error(f"bone '{bone['name']}' has unknown parent '{bone['parent']}'")
    if not any(bone.get("cubes") for bone in bones.values()):
        error(f"geometry '{geometry_id}' has no cubes")

    width = geometry["description"]["texture_width"]
    height = geometry["description"]["texture_height"]
    for bone in bones.values():
        for cube in bone.get("cubes", []):
            for face, uv in cube.get("uv", {}).items():
                u, v = uv["uv"]
                du, dv = uv["uv_size"]
                for value, limit in ((u, width), (u + du, width), (v, height), (v + dv, height)):
                    if not -1e-6 <= value <= limit + 1e-6:
                        error(f"bone '{bone['name']}' face {face} UV {uv} leaves the {width}x{height} texture")

    declared = attachable.get("animations", {})
    for entry in attachable.get("scripts", {}).get("animate", []):
        for short_name in entry if isinstance(entry, dict) else [entry]:
            if short_name not in declared:
                error(f"attachable {identifier} animates undeclared '{short_name}'")
    for short_name, full_name in declared.items():
        animation = animations.get(full_name)
        if animation is None:
            error(f"animation '{full_name}' ({short_name}) is not defined")
            continue
        for bone in animation.get("bones", {}):
            if bone not in bones:
                error(f"animation '{full_name}' moves unknown bone '{bone}'")

    texture = texture_file(attachable["textures"]["default"])
    if texture:
        size = png_size(texture)
        if size and size[0] != size[1]:
            error(f"attachable texture should be square, is {size}")


def check_hud():
    """The overlay glyphs must exist in the Java font, the Bedrock glyph sheet and PortalHud.java alike."""
    java_pack = os.path.join(ROOT, "src", "main", "resourcepack", "java")
    font = load(os.path.join(java_pack, "assets", "minecraft", "font", "default.json"))
    glyphs = ""
    for provider in font["providers"]:
        if provider["type"] != "bitmap":
            continue
        namespace, path = provider["file"].split(":", 1)
        texture = os.path.join(java_pack, "assets", namespace, "textures", path)
        if not os.path.isfile(texture):
            error(f"Java font texture {provider['file']} is missing")
            continue
        chars = "".join(provider["chars"])
        width, height = png_size(texture) or (0, 0)
        # Minecraft only requires ascent <= height; a negative ascent moves a glyph down (the tank gauge)
        if len(chars) == 0 or width % len(chars) or provider["ascent"] > provider["height"]:
            error(f"Java font provider for {provider['file']} does not match its texture")
        glyphs += chars
    for char in glyphs:
        if not 0xE000 <= ord(char) <= 0xF8FF or 0xE000 <= ord(char) <= 0xE1FF:
            error(f"overlay glyph U+{ord(char):04X} must be in the Private Use Area outside Bedrock's E0xx/E1xx icons")

    # Bedrock only needs the HUD glyphs; the tank menu's are drawn for Java players with the pack only
    hud_glyphs = "".join(font["providers"][0]["chars"])
    sheets = {ord(char) >> 8 for char in hud_glyphs}
    for high in sheets:
        sheet = os.path.join(PACK, "font", f"glyph_{high:02X}.png")
        if not os.path.isfile(sheet):
            error(f"Bedrock glyph sheet font/glyph_{high:02X}.png is missing")
        elif png_size(sheet) != (256, 256):
            error(f"font/glyph_{high:02X}.png must be 256x256")

    java_source = os.path.join(ROOT, "src", "main", "java", "eu", "nurkert", "porticlegun", "visualization",
                               "PortalHud.java")
    with open(java_source, encoding="utf-8") as file:
        source = file.read()
    used = {chr(int(code, 16)) for code in re.findall(r"static final char \w+ = \(char\) 0x([0-9A-Fa-f]{4});", source)}
    if not used:
        error("found no glyph constants in PortalHud.java")
    space_glyphs = {c for p in font["providers"] if p["type"] == "space" for c in p["advances"]}
    missing = used - set(glyphs) - space_glyphs
    if missing:
        error("PortalHud.java uses glyphs missing from the font: " + ", ".join(f"U+{ord(c):04X}" for c in missing))

    tank_source = os.path.join(ROOT, "src", "main", "java", "eu", "nurkert", "porticlegun", "rift", "TankGui.java")
    with open(tank_source, encoding="utf-8") as file:
        tank = {chr(int(code, 16)) for code in re.findall(r"static final char \w+ = '\\u([0-9A-Fa-f]{4})';",
                                                          file.read())}
    if not tank:
        error("found no glyph constants in TankGui.java")
    missing = tank - set(glyphs) - space_glyphs
    if missing:
        error("TankGui.java uses glyphs missing from the font: " + ", ".join(f"U+{ord(c):04X}" for c in missing))


def check_schemas(schema_root):
    try:
        import jsonschema
    except ImportError:
        error("--schemas needs the jsonschema package (pip install jsonschema)")
        return
    for pattern, schema_path in SCHEMAS.items():
        schema = load(os.path.join(schema_root, schema_path))
        validator = jsonschema.validators.validator_for(schema)(schema)
        matched = files(pattern)
        if not matched:
            error(f"no file matches {pattern}")
        for path in matched:
            for problem in validator.iter_errors(load(path)):
                location = "/".join(str(p) for p in problem.absolute_path)
                error(f"{os.path.relpath(path, PACK)}: {location}: {problem.message}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--schemas", help="checkout of Blockception/Minecraft-bedrock-json-schemas")
    args = parser.parse_args()

    check_manifest()
    check_references()
    check_hud()
    if args.schemas:
        check_schemas(args.schemas)

    if errors:
        print("Bedrock pack validation failed:")
        for message in errors:
            print("  - " + message)
        sys.exit(1)
    print("Bedrock pack OK" + (" (schemas checked)" if args.schemas else ""))


if __name__ == "__main__":
    main()
