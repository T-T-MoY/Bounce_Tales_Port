import json
d=json.load(open("assets/sprites/sprites.json",encoding="utf-8"))
print(type(d), list(d.keys())[:10] if isinstance(d,dict) else len(d))
# inspect structure
s=json.dumps(d)[:1200]
print(s)
