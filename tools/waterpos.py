import json, math
def mat(e):
    t=e["transform"]; f=t.get("flags",0); m=[1,0,0,0,1,0]
    if (f&7)==7:
        q=t["matrix"]; m=[q[0],q[1],q[2],q[3],q[4],q[5]]
    else:
        if f&2:
            r=t.get("rotation",0); c=math.cos(r); s=math.sin(r); m=[c,-s,0,s,c,0]
        if f&4: m[0]=t.get("scale_x",1); m[4]=t.get("scale_y",1)
        if f&1: m[2]=t.get("x",0); m[5]=t.get("y",0)
    return m
def mul(a,b):
    return [a[0]*b[0]+a[1]*b[3], a[0]*b[1]+a[1]*b[4], a[0]*b[2]+a[1]*b[5]+a[2],
            a[3]*b[0]+a[4]*b[3], a[3]*b[1]+a[4]*b[4], a[3]*b[2]+a[4]*b[5]+a[5]]
for name, idxs in [("bg",[41,47,54,82]),("bk",[72,89]),("bm",[118]),("bn",[81]),("bi",[13,31,37,38,47,71]),("bj",[4,77])]:
    d=json.load(open(f"assets/levels/{name}.json",encoding="utf-8")); objs={e["index"]:e for e in d["objects"]}
    print("="*8,name)
    for i in idxs:
        e=objs[i]; chain=[]; cur=e
        while cur is not None:
            chain.append(cur); p=cur.get("link_parent",-1); cur=objs.get(p) if p>=0 else None
        M=[1,0,0,0,1,0]
        for cur in reversed(chain): M=mul(M,mat(cur))
        b=e["beam"]; x0,y0,x1,y1=b["x0"],b["x1"],b["x2"],b["y"]
        # world corners: local (x0,y0),(x1,y1)
        def lp(x,y): return (M[0]*x+M[1]*y+M[2], M[3]*x+M[4]*y+M[5])
        print(f' idx {i:3d} worldOrigin=({M[2]:.1f},{M[5]:.1f}) rect world left-top {lp(x0,y1)} right-bottom {lp(x1,y0)} color={b["color"]}')
