import json
for name, idxs in [("bj",[1,123,126]),("bk",[150]),("bl",[154]),("bm",[1,4]),("bn",[158]),("bp",[155]),("bt",[105,106,107,108,109,110])]:
    data=json.load(open(f"assets/levels/{name}.json",encoding="utf-8"))
    print("="*20,name)
    for e in data["objects"]:
        if e["index"] in idxs:
            print(json.dumps(e,indent=1))
    ball=[e for e in data["objects"] if e["tag"]==8]
    print("ball:",ball[0]["index"] if ball else None, "entities with index filter:")
    for e in data["objects"]:
        if e["index"] in [138,185,176,172,188,156,111]:
            print("  target",e["index"],e["class"],e["tag"],e["transform"],"flags_m",e["flags_m"],"solid",e["solid"],"depth",e["depth"])
