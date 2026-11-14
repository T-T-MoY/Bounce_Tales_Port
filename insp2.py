import json
d=json.load(open("assets/sprites/sprites.json",encoding="utf-8"))
for k in ["334","474","480","485","118","342"]:
    print(k, "desc", d["descriptors"].get(k), "anim", d.get("animations",{}).get(k) if isinstance(d.get("animations"),dict) else None)
print("animations type", type(d.get("animations")), (list(d.get("animations",{}).items())[:3] if isinstance(d.get("animations"),dict) else None))
