#!/usr/bin/env python3
"""Export the inspected native-alpha foreground terrain and six props for each late chapter."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
import numpy as np
from scipy.ndimage import label, distance_transform_edt
from export_chapter_art import ROOT, SOURCES, OUTPUT, QA, sha, metadata, prepare

NAMES=('deck','platform','support','prop-a','prop-b','trim')
SEEDS={3:[(300,250),(1000,255),(1390,200),(200,780),(650,770),(1150,735)],
       4:[(250,300),(950,350),(1400,250),(175,700),(650,800),(1200,840)],
       5:[(500,260),(1000,250),(1420,250),(180,650),(600,800),(1200,850)]}

def split(source,world):
 """The staggered parts' bounding boxes overlap; split actual silhouettes, not equal cells.

 Six visually inspected major components define ownership. Nearest opaque-component ownership
 retains each component's soft native alpha edge, including narrow 2px gaps and disconnected
 details. No RGB/alpha value belonging to a selected component is normalized or defringed.
 """
 pixels=np.asarray(source);alpha=pixels[:,:,3]
 components,count=label(alpha>=80)
 counts=np.bincount(components.ravel());counts[0]=0
 major=np.flatnonzero(counts>10000)
 assert len(major)==6,(world,len(major))
 opaque=np.where(np.isin(components,major),components,0)
 nearest=distance_transform_edt(opaque==0,return_distances=False,return_indices=True)
 owner=opaque[tuple(nearest)]
 selected=[int(owner[y,x]) for x,y in SEEDS[world]]
 assert len(set(selected))==6
 result=[]
 for component in selected:
  rgba=pixels.copy();rgba[:,:,3]=np.where(owner==component,alpha,0)
  # Every sufficiently covered source material pixel has exactly one assigned output.
  assert np.array_equal(rgba[:,:,3][owner==component],alpha[owner==component])
  result.append((Image.fromarray(rgba,'RGBA'),int(counts[component])))
 return result
def proof(parts,world,color,name):
 sheet=Image.new('RGB',(1260,760),color);draw=ImageDraw.Draw(sheet)
 for i,part in enumerate(parts):
  x,y=(i%3)*420,(i//3)*380; thumb=part.copy();thumb.thumbnail((390,315),Image.Resampling.LANCZOS)
  sheet.paste(thumb,(x+(420-thumb.width)//2,y+10),thumb)
  draw.text((x+20,y+347),NAMES[i],fill='white' if sum(color)<400 else '#111820')
 sheet.save(QA/f'chapter{world}-terrain-{name}.png')

def main():
 report={'format':1,'tool':'built-in image_gen','method':'Six inspected major alpha>=80 components split overlapping/staggered layout. Native soft alpha assigned to its nearest major component, unchanged RGB/alpha within ownership; crop at alpha>=8. No matte removal, defringing or alpha normalization; maximum640px uniform scaling; 2px transparent guard.', 'worlds':{}}
 for world in (3,4,5):
  path=SOURCES/f'chapter{world}-terrain-generated.png';source=Image.open(path);before=sha(path)
  assert source.mode=='RGBA' and source.size==(1536,1024)
  parts=[];infos=[]
  for name,(isolated,covered),seed in zip(NAMES,split(source,world),SEEDS[world]):
   part,info=prepare(isolated,(0,0,*source.size),name,{})
   info['component_seed']=seed;info['source_major_component_pixels']=covered
   parts.append(part);infos.append(info)
  atlas=Image.new('RGBA',(sum(p.width+4 for p in parts)+4,max(p.height for p in parts)+8));x=4
  for part,info in zip(parts,infos):
   atlas.paste(part,(x,4));info['atlas_rect']=[x,4,*part.size];x+=part.width+4
  name=f'chapter{world}-terrain';atlas.save(OUTPUT/(name+'.png'),optimize=True)
  (OUTPUT/(name+'.properties')).write_text(metadata(name,infos,15),encoding='utf-8')
  for label,color in [('black',(8,12,19)),('white',(246,246,238)),('blue',(49,92,119))]:proof(parts,world,color,label)
  report['worlds'][world]={'source_sha256':before,'source_size':list(source.size),'parts':infos,
      'outputs':{p.name:{'sha256':sha(p),'bytes':p.stat().st_size} for p in OUTPUT.glob(name+'.*')}}
  assert before==sha(path)
 report['script_sha256']=sha(Path(__file__));report['helper_script_sha256']=sha(Path(__file__).with_name('export_chapter_art.py'))
 (ROOT/'docs/design/evolution/chapter-terrain-provenance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps({world:value['outputs'] for world,value in report['worlds'].items()},indent=2))
if __name__=='__main__':main()
