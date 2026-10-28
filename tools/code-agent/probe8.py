# exact virtual resolution: vtable slots filled via  global.get X@vt ; ref.func F ; struct.set VT<T> field
import sys, runpy, collections, re, heapq
ROOTS=sys.argv[2].split(','); sys.argv=[sys.argv[0]]+sys.argv[1:2]
m=runpy.run_path('/tmp/code-agent/asyncchain.py', run_name='lib')
fn=m['fn']; byname=m['byname']; scan=m['scan']; FIB=m['FIB']; nimports=m['nimports']; tn=m['tn']; data=m['data']; leb=m['leb']; bodies=m['bodies']; fields=m['fields']; gl=m['gl']
slots=collections.defaultdict(set)
for bi,(bs,bz) in enumerate(bodies):
    e=bs+bz; j=bs
    while j<e-6:
        if data[j]==0x23:
            try:
                g,k=leb(j+1)
                if gl.get(g,'').endswith('@vt') and data[k]==0xd2:
                    f,k2=leb(k+1)
                    if data[k2]==0xfb and data[k2+1]==0x05:
                        t,k3=leb(k2+2); fi,_=leb(k3)
                        slots[(tn.get(t,str(t)),fields.get(t,{}).get(fi))].add(f)
            except IndexError: pass
        j+=1
print('slots',len(slots),'entries',sum(len(v) for v in slots.values()))
def unwrap(f):
    return f
NOPROP=set(byname.get('java.lang.Object::monitorEnter',[])+byname.get('java.lang.Object::monitorExit',[]))
def edges(i):
    calls,gets,v,fo=scan(i)
    for c in calls-NOPROP:
        if c not in FIB and c>=nimports and scan(c)[3]: yield 1,'call',c
    for g in gets:
        for c in byname.get(g.split('@')[0]+'::<clinit>',[]):
            if c!=i and scan(c)[3]: yield 1,'clinit',c
    for t,f in v:
        for c in slots.get((t,f),()):
            if '@caller' in fn[c]:
                for d in scan(c)[0]:
                    if d not in FIB and d>=nimports and scan(d)[3]: yield 1,'virt '+t+'.'+f,d
            elif scan(c)[3]: yield 1,'virt '+t+'.'+f,c
SUSP=set(k for k in FIB if fn[k]=='org.teavm.runtime.Fiber::suspend')
for ROOT in ROOTS:
    s=byname[ROOT][0]; dist={s:0}; prev={s:None}; pq=[(0,s)]; found=None
    while pq:
        d,i=heapq.heappop(pq)
        if d>dist[i]: continue
        if i!=s and scan(i)[0]&SUSP: found=i; break
        for w,k,c in edges(i):
            if c not in dist or d+w<dist[c]: dist[c]=d+w; prev[c]=(i,k); heapq.heappush(pq,(d+w,c))
    print('ROOT',ROOT,'cost',dist.get(found),'visited',len(dist))
    x=found; path=[]
    while x is not None:
        p=prev[x]; path.append(fn[x]+(f'   <-[{p[1]}]' if p else '')); x=p[0] if p else None
    print('\n'.join('   '+l for l in reversed(path)) if found else '   no seed')
