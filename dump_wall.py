import json
for name, idxs in [("bj",[27,28,9]),("bk",[151,152]),("bm",[5,6]),("bt",[68,69,8]),("bl",[44,45,4]),("bn",[110,111,17]),("bp",[10,11,18])]:
    d=json.load(open(f"assets/levels/{name}.json",encoding="utf-8"))
    print("="*12,name)
    for e in d["objects"]:
        if e["index"] in idxs:
            print("idx",e["index"],"class",e["class"],"tag",e["tag"],"pos",e["transform"],"depth",e["depth"],"flags_m",e["flags_m"],"solid",e["solid"])
            if e.get("sprites"): print("   sprites",e["sprites"])
            if e.get("trigger"): print("   trigger",e["trigger"]["state"],e["trigger"]["mode_a"],e["trigger"]["mode_b"],e["trigger"]["filter_object"],"box",e["trigger"]["box"])
