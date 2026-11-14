import json
d=json.load(open("assets/sprites/sprites.json",encoding="utf-8"))
for k in ["334","474","480","485","211","17","28","5","208"]:
    p=d["prefabs"].get(k)
    print(k, json.dumps(p)[:400] if p else None)
