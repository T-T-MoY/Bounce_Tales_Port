import json, glob, os
ops={12:"EQ",13:"NE",14:"LT",15:"GT"}
for path in sorted(glob.glob("assets/levels/*.json")):
    data=json.load(open(path,encoding="utf-8"))
    hits=[]
    for ei,e in enumerate(data["objects"]):
        tr=e.get("trigger")
        if not tr: continue
        for pi,prog in enumerate(tr.get("programs",[])):
            b=prog
            for i,op in enumerate(b):
                if op in ops and i+4<len(b) and b[i+2]==3:
                    hits.append((e["index"],e["tag"],tr.get("mode_a"),tr.get("filter_object"),pi,i,ops[op],b[i:i+9]))
    if hits:
        print(os.path.basename(path), "hits:",len(hits))
        for h in hits[:20]: print("  ",h)
