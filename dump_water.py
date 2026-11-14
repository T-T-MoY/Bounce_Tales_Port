import json,glob,os
for path in sorted(glob.glob("assets/levels/*.json")):
    d=json.load(open(path,encoding="utf-8"))
    ws=[e for e in d["objects"] if e["tag"]==10]
    if not ws: continue
    print("="*10,os.path.basename(path),len(ws))
    for e in ws:
        b=e["beam"]
        color=tuple(b.get("color",[]))
        x0=b["x0"]; y0=b["x1"]; x1=b["x2"]; y1=b["y"]
        print(f' idx={e["index"]:4d} parent={e["link_parent"]:4d} pos=({e["transform"]["x"]},{e["transform"]["y"]}) rect=({x0},{y0})-({x1},{y1}) size=({x1-x0},{y1-y0}) cur=({b["p3"]},{b["p1"]},{b["p2"]},{b["p0"]}) color={color} skip={b.get("_skip")} depth={e["depth"]}')
