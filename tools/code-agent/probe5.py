import sys, runpy
names=sys.argv[2:]; sys.argv=[sys.argv[0]]+sys.argv[1:2]
import os
_lib=os.path.join(os.path.dirname(os.path.abspath(__file__)),'asyncchain.py')
if not os.path.exists(_lib): _lib='/tmp/code-agent/asyncchain.py'
m=runpy.run_path(_lib, run_name='lib')
fn=m['fn']; byname=m['byname']; scan=m['scan']; FIB=m['FIB']
for n in names:
    for i in byname.get(n,[]):
        c,g,v,fo=scan(i)
        print(n,'fiber',fo)
        for x in sorted(c,key=lambda x:fn[x]):
            if x not in FIB: print('   call',fn[x],'fiber',scan(x)[3])
        for x in sorted(g): print('   init',x)
        for t,f in sorted(v): print('   virt',t+'.'+f)
