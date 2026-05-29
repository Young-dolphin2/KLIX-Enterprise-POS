import os, re
root='BarAndGrillOwnerPanel'
count=0
pattern = re.compile(r"\.postgrest\[\"([^\"]+)\"\]")
for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.kt'): continue
        path=os.path.join(dirpath,fn)
        with open(path,'r',encoding='utf-8') as f:
            s=f.read()
        new = pattern.sub(r'?.postgrest?.get("\1")', s)
        if new!=s:
            with open(path,'w',encoding='utf-8') as f:
                f.write(new)
            print('Fixed brackets in', path)
            count+=1
print('Done. Files changed:',count)
