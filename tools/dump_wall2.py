import json
for name, idxs in [("bj",[27,28]),("bk",[151,152]),("bm",[5,6]),("bt",[68,69]),("bl",[44,45]),("bn",[110,111]),("bp",[10,11])]:
    d=json.load(open(f"assets/levels/{name}.json",encoding="utf-8"))
    print("="*8,name)
    for e in d["objects"]:
        if e["index"] in idxs:
            print("idx",e["index"],"class",e["class"],"depth",e["depth"],"solid",e["solid"],"flags_m",e["flags_m"],"parent",e["link_parent"])
            if e.get("polygon"): print("  poly color",e["polygon"].get("color_argb"),"verts",len(e["polygon"]["vertices"]),"trigger",e["polygon"].get("trigger_id"))
            if e.get("sprites"): print("  sprites",e["sprites"])
