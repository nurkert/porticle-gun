#!/usr/bin/env python3
"""Renders the animated README images (turntables, tank, inventory icons) into images/animations/.

    python3 scripts/render_gifs.py

Needs Pillow. Everything is rendered from the models in generate_resourcepack.py. The GIFs have a transparent
background (GIF only knows fully transparent or opaque, so soft edges are cut at half coverage).
"""

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import generate_resourcepack as pack  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "images", "animations")
TRANSPARENT = 255  # palette index kept free for the background


def frame(pixels):
    size = (len(pixels[0]), len(pixels))
    flat = [p for row in pixels for p in row]
    colours = Image.new("RGB", size)
    colours.putdata([p[:3] for p in flat])
    image = colours.convert("P", palette=Image.ADAPTIVE, colors=255)
    mask = Image.new("L", size)
    mask.putdata([255 if p[3] < 128 else 0 for p in flat])
    image.paste(TRANSPARENT, mask=mask)
    return image


def save(name, frames, duration):
    # disposal 2 clears each frame, otherwise the transparent parts would show the frames before
    frames[0].save(os.path.join(OUT, name), save_all=True, append_images=frames[1:], duration=duration, loop=0,
                   transparency=TRANSPARENT, disposal=2)


def turntable(name, model, size=360, count=36, pitch=20):
    yaws = [-35 + 360 * i / count for i in range(count)]
    fit = pack.showcase_fit([(yaw, pitch) for yaw in yaws], model)  # one scale for the whole turn
    save(name, [frame(pack.render_showcase(yaw, pitch, size, supersample=2, model=model, fit=fit)) for yaw in yaws],
         70)


def main():
    os.makedirs(OUT, exist_ok=True)
    turntable("portal_gun.gif", pack.PORTAL_GUN)
    turntable("interdimensional_gun.gif", pack.RIFT_GUN)
    turntable("rift_shard.gif", pack.RIFT_SHARD, size=280)
    turntable("portal_fluid.gif", pack.PORTAL_FLUID, size=280)
    turntable("rift_compass.gif", pack.RIFT_COMPASS, size=280, pitch=35)

    levels = [0, 1, 2, 3, 4, 4, 3, 2, 1, 0]
    tanks = [{**pack.RIFT_GUN, "elements": elements} for elements in pack.RIFT_GUN["variants"]]
    fit = pack.showcase_fit([(-35, 22)], tanks[-1])
    save("interdimensional_gun_tank.gif",
         [frame(pack.render_showcase(-35, 22, 360, supersample=2, model=tanks[level], fit=fit)) for level in levels],
         350)

    for item in ("rift_shard", "portal_fluid", "rift_compass"):
        save(f"{item}_icon.gif", [frame(pack.enlarge(icon, 12)) for icon in pack.flat_item_frames(item)], 150)


if __name__ == "__main__":
    main()
