#!/usr/bin/env python3
"""Reproducible, topology-aware extraction of the approved generated mechanical rig sheets.

The input checkerboards are baked RGB, not transparency. Neutral/bright pixels are only
background when reachable from a cell border or an explicitly inspected aperture seed.
Enclosed ivory armor and metal highlights are therefore retained. A narrow silhouette matte
gets subpixel coverage and nearest interior RGB to remove baked checkerboard edge spill.
Original sources are never written. See the generated provenance and black/white/color proofs.

Requires Python 3.10+, Pillow, numpy and scipy.
Run from any directory: python scripts/art/export_rig_atlases.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage as ndi


ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "docs/design/evolution/sources"
EXPECTED_SIZE = (1774, 887)


@dataclass(frozen=True)
class Part:
    name: str
    anchor: tuple[int, int]
    sockets: dict[str, tuple[int, int]] = field(default_factory=dict)
    holes: tuple[tuple[int, int], ...] = ()


# Coordinates refer to the unmodified 1774x887 source sheet. Joint endpoints are visible metal
# bearing centers, not arbitrary crop corners. Direction is preserved; no limb is straightened.
RIGS = {
    "repair": {
        "source": "repair-rig-generated.png",
        "alternatives": ["repair-alpha-retry.png"],
        "direction": "right; limbs hang downward; scarf collar right, tails left; cannon muzzle right",
        "parts": [
            Part("head", (237, 239), {"neck": (274, 343), "visor": (306, 233),
                 "visor-top-left": (244, 159), "visor-bottom-right": (372, 303)}, ((290, 138),)),
            Part("torso", (664, 238), {"neck": (690, 111), "shoulder": (581, 184), "hip": (632, 347)}),
            Part("upper-arm", (1085, 120), {"distal": (1093, 347)}),
            Part("forearm", (1537, 126), {"distal": (1586, 365)}),
            Part("thigh", (174, 533), {"distal": (203, 779)}),
            Part("shin", (605, 515), {"distal": (678, 823), "ankle": (604, 735),
                 "heel": (570, 829), "toe": (770, 833)}),
            Part("cannon", (1280, 632), {"muzzle": (1280, 632), "grip": (975, 741)}, ((1028, 690), (1038, 592), (1011, 696))),
            Part("scarf", (1678, 601), {"tail": (1400, 713)}, ((1413, 555), (1391, 590),
                 (1388, 682), (1405, 691), (1383, 697), (1429, 726), (1407, 728), (1400, 734), (1419, 741))),
        ],
    },
    "hostiles": {
        "source": "hostiles-rig-generated.png",
        "alternatives": ["hostiles-alpha-retry.png"],
        "direction": "left; bug-upper hip right and elbow left; other limbs proximal top; debt boot toe left",
        "parts": [
            Part("bug-body", (234, 228), holes=((274, 311),)),
            Part("bug-upper", (827, 163), {"distal": (562, 251)}, ((757, 208), (751, 140))),
            Part("bug-lower", (1138, 64), {"distal": (1133, 415)}, ((1127, 177), (1135, 113))),
            Part("debt-body", (1552, 226), {"hip": (1538, 414), "shoulder-left": (1390, 258), "shoulder-right": (1688, 215)}, ((1641, 369),)),
            Part("debt-arm", (327, 500), {"distal": (162, 799)}, ((330, 557), (342, 539))),
            Part("debt-leg", (763, 494), {"distal": (665, 849)}, ((726, 569), (736, 546))),
            Part("legacy-core", (1110, 647), {"reactor": (1110, 647)}, ((950, 604), (1268, 592),
                 (950, 783), (1261, 785), (986, 780), (1229, 781), (1008, 505), (1183, 483),
                 (1263, 648), (957, 653))),
            Part("node", (1548, 684), {"socket": (1548, 684)}, ((1571, 547),)),
        ],
    },
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def to_json_point(point: tuple[int, int], offset: tuple[int, int]) -> list[int]:
    return [point[0] - offset[0], point[1] - offset[1]]


def extract(rgb: np.ndarray, part: Part, cell: tuple[int, int, int, int]):
    x0, y0, x1, y1 = cell
    pixels = rgb[y0:y1, x0:x1]
    channel_max = pixels.max(axis=2).astype(np.int16)
    channel_min = pixels.min(axis=2).astype(np.int16)
    # This is a background candidate mask, not the alpha mask. Connectivity protects enclosed
    # bright/neutral armor pixels. All original sheets have dark outlines around light metal.
    candidates = (channel_max - channel_min <= 26) & (channel_min >= 138)
    labels, count = ndi.label(candidates, structure=np.ones((3, 3), dtype=bool))
    border_labels = np.unique(np.concatenate((labels[0], labels[-1], labels[:, 0], labels[:, -1])))
    remove_labels = set(int(label) for label in border_labels if label)
    interior_aperture_labels = set()
    aperture_info = []
    for source_point in part.holes:
        hx, hy = source_point[0] - x0, source_point[1] - y0
        label = int(labels[hy, hx])
        if not label:
            raise ValueError(f"{part.name}: aperture seed {source_point} does not match checkerboard; inspect the source")
        remove_labels.add(label)
        if label not in border_labels:
            interior_aperture_labels.add(label)
        aperture_info.append({"source": list(source_point), "candidate_region_pixels": int(np.count_nonzero(labels == label))})
    background = np.isin(labels, list(remove_labels))
    raw_foreground = ~background
    components, component_count = ndi.label(raw_foreground, structure=np.ones((3, 3), dtype=bool))
    sizes = np.bincount(components.ravel())
    sizes[0] = 0
    largest = int(sizes.argmax())
    if sizes[largest] < 500:
        raise ValueError(f"{part.name}: no substantial connected component")
    foreground = components == largest
    # The source contains no detached prop fragments. Removing disconnected residual components
    # gets rid of neutral baked-backdrop wrinkles without changing the connected asset silhouette.
    core = ndi.binary_erosion(foreground, iterations=2, border_value=0)
    if not core.any():
        raise ValueError(f"{part.name}: no safe interior for edge decontamination")
    _, nearest = ndi.distance_transform_edt(~core, return_indices=True)
    nearest_rgb = pixels[nearest[0], nearest[1]]
    alpha = np.asarray(Image.fromarray((foreground * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.45))).copy()
    alpha[alpha <= 3] = 0
    alpha[alpha >= 252] = 255
    # Known hollow centers stay genuinely open even when only one original pixel wide.
    # Outer silhouette antialiasing remains soft; this is restricted to inspected internal holes.
    alpha[np.isin(labels, list(interior_aperture_labels))] = 0
    result_rgb = pixels.copy()
    # Only the two-pixel silhouette band is defringed. Every enclosed armor/highlight pixel is
    # left at its original RGB; this does not bleach/desaturate the entire object.
    result_rgb[~core] = nearest_rgb[~core]
    result_rgb[alpha == 0] = 0
    rgba = np.dstack((result_rgb, alpha))
    ys, xs = np.nonzero(alpha)
    # One fully transparent guard pixel is part of each tightly bounded frame.
    left, top = max(0, int(xs.min()) - 1), max(0, int(ys.min()) - 1)
    right, bottom = min(x1 - x0, int(xs.max()) + 2), min(y1 - y0, int(ys.max()) + 2)
    image = Image.fromarray(rgba[top:bottom, left:right], "RGBA")
    offset = (x0 + left, y0 + top)
    anchor = to_json_point(part.anchor, offset)
    sockets = {name: to_json_point(point, offset) for name, point in part.sockets.items()}
    for name, point in [("anchor", anchor), *sockets.items()]:
        if not (0 <= point[0] < image.width and 0 <= point[1] < image.height):
            raise ValueError(f"{part.name}: {name} is outside the extracted frame: {point}")
    out_alpha = np.asarray(image)[:, :, 3]
    for aperture in aperture_info:
        ax, ay = to_json_point(tuple(aperture["source"]), offset)
        aperture["frame"] = [ax, ay]
        aperture["output_alpha"] = int(out_alpha[ay, ax])
        if aperture["output_alpha"] != 0:
            raise ValueError(f"{part.name}: the checked aperture is not fully transparent")
    info = {
        "name": part.name,
        "source_cell": list(cell),
        "source_crop": [*offset, x0 + right, y0 + bottom],
        "size": [image.width, image.height],
        "anchor": anchor,
        "sockets": sockets,
        "alpha_bbox": list(image.getbbox()),
        "alpha_zero_pixels": int(np.count_nonzero(out_alpha == 0)),
        "alpha_soft_pixels": int(np.count_nonzero((out_alpha > 0) & (out_alpha < 255))),
        "alpha_opaque_pixels": int(np.count_nonzero(out_alpha == 255)),
        "corner_alpha": [int(out_alpha[y, x]) for y, x in [(0, 0), (0, -1), (-1, 0), (-1, -1)]],
        "discarded_disconnected_pixels": int(raw_foreground.sum() - foreground.sum()),
        "apertures": aperture_info,
        "interior_rgb_unchanged": bool(np.array_equal(result_rgb[core], pixels[core])),
    }
    if any(info["corner_alpha"]):
        raise ValueError(f"{part.name}: an image corner is not transparent")
    return image, info


def font(size: int):
    for name in ["C:/Windows/Fonts/arial.ttf", "DejaVuSans.ttf"]:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def proof_sheet(parts, infos, background, output: Path, pivots=False):
    tile_w, tile_h = 440, 485
    sheet = Image.new("RGB", (tile_w * 4, tile_h * 2), background)
    draw = ImageDraw.Draw(sheet)
    light = sum(background) < 350
    ink = (238, 240, 237) if light else (22, 28, 31)
    for index, (part, info) in enumerate(zip(parts, infos)):
        x, y = index % 4 * tile_w, index // 4 * tile_h
        scale = min(1, (tile_w - 32) / part.width, (tile_h - 68) / part.height)
        thumb = part.resize((round(part.width * scale), round(part.height * scale)), Image.Resampling.LANCZOS)
        px, py = x + (tile_w - thumb.width) // 2, y + 18
        sheet.paste(thumb, (px, py), thumb)
        draw.text((x + 14, y + tile_h - 41), info["name"], fill=ink, font=font(20))
        draw.text((x + 14, y + tile_h - 18), f'{part.width}x{part.height} / soft alpha {info["alpha_soft_pixels"]}', fill=ink, font=font(12))
        if pivots:
            for name, point in [("anchor", info["anchor"]), *info["sockets"].items()]:
                qx, qy = px + round(point[0] * scale), py + round(point[1] * scale)
                color = (80, 255, 200) if name == "anchor" else (255, 75, 195)
                draw.ellipse((qx - 5, qy - 5, qx + 5, qy + 5), outline=color, width=2)
                draw.text((qx + 7, qy - 8), name, fill=color, font=font(13))
    sheet.save(output)


def export_rig(rig_name, rig, output_dir, qa_dir):
    source = SOURCE_DIR / rig["source"]
    source_hash = sha256(source)
    original = Image.open(source)
    if original.size != EXPECTED_SIZE:
        raise ValueError(f"Expected approved source size {EXPECTED_SIZE}, got {original.size}")
    rgb = np.asarray(original.convert("RGB"))
    parts, infos = [], []
    for index, part in enumerate(rig["parts"]):
        col, row = index % 4, index // 4
        cell = (col * original.width // 4, row * original.height // 2,
                (col + 1) * original.width // 4, (row + 1) * original.height // 2)
        image, info = extract(rgb, part, cell)
        parts.append(image)
        infos.append(info)
        image.save(qa_dir / f"{rig_name}-{part.name}.png")
    pad = 4
    cell_width = max(image.width for image in parts) + pad * 2
    cell_height = max(image.height for image in parts) + pad * 2
    atlas = Image.new("RGBA", (cell_width * 4, cell_height * 2), (0, 0, 0, 0))
    properties = ["# Generated by scripts/art/export_rig_atlases.py; preserve source direction.",
                  "format=1", f"atlas=game/art/{rig_name}.png", "pixelsPerUnit=1", "layer=20",
                  "default=" + infos[0]["name"], "clips=" + ",".join(info["name"] for info in infos)]
    for index, (part, info) in enumerate(zip(parts, infos)):
        x, y = index % 4 * cell_width + pad, index // 4 * cell_height + pad
        atlas.paste(part, (x, y))
        info["atlas_rect"] = [x, y, part.width, part.height]
        name = info["name"]
        properties += ["", f"clip.{name}.loop=true", f"clip.{name}.frames={name}",
                       f"frame.{name}.rect={x},{y},{part.width},{part.height}",
                       f"frame.{name}.durationMs=1000", f'frame.{name}.anchor={info["anchor"][0]},{info["anchor"][1]}']
        if info["sockets"]:
            properties.append(f'frame.{name}.sockets=' + ",".join(info["sockets"]))
            properties.extend(f"frame.{name}.socket.{socket}={point[0]},{point[1]}" for socket, point in info["sockets"].items())
    atlas_path = output_dir / f"{rig_name}.png"
    metadata_path = output_dir / f"{rig_name}.properties"
    atlas.save(atlas_path)
    metadata_path.write_text("\n".join(properties) + "\n", encoding="utf-8")
    for name, bg in [("black", (10, 12, 17)), ("white", (255, 255, 255)), ("color", (25, 131, 162))]:
        proof_sheet(parts, infos, bg, qa_dir / f"{rig_name}-proof-{name}.png")
    proof_sheet(parts, infos, (13, 20, 26), qa_dir / f"{rig_name}-pivots.png", True)
    if sha256(source) != source_hash:
        raise RuntimeError("Source was unexpectedly modified")
    return {
        "source": source.relative_to(ROOT).as_posix(), "source_sha256": source_hash,
        "source_mode": original.mode, "source_size": list(original.size),
        "source_is_true_alpha": original.mode == "RGBA" and np.asarray(original)[:, :, 3].min() < 255,
        "selection_reason": "Original rig retains finer armor wear and metal detail than the smoother alpha-retry image; both still contain a baked RGB checkerboard.",
        "alternatives_reviewed": [{"path": (SOURCE_DIR / path).relative_to(ROOT).as_posix(), "sha256": sha256(SOURCE_DIR / path)} for path in rig["alternatives"]],
        "direction": rig["direction"], "output": atlas_path.relative_to(ROOT).as_posix(),
        "output_sha256": sha256(atlas_path), "output_mode": atlas.mode, "output_size": list(atlas.size),
        "metadata_sha256": sha256(metadata_path), "parts": infos,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "src/main/resources/game/art")
    parser.add_argument("--qa-dir", type=Path, default=ROOT / "build/art-export")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.qa_dir.mkdir(parents=True, exist_ok=True)
    results = {name: export_rig(name, rig, args.output_dir.resolve(), args.qa_dir.resolve()) for name, rig in RIGS.items()}
    report = {
        "format": 1, "script": "scripts/art/export_rig_atlases.py", "script_sha256": sha256(Path(__file__)),
        "software": {"python": __import__("platform").python_version(), "pillow": Image.__version__, "numpy": np.__version__, "scipy": __import__("scipy").__version__},
        "method": {"background_candidate": "max(RGB)-min(RGB)<=26 and min(RGB)>=138, border/aperture connected only", "matte": "0.45px Gaussian silhouette coverage; <=3->0, >=252->255", "edge_rgb": "nearest two-pixel-eroded object interior only in silhouette band", "crop": "tight nonzero alpha bbox plus one transparent guard pixel", "resampling": "none for production; QA thumbnails only"},
        "rigs": results,
    }
    provenance = ROOT / "docs/design/evolution/rig-atlas-provenance.json"
    provenance.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: {"size": value["output_size"], "parts": len(value["parts"]), "alpha": value["output_mode"]} for key, value in results.items()}, indent=2))


if __name__ == "__main__":
    main()
