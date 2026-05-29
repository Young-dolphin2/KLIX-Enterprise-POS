import os, re
root = 'BarAndGrillOwnerPanel'
count=0
for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.kt'): continue
        path=os.path.join(dirpath,fn)
        with open(path,'r',encoding='utf-8') as f:
            s=f.read()
        orig=s
        # 1. client.postgrest -> client?.postgrest
        s=re.sub(r'SupabaseManager\.client\s*\.postgrest', 'SupabaseManager.client?.postgrest', s)
        s=re.sub(r'com\.example\.barandgrillownerpanel\.data\.remote\.SupabaseManager\.client\s*\.postgrest', 'com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?.postgrest', s)
        # 2. .postgrest["table"] -> ?.postgrest?.get("table")
        s=re.sub(r'\.postgrest\["', '?.postgrest?.get("', s)
        # 3. .select().decodeAsX -> .select()?.decodeAsX
        s=re.sub(r'\.select\(\)\.(decode[A-Za-z0-9_<>]+)', r'.select()?.\1', s)
        # 4. decodeAs<List...>>() -> decodeAs<List...>>() ?: emptyList()
        s=re.sub(r'(decodeAs<[^>]+>\(\))', r'\1 ?: emptyList()', s)
        if s!=orig:
            with open(path,'w',encoding='utf-8') as f:
                f.write(s)
            print('Patched', path)
            count+=1
print('Done. Files changed:', count)
