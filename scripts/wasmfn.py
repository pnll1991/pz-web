import sys
data=open(sys.argv[1],'rb').read()
def leb(p):
    r=s=0
    while True:
        b=data[p]; p+=1; r|=(b&0x7f)<<s; s+=7
        if b<0x80: return r,p
p=8; secs=[]
while p<len(data):
    sid=data[p]; p+=1; size,p=leb(p); secs.append((sid,p,size)); p+=size
nimports=0; names={}
for sid,start,size in secs:
    if sid==2:
        q=start; n,q=leb(q)
        for _ in range(n):
            l,q=leb(q); q+=l; l,q=leb(q); q+=l; kind=data[q]; q+=1
            if kind==0: _,q=leb(q); nimports+=1
            elif kind==1:
                # table: reftype (may be 0x63/0x64 + heaptype) + limits
                t=data[q]; q+=1
                if t in (0x63,0x64): _,q=leb(q)
                fl=data[q]; q+=1; _,q=leb(q)
                if fl&1: _,q=leb(q)
            elif kind==2:
                fl=data[q]; q+=1; _,q=leb(q)
                if fl&1: _,q=leb(q)
            elif kind==3:
                t=data[q]; q+=1
                if t in (0x63,0x64): _,q=leb(q)
                q+=1
            elif kind==4: q+=1; _,q=leb(q)
    if sid==0:
        q=start; l,q=leb(q); nm=data[q:q+l]; q+=l
        if nm==b'name':
            end=start+size
            while q<end:
                sub=data[q]; q+=1; sz,q=leb(q); se=q+sz
                if sub==1:
                    n,q=leb(q)
                    for _ in range(n):
                        i,q=leb(q); l,q=leb(q); names[i]=data[q:q+l].decode('utf8','replace'); q+=l
                q=se
want=sys.argv[2:]
byname={v:k for k,v in names.items()}
for sid,start,size in secs:
    if sid==10:
        q=start; n,q=leb(q); bodies=[]
        for i in range(n):
            sz,q=leb(q); bodies.append((q,sz)); q+=sz
for w in want:
    matches=[k for k,v in names.items() if v==w]
    for idx in matches:
        if idx<nimports: print(w,'import'); continue
        bs,bz=bodies[idx-nimports]
        b=data[bs:bs+bz]
        calls=set()
        for j in range(len(b)-1):
            if b[j]==0x10:
                try:
                    v,_=leb(bs+j+1)
                    if v in names: calls.add(names[v])
                except Exception: pass
        print(w,"idx",idx,"size",bz,"calls:",sorted(c for c in calls if not c.startswith("org.teavm.runtime.Fiber::")), "fiberops:", any("popInt" in c for c in calls))
