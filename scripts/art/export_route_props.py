"""Pack native-alpha illustrated route materials; preserve source pixels and alpha."""
from pathlib import Path
from PIL import Image
import hashlib
import json
import numpy as np
from scipy import ndimage

root = Path(__file__).resolve().parents[2]
source = root / 'docs/design/evolution/sources/routes/route-props-source.png'
target = root / 'src/main/resources/game/art'
im = Image.open(source).convert('RGBA')
assert im.size == (1536, 1024)
assert im.getchannel('A').getextrema()[0] == 0
names = ['city-wall', 'drain-wall', 'gantry-wall', 'foundry-wall',
         'hive-wall', 'walkway', 'console', 'nerve-console']
boxes = [(0, 0, 384, 512), (384, 0, 768, 512), (768, 0, 1152, 512), (1152, 0, 1536, 512),
         (0, 512, 384, 1024), (384, 512, 890, 1024), (890, 512, 1185, 1024), (1185, 512, 1536, 1024)]
images = []
for box in boxes:
    tile = im.crop(box)
    labels, _ = ndimage.label(np.asarray(tile.getchannel('A')) > 64)
    sizes = np.bincount(labels.ravel())
    sizes[0] = 0
    ys, xs = ndimage.find_objects(labels)[int(sizes.argmax()) - 1]
    images.append(tile.crop((max(0, xs.start - 2), max(0, ys.start - 2),
                             min(tile.width, xs.stop + 2), min(tile.height, ys.stop + 2))))
cw, ch = max(p.width for p in images) + 8, max(p.height for p in images) + 8
atlas = Image.new('RGBA', (4 * cw, 2 * ch))
lines = ['# Generated native-alpha art; export_route_props.py', 'format=1',
         'atlas=game/art/route-props.png', 'pixelsPerUnit=1', 'layer=10',
         'default=city-wall', 'clips=' + ','.join(names), '']
for index, (name, tile) in enumerate(zip(names, images)):
    x, y = (index % 4) * cw + 4, (index // 4) * ch + 4
    atlas.paste(tile, (x, y))
    lines += [f'clip.{name}.loop=true', f'clip.{name}.frames={name}',
              f'frame.{name}.rect={x},{y},{tile.width},{tile.height}',
              f'frame.{name}.durationMs=1000', f'frame.{name}.anchor=0,0', '']
atlas.save(target / 'route-props.png')
(target / 'route-props.properties').write_text('\n'.join(lines), encoding='utf-8')
manifest = {'source': str(source.relative_to(root)), 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'mode': 'built-in imagegen; native RGBA; deterministic crop and atlas packing',
            'parts': {name: list(tile.size) for name, tile in zip(names, images)}}
(source.parent / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(atlas.size, manifest)
