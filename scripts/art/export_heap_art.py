#!/usr/bin/env python3
"""Export the inspected Heap panorama and machines, preserving generated source files.

The panorama remains a separate opaque RGB atlas. The generated machines already have true
alpha, so their RGB and alpha are retained without a background matte or defringing. Requires
Python 3.10+, Pillow, numpy and scipy. No imaging dependency is used by the Java runtime.
"""
from __future__ import annotations

import argparse
import json
import platform
import sys
from dataclasses import dataclass
from pathlib import Path

sys.dont_write_bytecode = True

import numpy as np
import scipy
from PIL import Image, ImageDraw

from export_rig_atlases import Part, font, sha256

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "docs/design/evolution/sources"
MAX_EDGE = 768


@dataclass(frozen=True)
class InspectedPart:
    part: Part
    cell: tuple[int, int, int, int]
    anchor_meaning: str
    bottom_anchor: bool = False
    center_anchor: bool = False
    protected: tuple[tuple[str, tuple[int, int]], ...] = ()


# Changing a source requires re-inspecting these cells, joints, apertures and metal samples.
ACTOR_SIZE = (1448, 1086)
PARTS = (
    InspectedPart(Part("boss-shell", (520, 1050),
                       {"core": (520, 508), "core-left": (386, 508), "core-right": (647, 508),
                        "fan": (522, 88)},
                       ((387, 66), (652, 65), (130, 280), (916, 280), (431, 942), (612, 942))),
                  (0, 0, 1040, 1086), "bottom center; core and fan sockets retain source geometry",
                  bottom_anchor=True,
                  protected=(("piston-silver", (283, 313)), ("orange-armor-light", (412, 221)),
                             ("exhaust-silver", (498, 965)), ("dark-core-material", (520, 508)))),
    InspectedPart(Part("leak-body", (1245, 495),
                       {"core": (1245, 270), "eye": (1106, 232), "flow": (1320, 270)},
                       ((1210, 115), (1360, 381))),
                  (1040, 60, 1448, 510), "bottom center of floating body; no floor-contact shadow",
                  bottom_anchor=True,
                  protected=(("red-eye", (1106, 232)), ("reservoir-glass", (1320, 270)),
                             ("top-brass", (1245, 89)))),
    InspectedPart(Part("shutter", (1250, 785)), (1160, 530, 1345, 1040),
                  "center; renderer mirrors this plate for a matching pair", center_anchor=True,
                  protected=(("red-strip", (1198, 767)), ("edge-metal", (1181, 665)))),
)


def coverage(image: Image.Image) -> dict:
    alpha = np.asarray(image)[:, :, 3]
    result = {
        "size": list(image.size), "alpha_bbox": list(image.getbbox()),
        "alpha_zero_pixels": int(np.count_nonzero(alpha == 0)),
        "alpha_soft_pixels": int(np.count_nonzero((alpha > 0) & (alpha < 255))),
        "alpha_opaque_pixels": int(np.count_nonzero(alpha == 255)),
        "alpha_high_coverage_pixels": int(np.count_nonzero(alpha >= 248)),
        "corner_alpha": [int(alpha[y, x]) for y, x in [(0, 0), (0, -1), (-1, 0), (-1, -1)]],
    }
    if (image.getbbox() != (1, 1, image.width - 1, image.height - 1)
            or any(result["corner_alpha"]) or not result["alpha_soft_pixels"]
            or not result["alpha_high_coverage_pixels"]):
        raise ValueError("Cutout must have a tight alpha bbox, a 1px transparent guard and soft/high coverage")
    return result


def prepare(source: Image.Image, item: InspectedPart):
    if source.mode != "RGBA" or np.asarray(source)[:, :, 3].min() != 0:
        raise ValueError("The inspected Heap source must retain its genuine RGBA transparency")
    cell = source.crop(item.cell)
    bbox = cell.getbbox()
    if bbox is None:
        raise ValueError(item.part.name + ": empty source cell")
    raw = cell.crop(bbox)
    offset = (item.cell[0] + bbox[0], item.cell[1] + bbox[1])

    def local(point):
        return [point[0] - offset[0], point[1] - offset[1]]

    info = {"name": item.part.name, "source_cell": list(item.cell),
            "source_crop": [*offset, offset[0] + raw.width, offset[1] + raw.height],
            "size": list(raw.size), "anchor": local(item.part.anchor),
            "sockets": {name: local(point) for name, point in item.part.sockets.items()},
            "rgba_unchanged_before_resampling": np.array_equal(np.asarray(raw), np.asarray(source)[
                offset[1]:offset[1] + raw.height, offset[0]:offset[0] + raw.width]),
            "apertures": []}
    for point in item.part.holes:
        original_alpha = source.getpixel(point)[3]
        if original_alpha > 8:
            raise ValueError(f"{item.part.name}: inspected transparent aperture {point} is not open")
        info["apertures"].append({"source": list(point), "frame": local(point), "source_alpha": original_alpha})
    protected = []
    for name, point in item.protected:
        local = (point[0] - info["source_crop"][0], point[1] - info["source_crop"][1])
        before, after = source.getpixel(point), raw.getpixel(local)
        if after[3] < 248 or tuple(before) != tuple(after):
            raise ValueError(f"{item.part.name}: protected {name} was removed or changed")
        protected.append({"name": name, "source": list(point), "source_rgba": list(before),
                          "pre_resample_rgba": list(after), "frame_before_resampling": list(local)})
    scale = min(1.0, MAX_EDGE / max(raw.size))
    resized = (raw.resize((round(raw.width * scale), round(raw.height * scale)), Image.Resampling.LANCZOS)
               if scale < 1 else raw)
    sx, sy = resized.width / raw.width, resized.height / raw.height
    bbox = resized.getbbox()
    if bbox is None:
        raise ValueError(item.part.name + ": empty cutout")
    cropped = resized.crop(bbox)
    final = Image.new("RGBA", (cropped.width + 2, cropped.height + 2))
    final.paste(cropped, (1, 1))

    def transform(point):
        return [round(point[0] * sx - bbox[0] + 1, 6), round(point[1] * sy - bbox[1] + 1, 6)]

    info["source_anchor"] = list(item.part.anchor)
    info["size_before_resampling"] = info["size"]
    info["anchor"] = ([final.width / 2.0, final.height - 1] if item.bottom_anchor
                      else [final.width / 2.0, final.height / 2.0] if item.center_anchor
                      else transform(info["anchor"]))
    info["anchor_meaning"] = item.anchor_meaning
    info["sockets"] = {name: transform(point) for name, point in info["sockets"].items()}
    info["export_scale"] = [sx, sy]
    info.update(coverage(final))
    for aperture in info["apertures"]:
        aperture["frame"] = transform(aperture["frame"])
        aperture["output_alpha"] = final.getpixel(tuple(map(round, aperture["frame"])))[3]
        if aperture["output_alpha"] > 8:
            raise ValueError(item.part.name + ": inspected aperture was closed by resampling")
    for sample in protected:
        sample["frame"] = transform(sample.pop("frame_before_resampling"))
        sample["output_rgba"] = list(final.getpixel(tuple(map(round, sample["frame"]))))
        if sample["output_rgba"][3] < 248:
            raise ValueError(item.part.name + ": protected highlight lost coverage during resampling")
    info["protected_samples"] = protected
    return final, info


def metadata(atlas_name, infos, layer):
    lines = ["# Generated by scripts/art/export_heap_art.py; coordinates are frame-local pixels.",
             "format=1", f"atlas=game/art/{atlas_name}.png", "pixelsPerUnit=1", f"layer={layer}",
             "default=" + infos[0]["name"], "clips=" + ",".join(info["name"] for info in infos)]
    for info in infos:
        name = info["name"]
        lines += ["", f"clip.{name}.loop=true", f"clip.{name}.frames={name}",
                  f"frame.{name}.rect=" + ",".join(map(str, info["atlas_rect"])),
                  f"frame.{name}.durationMs=1000",
                  f"frame.{name}.anchor=" + ",".join(map(str, info["anchor"]))]
        if info.get("sockets"):
            lines.append(f"frame.{name}.sockets=" + ",".join(info["sockets"]))
            for socket, point in info["sockets"].items():
                lines.append(f"frame.{name}.socket.{socket}=" + ",".join(map(str, point)))
    return "\n".join(lines) + "\n"


def proof(parts, infos, color, output, pivots=False):
    tile_w, tile_h = 620, 860
    sheet = Image.new("RGB", (tile_w * len(parts), tile_h), color)
    draw = ImageDraw.Draw(sheet)
    ink = (238, 240, 237) if sum(color) < 350 else (22, 28, 31)
    for index, (part, info) in enumerate(zip(parts, infos)):
        scale = min(1, (tile_w - 40) / part.width, (tile_h - 92) / part.height)
        thumb = part.resize((round(part.width * scale), round(part.height * scale)), Image.Resampling.LANCZOS)
        x, y = index * tile_w + (tile_w - thumb.width) // 2, 20
        sheet.paste(thumb, (x, y), thumb)
        draw.text((index * tile_w + 18, tile_h - 65), info["name"], fill=ink, font=font(22))
        draw.text((index * tile_w + 18, tile_h - 33),
                  f'{part.width}x{part.height}; soft alpha {info["alpha_soft_pixels"]}', fill=ink, font=font(15))
        if pivots:
            for name, point in [("anchor", info["anchor"]), *info["sockets"].items()]:
                px, py = x + round(point[0] * scale), y + round(point[1] * scale)
                marker = (80, 255, 200) if name == "anchor" else (255, 75, 195)
                draw.ellipse((px - 5, py - 5, px + 5, py + 5), outline=marker, width=2)
                draw.text((px + 7, py - 8), name, fill=marker, font=font(13))
    sheet.save(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "src/main/resources/game/art")
    parser.add_argument("--qa-dir", type=Path, default=ROOT / "build/heap-art-export")
    parser.add_argument("--provenance", type=Path, default=ROOT / "docs/design/evolution/heap-art-provenance.json")
    args = parser.parse_args()
    if not PARTS or ACTOR_SIZE is None:
        raise ValueError("Generated source has not yet been visually inspected; no crop or aperture is assumed")
    for path in (args.output_dir, args.qa_dir, args.provenance.parent):
        path.mkdir(parents=True, exist_ok=True)
    background_path, actor_path = (SOURCES / "heap-background-generated.png", SOURCES / "heap-actors-generated.png")
    inputs = {path.name: sha256(path) for path in (background_path, actor_path)}
    background, actor_source = Image.open(background_path), Image.open(actor_path)
    if background.mode != "RGB":
        raise ValueError("Panorama must be the inspected opaque RGB source")
    if actor_source.size != ACTOR_SIZE:
        raise ValueError(f"Expected inspected actor sheet size {ACTOR_SIZE}, got {actor_source.size}")
    background.save(args.output_dir / "heap-background.png")
    background_info = {"name": "backdrop", "atlas_rect": [0, 0, *background.size], "anchor": [0, 0]}
    (args.output_dir / "heap-background.properties").write_text(metadata("heap-background", [background_info], 0), encoding="utf-8")
    parts, infos = [], []
    for item in PARTS:
        image, info = prepare(actor_source, item)
        info["source"] = actor_path.relative_to(ROOT).as_posix()
        parts.append(image)
        infos.append(info)
        image.save(args.qa_dir / (info["name"] + ".png"))
    pad = 4
    atlas = Image.new("RGBA", (sum(part.width for part in parts) + pad * (len(parts) + 1),
                               max(part.height for part in parts) + 2 * pad))
    x = pad
    for part, info in zip(parts, infos):
        atlas.paste(part, (x, pad))
        info["atlas_rect"] = [x, pad, *part.size]
        x += part.width + pad
    atlas.save(args.output_dir / "heap-actors.png")
    (args.output_dir / "heap-actors.properties").write_text(metadata("heap-actors", infos, 20), encoding="utf-8")
    for name, color in [("black", (10, 12, 17)), ("white", (255, 255, 255)), ("color", (25, 131, 162))]:
        proof(parts, infos, color, args.qa_dir / f"proof-{name}.png")
    proof(parts, infos, (13, 20, 26), args.qa_dir / "pivots.png", True)
    background.copy().resize((960, round(background.height * 960 / background.width)), Image.Resampling.LANCZOS).save(args.qa_dir / "backdrop-preview.png")
    for path in (background_path, actor_path):
        if sha256(path) != inputs[path.name]:
            raise RuntimeError("Generated source changed during export: " + path.name)
    outputs = {}
    for name, image in [("heap-background", background), ("heap-actors", atlas)]:
        outputs[name] = {"mode": image.mode, "size": list(image.size),
                         "png_sha256": sha256(args.output_dir / (name + ".png")),
                         "properties_sha256": sha256(args.output_dir / (name + ".properties"))}
    report = {
        "format": 1, "generation": "built-in image_gen; prompts beside unchanged source PNGs",
        "source_sha256": inputs, "actor_source_mode": actor_source.mode, "actor_source_size": list(actor_source.size),
        "script": "scripts/art/export_heap_art.py", "script_sha256": sha256(Path(__file__)),
        "utility_dependency_sha256": sha256(Path(__file__).with_name("export_rig_atlases.py")),
        "software": {"python": platform.python_version(), "pillow": Image.__version__, "numpy": np.__version__, "scipy": scipy.__version__},
        "method": "Opaque full-width RGB background; direct cropping of genuine RGBA machines; no matte, alpha normalization, defringing or disconnected-component filtering; inspected existing apertures verified, dark core retained as material; uniform maximum edge 768; tight alpha crop plus 1px guard",
        "outputs": outputs, "parts": infos,
    }
    args.provenance.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"outputs": outputs, "parts": len(parts)}, indent=2))


if __name__ == "__main__":
    main()
