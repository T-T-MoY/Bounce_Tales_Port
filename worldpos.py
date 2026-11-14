import json, math

def mat(e):
    t=e["transform"]; f=t.get("flags",0)
    m=[1,0,0,0,1,0]
    if (f&7)==7:
        q=t["matrix"]; m=[q[0],q[1],q[2],q[3],q[4],q[5]]
    else:
        if f&2:
            r=t.get("rotation",0); c=math.cos(r); s=math.sin(r)
            m=[c,-s,0,s,c,0]
        if f&4:
            m[0]=t.get("scale_x",1); m[4]=t.get("scale_y",1)
        if f&1:
            m[2]=t.get("x",0); m[5]=t.get("y",0)
    return m

def mul(a,b):
    # a*b affine [a,b,c,d,e,f] as libgdx m00,m01,m02,m10,m11,m12
    return [a[0]*b[0]+a[1]*b[3], a[0]*b[1]+a[1]*b[4], a[0]*b[2]+a[1]*b[5]+a[2],
            a[3]*b[0]+a[4]*b[3], a[3]*b[1]+a[4]*b[4], a[3]*b[2]+a[4]*b[5]+a[5]]

for name, idxs in [("bj",[1,123,126,27,28,9,18]),("bk",[150,151,152,126,185]),("bt",[105,106,107,108,109,110,68,69,111]),("bm",[1,4,5,6,172])]:
    d=json.load(open(f"assets/levels/{name}.json",encoding="utf-8"))
    objs={e["index"]:e for e in d["objects"]}
    print("="*10,name)
    for i in idxs:
        e=objs[i]; chain=[]; cur=e
        while cur is not None:
            chain.append(cur); p=cur.get("link_parent",-1)
            cur=objs.get(p) if p>=0 else None
        M=[1,0,0,0,1,0]
        for cur in reversed(chain): M=mul(M,mat(cur))
        print(f' idx {i:4d} class {e["class"]:2s} world=({M[2]:.1f},{M[5]:.1f}) local={e["transform"].get("x")},{e["transform"].get("y")} box={e.get("trigger",{}).get("box")}')
