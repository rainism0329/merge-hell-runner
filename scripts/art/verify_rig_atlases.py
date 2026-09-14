#!/usr/bin/env python3
"""Validate exported rig alpha, tight frames, inspected holes and source fidelity.

Run after export_rig_atlases.py. Requires the same Pillow/numpy/scipy dependencies.
The generated provenance is also checked against actual file hashes.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
# Deliberately bright source pixels which must never become checkerboard holes.
PROTECTED = {
    ("repair", "head"): [(283, 151)],
    ("repair", "scarf"): [(1722, 608)],
    ("hostiles", "bug-body"): [(122, 225), (72, 228), (162, 273)],
    ("hostiles", "legacy-core"): [(1110, 642)],
    ("hostiles", "node"): [(1541, 679)],
}


def checked(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    report = json.loads((ROOT / "docs/design/evolution/rig-atlas-provenance.json").read_text(encoding="utf-8"))
    checked(hashlib.sha256((ROOT / report["script"]).read_bytes()).hexdigest() == report["script_sha256"], "Exporter changed since last export")
    parts = holes = preserved = interior_pixels = 0
    for rig_name, rig in report["rigs"].items():
        source_path, atlas_path = ROOT / rig["source"], ROOT / rig["output"]
        for path, expected in [(source_path, rig["source_sha256"]), (atlas_path, rig["output_sha256"]),
                               (atlas_path.with_suffix(".properties"), rig["metadata_sha256"])]:
            checked(hashlib.sha256(path.read_bytes()).hexdigest() == expected, f"Hash mismatch: {path}")
        source = np.asarray(Image.open(source_path).convert("RGB"))
        image = Image.open(atlas_path)
        checked(image.mode == "RGBA", f"{rig_name}: not RGBA")
        atlas = np.asarray(image)
        checked(all(atlas[y, x, 3] == 0 for y, x in [(0, 0), (0, -1), (-1, 0), (-1, -1)]), f"{rig_name}: opaque atlas corner")
        for info in rig["parts"]:
            name = rig_name + "/" + info["name"]
            x, y, w, h = info["atlas_rect"]
            checked(x >= 0 and y >= 0 and x + w <= image.width and y + h <= image.height, name + ": out of bounds")
            frame = atlas[y:y+h, x:x+w]
            alpha = frame[:, :, 3]
            ys, xs = np.nonzero(alpha)
            checked((int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) == (1, 1, w-2, h-2), name + ": crop not tight with 1px guard")
            checked(np.any((alpha > 0) & (alpha < 255)), name + ": missing antialias coverage")
            sx, sy, ex, ey = info["source_crop"]
            original = source[sy:ey, sx:ex]
            interior = ndi.binary_erosion(alpha == 255, iterations=3)
            checked(np.array_equal(frame[:, :, :3][interior], original[interior]), name + ": interior source RGB changed")
            interior_pixels += int(interior.sum())
            for aperture in info["apertures"]:
                px, py = aperture["frame"]
                checked(alpha[py, px] == 0, name + ": inspected aperture is closed")
                holes += 1
            for px, py in PROTECTED.get((rig_name, info["name"]), []):
                result = frame[py-sy, px-sx]
                checked(result[3] == 255 and np.array_equal(result[:3], source[py, px]), name + ": protected highlight changed")
                preserved += 1
            parts += 1
    print(json.dumps({"passed": True, "rgba_atlases": len(report["rigs"]), "tight_frames": parts,
                      "transparent_aperture_seeds": holes, "protected_highlights": preserved,
                      "unchanged_interior_pixels": interior_pixels}, indent=2))


if __name__ == "__main__":
    main()
