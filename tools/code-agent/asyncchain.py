# Read-only heuristic analyzer: why is a wasm function a TeaVM coroutine?
# usage: asyncchain.py boot.wasm root1 [root2...]  (env DEPTH, default 12)
import sys, os, re, collections
data=open(sys.argv[1],'rb').read()
def leb(p):
    r=s=0
    while True:
        b=data[p]; p+=1; r|=(b&0x7f)<<s; s+=7
        if b<0x80: return r,p
p=8; secs=[]
while p<len(data):
    sid=data[p]; p+=1; size,p=leb(p); secs.append((sid,p,size)); p+=size
nimports=0; fn={}; gl={}; fields=collections.defaultdict(dict); tn={}
for sid,start,size in secs:
    if sid==2:
        q=start; n,q=leb(q)
        for _ in range(n):
            l,q=leb(q); q+=l; l,q=leb(q); q+=l; kind=data[q]; q+=1
            if kind==0: _,q=leb(q); nimports+=1
            elif kind==1:
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
                def namemap(q):
                    m={}; n,q=leb(q)
                    for _ in range(n):
                        i,q=leb(q); l,q=leb(q); m[i]=data[q:q+l].decode('utf8','replace'); q+=l
                    return m,q
                if sub==1: fn,_=namemap(q)
                elif sub==4: tn,_=namemap(q)
                elif sub==7: gl,_=namemap(q)
                elif sub==10:
                    n,q2=leb(q)
                    for _ in range(n):
                        ti,q2=leb(q2); m,q2=namemap(q2); fields[ti]=m
                q=se
for sid,start,size in secs:
    if sid==10:
        q=start; n,q=leb(q); bodies=[]
        for i in range(n):
            sz,q=leb(q); bodies.append((q,sz)); q+=sz
byname=collections.defaultdict(list)
for k,v in fn.items(): byname[v].append(k)
FIB=set(k for k,v in fn.items() if v.startswith('org.teavm.runtime.Fiber::'))
cache={}
def scan(idx):
    if idx in cache: return cache[idx]
    calls=set(); gets=set(); vcalls=set()
    if idx>=nimports:
        bs,bz=bodies[idx-nimports]; e=bs+bz; j=bs
        while j<e-1:
            b=data[j]
            try:
                if b==0x10:
                    v,_=leb(j+1)
                    if v in fn: calls.add(v)
                elif b==0x23:
                    v,_=leb(j+1)
                    if v in gl and 'initializer' in gl[v].lower(): gets.add(gl[v])
                elif b==0xfb and data[j+1]==0x02:
                    t,k=leb(j+2); f,_=leb(k)
                    nm=fields.get(t,{}).get(f)
                    if nm and tn.get(t,'').startswith('VT<'):
                        vcalls.add((tn.get(t,str(t)),nm))
            except IndexError: pass
            j+=1
    r=(calls,gets,vcalls,bool(calls&FIB)); cache[idx]=r; return r
def show(name,depth,seen,ind=''):
    for idx in byname.get(name,[]):
        calls,gets,vcalls,fo=scan(idx)
        print(f"{ind}{name} [fiber={fo}]")
        if idx in seen or depth==0: continue
        seen.add(idx)
        if not fo: continue
        for g in sorted(gets): print(f"{ind}  ~clinit-get {g}")
        for t,f in sorted(vcalls)[:15]: print(f"{ind}  ~vfield {t}.{f}")
        for c in sorted(calls, key=lambda c: fn[c]):
            if c in FIB: continue
            if scan(c)[3] or fn[c].startswith('teavm'): show(fn[c],depth-1,seen,ind+'  ')
if __name__=='__main__':
    print('nfuncs',len(fn),'fields-types',len(fields),'globals',len(gl))
    seen=set()
    for r in sys.argv[2:]: show(r,int(os.environ.get('DEPTH','12')),seen)
