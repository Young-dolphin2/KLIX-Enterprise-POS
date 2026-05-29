import os, re
root='BarAndGrillOwnerPanel'
count=0
for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.kt'): continue
        path=os.path.join(dirpath,fn)
        with open(path,'r',encoding='utf-8') as f:
            s=f.read()
        orig=s
        # replace double question marks
        s=s.replace('??', '?.')
        # fix get("table"] -> get("table")
        s=re.sub(r'get\("([^\"]+)"\]\)', r'get("\1")', s)
        s=re.sub(r'get\("([^\"]+)"\]', r'get("\1")', s)
        # ensure safe-navigation before select when chained after get(...)
        s=re.sub(r'\)\.select\(\)', r')?.select()', s)
        s=re.sub(r'\)\.select\s*\{', r')?.select {', s)
        # ensure decodeAs after select is safe (if not already)
        s=re.sub(r'\.select\(\)\.(decode[A-Za-z0-9_<>]+)', r'.select()?.\1', s)
        if s!=orig:
            with open(path,'w',encoding='utf-8') as f:
                f.write(s)
            print('Cleaned', path)
            count+=1
print('Done. Files changed:',count)
