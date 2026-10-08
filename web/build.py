"""web/src.html + 앱 데이터(points.json, catches.json, 섬 외곽선) → docs/index.html (GitHub Pages)"""
import json, os, sys
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(root, 'web/src.html'), encoding='utf-8').read()
shape = json.load(open(os.path.join(root, 'web/shape.json'), encoding='utf-8'))
pts = json.load(open(os.path.join(root, 'app/src/main/assets/points.json'), encoding='utf-8'))['points']
recs = json.load(open(os.path.join(root, 'app/src/main/assets/catches.json'), encoding='utf-8'))['records']
kp = ['id','name','lat','lng','facingDeg','depthMin','depthMax','depth','terrain','species','target','targetDistance']
kr = ['date','startHour','endHour','pointId','sideFacingDeg','catches','rating','source']
data = ("const SHAPE=" + json.dumps(shape, ensure_ascii=False) + ";\n"
        "const POINTS=" + json.dumps([{k: p[k] for k in kp} for p in pts], ensure_ascii=False) + ";\n"
        "const RECORDS=" + json.dumps([{k: r[k] for k in kr if k in r} for r in recs], ensure_ascii=False) + ";")
page = src.replace('/*DATA*/', data)
if len(sys.argv) > 1:   # 아티팩트용 (문서 골격 없이)
    open(sys.argv[1], 'w', encoding='utf-8').write(page)
os.makedirs(os.path.join(root, 'docs'), exist_ok=True)
head = ('<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
        '<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}'
        'body{margin:0}img{max-width:100%}[hidden]{display:none!important}</style></head><body>\n')
open(os.path.join(root, 'docs/index.html'), 'w', encoding='utf-8').write(head + page + '\n</body></html>\n')
print('built', len(page))
