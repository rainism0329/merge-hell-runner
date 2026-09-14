#!/usr/bin/env python3
"""Export the approved first-wave enemies without changing the existing rig atlases.

Uses the same inspected, topology-aware matte as export_rig_atlases.py. Python/Pillow/numpy/scipy.
Source-space cells overlap intentionally: the generated clamps cross the nominal 2x2 grid,
but each complete body is a separate connected component. The largest component in each
inspected crop selects the intended enemy without cutting a clamp or including a neighbour.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # Import the reusable matte without leaving project-local caches.

import numpy as np
from PIL import Image, ImageDraw

from export_rig_atlases import Part, extract, font, sha256

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "docs/design/evolution/sources"
OUTPUT = ROOT / "src/main/resources/game/art"
QA = ROOT / "build/first-wave"
MAX_EDGE = 768

# Only visually confirmed checkerboard apertures; optic and silver plate highlights stay intact.
PARTS = [
    ("first-wave-generated.png", (0, 0, 680, 585),
     Part("conflict", (345, 561), {"core": (333, 365)}, ((426, 292), (494, 398)))),
    ("first-wave-generated.png", (585, 0, 1261, 585),
     Part("crash", (925, 558), {"core": (1038, 416), "wheel": (1038, 416),
          "wheel-rim": (1128, 416)}, ((959, 229), (1159, 391), (1170, 360)))),
    ("first-wave-generated.png", (0, 585, 650, 1247),
     Part("lock", (338, 1149), {"core": (283, 927), "aperture": (347, 729)}, ((311, 732),))),
    ("first-wave-firewall-generated.png", (0, 0, 1024, 1536),
     Part("firewall", (511, 1440), {"core": (500, 846)})),
]


def prepare(source: Image.Image, part: Part, cell):
    if source.mode != "RGB":
        raise ValueError("This matte is specific to the reviewed flattened RGB sources; inspect new alpha before re-exporting")
    raw, info = extract(np.asarray(source), part, cell)
    # The tall furnace has ample source resolution. Bound shipped frame size while keeping
    # uniform scale, then restore a tight alpha bbox and a genuine transparent 1px guard.
    scale = min(1.0, MAX_EDGE / max(raw.size))
    resized = raw.resize((round(raw.width * scale), round(raw.height * scale)), Image.Resampling.LANCZOS) if scale < 1 else raw
    sx, sy = resized.width / raw.width, resized.height / raw.height
    bbox = resized.getbbox()
    if bbox is None:
        raise ValueError(part.name + ": empty frame")
    cropped = resized.crop(bbox)
    final = Image.new("RGBA", (cropped.width + 2, cropped.height + 2))
    final.paste(cropped, (1, 1))
    transform = lambda p: [round(p[0] * sx - bbox[0] + 1, 6), round(p[1] * sy - bbox[1] + 1, 6)]
    info["source_anchor"] = list(part.anchor)
    info["source_size_before_resampling"] = info["size"]
    info["anchor"] = [final.width / 2.0, final.height - 1]
    info["anchor_meaning"] = "bottom center contact plane; hover body uses the same logical bottom reference"
    info["sockets"] = {name: transform(point) for name, point in info["sockets"].items()}
    info["export_scale"] = [sx, sy]
    info["size"] = list(final.size)
    info["alpha_bbox"] = list(final.getbbox())
    alpha = np.asarray(final)[:, :, 3]
    info["alpha_zero_pixels"] = int(np.count_nonzero(alpha == 0))
    info["alpha_soft_pixels"] = int(np.count_nonzero((alpha > 0) & (alpha < 255)))
    info["alpha_opaque_pixels"] = int(np.count_nonzero(alpha == 255))
    info["corner_alpha"] = [int(alpha[y, x]) for y, x in [(0, 0), (0, -1), (-1, 0), (-1, -1)]]
    for aperture in info["apertures"]:
        aperture["frame"] = transform(aperture["frame"])
        x, y = map(round, aperture["frame"])
        aperture["output_alpha"] = int(alpha[y, x])
        if alpha[y, x] != 0:
            raise ValueError(part.name + ": inspected aperture was closed by resampling")
    if any(info["corner_alpha"]) or not info["alpha_soft_pixels"] or not info["alpha_opaque_pixels"]:
        raise ValueError(part.name + ": invalid cutout coverage")
    return final, info


def proof(parts, infos, background, output):
    sheet = Image.new("RGB", (1000, 1420), background)
    draw = ImageDraw.Draw(sheet)
    ink = (240, 240, 237) if sum(background) < 350 else (20, 25, 30)
    for i, (part, info) in enumerate(zip(parts, infos)):
        x, y = (i % 2) * 500, (i // 2) * 710
        s = min(1, 460 / part.width, 630 / part.height)
        thumb = part.resize((round(part.width * s), round(part.height * s)), Image.Resampling.LANCZOS)
        px, py = x + (500 - thumb.width) // 2, y + 16
        sheet.paste(thumb, (px, py), thumb)
        draw.text((x + 16, y + 662), info["name"], fill=ink, font=font(23))
        draw.text((x + 16, y + 691), f'{part.width}x{part.height}; alpha edges {info["alpha_soft_pixels"]}', fill=ink, font=font(13))
    sheet.save(output)


def main():
    QA.mkdir(parents=True, exist_ok=True)
    sources = {name: Image.open(SOURCES / name) for name, _, _ in PARTS}
    input_hashes = {name: sha256(SOURCES / name) for name in sources}
    parts, infos = [], []
    for source_name, cell, part in PARTS:
        image, info = prepare(sources[source_name], part, cell)
        info["source"] = "docs/design/evolution/sources/" + source_name
        parts.append(image)
        infos.append(info)
        image.save(QA / (part.name + ".png"))
    pad = 4
    tile_w, tile_h = max(p.width for p in parts) + 2 * pad, max(p.height for p in parts) + 2 * pad
    atlas = Image.new("RGBA", (tile_w * 2, tile_h * 2))
    metadata = ["# Generated by scripts/art/export_first_wave.py; authored enemy facing left.",
                "format=1", "atlas=game/art/first-wave.png", "pixelsPerUnit=1", "layer=20",
                "default=conflict", "clips=" + ",".join(info["name"] for info in infos)]
    for i, (part, info) in enumerate(zip(parts, infos)):
        x, y = (i % 2) * tile_w + pad, (i // 2) * tile_h + pad
        atlas.paste(part, (x, y))
        info["atlas_rect"] = [x, y, part.width, part.height]
        name = info["name"]
        metadata += ["", f"clip.{name}.loop=true", f"clip.{name}.frames={name}",
                     f"frame.{name}.rect={x},{y},{part.width},{part.height}", f"frame.{name}.durationMs=1000",
                     f'frame.{name}.anchor={info["anchor"][0]},{info["anchor"][1]}',
                     f'frame.{name}.sockets=' + ",".join(info["sockets"])]
        metadata += [f"frame.{name}.socket.{key}={p[0]},{p[1]}" for key, p in info["sockets"].items()]
    atlas.save(OUTPUT / "first-wave.png")
    (OUTPUT / "first-wave.properties").write_text("\n".join(metadata) + "\n", encoding="utf-8")
    for name, color in [("black", (10, 12, 17)), ("white", (255, 255, 255)), ("color", (25, 131, 162))]:
        proof(parts, infos, color, QA / ("proof-" + name + ".png"))
    for name, digest in input_hashes.items():
        if sha256(SOURCES / name) != digest:
            raise RuntimeError("Source changed during export: " + name)
    provenance = {
        "format": 1, "generation": "built-in image_gen; prompts stored beside unmodified generated sources",
        "source_sha256": input_hashes,
        "sources_have_true_alpha": False,
        "selection": "First three full bodies from 2x2 sheet; separate narrower furnace revision fits 60x120 collider with uniform scale. Original broad furnace retained as an unselected variant.",
        "script_sha256": sha256(Path(__file__)),
        "matte_dependency_sha256": sha256(Path(__file__).with_name("export_rig_atlases.py")),
        "method": "Connected neutral background removal with inspected aperture seeds; narrow edge defringe; optional uniform max-edge 768 resampling; tight alpha crop with transparent guard",
        "output": "src/main/resources/game/art/first-wave.png", "output_mode": atlas.mode, "output_size": list(atlas.size),
        "output_sha256": sha256(OUTPUT / "first-wave.png"), "metadata_sha256": sha256(OUTPUT / "first-wave.properties"),
        "parts": infos,
    }
    (ROOT / "docs/design/evolution/first-wave-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": provenance["output"], "mode": atlas.mode, "size": list(atlas.size), "parts": len(parts)}, indent=2))


if __name__ == "__main__":
    main()
