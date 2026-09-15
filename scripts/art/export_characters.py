"""Pack approved generated native-alpha rig parts and assign articulation metadata."""
from pathlib import Path
from PIL import Image
import hashlib, json
import numpy as np
from scipy import ndimage
root=Path(__file__).resolve().parents[2]
source=root/'docs/design/evolution/sources/characters'
target=root/'src/main/resources/game/art'
parts=['head','torso','upper-arm','forearm','thigh','shin','cannon','scarf']
anchors={'head':(.5,.5),'torso':(.5,.5),'upper-arm':(.5,.14),'forearm':(.47,.12),
         'thigh':(.47,.12),'shin':(.28,.10),'cannon':(.97,.37),'scarf':(.87,.23)}
sockets={'head':{'neck':(.55,.92)},'torso':{'neck':(.55,.08),'shoulder':(.2,.30),'hip':(.5,.9)},
         'upper-arm':{'distal':(.50,.87)},'forearm':{'distal':(.5,.90)},'thigh':{'distal':(.5,.89)},
         'shin':{'ankle':(.30,.72),'distal':(.55,.98),'heel':(.08,.99),'toe':(.94,.99)},
         'cannon':{'muzzle':(.97,.37),'grip':(.30,.86)},'scarf':{'tail':(.08,.65)}}
# Measured visible bearing/hand centers in the trimmed production frames.
calibration={
 'scout':{'head':{'neck':(123,292)},'torso':{'neck':(143,64),'shoulder':(45,111),'hip':(123,349)},
          'upper-arm':{'anchor':(30,70),'distal':(64,350)},'forearm':{'anchor':(35,56),'distal':(45,332)},
          'thigh':{'anchor':(36,58),'distal':(82,361)},'shin':{'anchor':(53,40),'ankle':(54,298)},
          'cannon':{'muzzle':(429,80),'grip':(121,158),'support-grip':(295,113)}},
 'warden':{'head':{'neck':(132,274)},'torso':{'neck':(220,65),'shoulder':(57,121),'hip':(188,369)},
           'upper-arm':{'anchor':(31,86),'distal':(100,368)},'forearm':{'anchor':(40,49),'distal':(69,340)},
           'thigh':{'anchor':(44,61),'distal':(64,356)},'shin':{'anchor':(51,56),'ankle':(62,297)},
           'cannon':{'muzzle':(558,116),'grip':(96,191),'support-grip':(368,160)}},
 'engineer':{'head':{'neck':(151,298)},'torso':{'neck':(168,45),'shoulder':(36,116),'hip':(155,378)},
             'upper-arm':{'anchor':(74,42),'distal':(65,320)},'forearm':{'anchor':(48,68),'wrist':(86,240),'distal':(127,298)},
             'thigh':{'anchor':(98,38),'distal':(113,340)},'shin':{'anchor':(103,26),'ankle':(78,299)},
             'cannon':{'muzzle':(482,105),'grip':(145,196),'support-grip':(323,143)}}
}
manifest={}
for role in ('scout','warden','engineer'):
    path=source/f'{role}-source.png'; im=Image.open(path).convert('RGBA'); w,h=im.size
    assert im.getchannel('A').getextrema()==(0,255),'Source must have native alpha'
    images=[]
    for i,part in enumerate(parts):
        row,col=divmod(i,4); x0,x1=col*w//4,(col+1)*w//4
        if role=='warden' and row==1:
            x0,x1=[(0,443),(443,835),(835,1444),(1444,w)][col]
        if role=='engineer' and row==1:
            x0,x1=[(0,443),(443,820),(820,1340),(1340,w)][col]
        selected=Image.open(source.parent/'characters-v2/engineer-source.png').convert('RGBA') if role=='engineer' and i<2 else im
        if role=='engineer' and part=='forearm':
            selected=Image.open(source.parent/'characters-v2/engineer-grip-source.png').convert('RGBA')
        tile=selected.crop((x0,row*h//2,x1,(row+1)*h//2))
        # Ignore disconnected near-transparent generation specks when locating the part;
        # copy original RGBA inside the padded visual bounds without recoloring or masking it.
        labels,count=ndimage.label(np.asarray(tile.getchannel('A'))>64)
        sizes=np.bincount(labels.ravel());sizes[0]=0
        region=ndimage.find_objects(labels)[int(sizes.argmax())-1]
        ys,xs=region
        box=(max(0,xs.start-2),max(0,ys.start-2),min(tile.width,xs.stop+2),min(tile.height,ys.stop+2))
        images.append(tile.crop(box))
    cw=max(i.width for i in images)+8; ch=max(i.height for i in images)+8
    atlas=Image.new('RGBA',(cw*4,ch*2))
    lines=['# Native-alpha generated rig; export_characters.py','format=1',f'atlas=game/art/{role}.png',
           'pixelsPerUnit=1','layer=20','default=head','clips='+','.join(parts),'']
    for i,(part,img) in enumerate(zip(parts,images)):
        row,col=divmod(i,4); x,y=col*cw+4,row*ch+4;atlas.paste(img,(x,y))
        pw,ph=img.size; ax,ay=anchors[part]
        measured=calibration[role].get(part,{})
        anchor=measured.get('anchor',(ax*pw,ay*ph))
        points={key:(sx*pw,sy*ph) for key,(sx,sy) in sockets[part].items()}
        points.update({key:value for key,value in measured.items() if key!='anchor'})
        lines += [f'clip.{part}.loop=true',f'clip.{part}.frames={part}',f'frame.{part}.rect={x},{y},{pw},{ph}',
                  f'frame.{part}.durationMs=1000',f'frame.{part}.anchor={anchor[0]:.2f},{anchor[1]:.2f}',
                  f'frame.{part}.sockets='+','.join(points)]
        for name,(sx,sy) in points.items(): lines.append(f'frame.{part}.socket.{name}={sx:.2f},{sy:.2f}')
        lines.append('')
    atlas.save(target/f'{role}.png');(target/f'{role}.properties').write_text('\n'.join(lines),encoding='utf-8')
    manifest[role]={'source':str(path.relative_to(root)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
                    'parts':{name:list(img.size) for name,img in zip(parts,images)},'mode':'built-in imagegen; native RGBA; deterministic atlas packing'}
    if role=='engineer':
        replacement=source.parent/'characters-v2/engineer-source.png'
        manifest[role]['replacementParts']={'head':str(replacement.relative_to(root)),'torso':str(replacement.relative_to(root)),
                                           'sha256':hashlib.sha256(replacement.read_bytes()).hexdigest()}
        grip=source.parent/'characters-v2/engineer-grip-source.png'
        manifest[role]['replacementGrip']={'forearm':str(grip.relative_to(root)),
                                         'sha256':hashlib.sha256(grip.read_bytes()).hexdigest()}
    manifest[role]['calibration']='measured joint and palm centers; see characters-v2/rig-review.md'
    print(role,atlas.size,manifest[role]['parts'])
(source/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
