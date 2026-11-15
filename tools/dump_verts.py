import json
for name, idxs in [("bj",[28,27]),("bk",[151,152])]:
    d=json.load(open(f"assets/levels/{name}.json",encoding="utf-8"))
    print("="*8,name)
    for e in d["objects"]:
        if e["index"] in idxs:
            print("idx",e["index"],"class",e["class"],"local",e["transform"])
            if e.get("polygon"): print(" vertices",e["polygon"]["vertices"],"tris",e["polygon"]["triangles"],"color",e["polygon"]["color_argb"])
            if e.get("sprites"): print(" sprites",e["sprites"])
