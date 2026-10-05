# Espelho em Python da lógica de domain/./FocusCropExpression.kt. Serve só para validar o desenho da expressão num FFmpeg real
# (tools/ffmpeg-validation/validate_crop.sh). A fonte da verdade é o Kotlin; mantenha os dois alinhados.
def number(v):
    if abs(v)<0.00005: return "0"
    return ("%.4f"%v).rstrip('0').rstrip('.')
def signed(v):
    t=number(v); return "(%s)"%t if t.startswith('-') else t
def anchors(points,start,dur_ms,axis):
    d=dur_ms/1000.0
    a=[((p[0]-start)/1000.0, min(1,max(0,p[1 if axis=='x' else 2]))) for p in points]
    a=[x for x in a if 0<=x[0]<=d]
    a.sort(key=lambda x:x[0])
    out=[]
    for x in a:
        if not out or out[-1][0]!=x[0]: out.append(x)
    if not out: return []
    if len(out)>40:
        idx=[int(__import__('math').floor(i*(len(out)-1)/39+0.5)) for i in range(40)]
        o2=[]
        for i in idx:
            if not o2 or o2[-1][0]!=out[i][0]: o2.append(out[i])
        out=o2
    if out[0][0]>0: out=[(0.0,out[0][1])]+out
    return out
def center(an):
    if len(an)==1 or all(abs(a[1]-an[0][1])<0.00005 for a in an): return number(an[0][1])
    s=""
    for i in range(len(an)-1):
        f,t=an[i],an[i+1]
        sl=(t[1]-f[1])/(t[0]-f[0])
        local="t" if f[0]==0 else "(t-%s)"%number(f[0])
        seg=number(f[1]) if abs(sl)<0.00005 else "%s+%s*%s"%(number(f[1]),signed(sl),local)
        s+="if(lt(t,%s),%s,"%(number(t[0]),seg)
    return s+number(an[-1][1])+")"*(len(an)-1)
def pos(frame,crop,c): return "max(0,min(%s-%s,(%s)*%s-%s/2))"%(frame,crop,c,frame,crop)
def crop(tw,th,points,start,dur):
    w="min(iw,ih*%d/%d)"%(tw,th); h="min(ih,iw*%d/%d)"%(th,tw)
    xa=anchors(points,start,dur,'x'); ya=anchors(points,start,dur,'y')
    x="(iw-ow)/2" if not xa else pos("iw","ow",center(xa))
    y="(ih-oh)/2" if not ya else pos("ih","oh",center(ya))
    e=lambda s:s.replace(",","\\,")
    return "crop=w=%s:h=%s:x=%s:y=%s"%(e(w),e(h),e(x),e(y))
if __name__=="__main__":
    print(crop(1080,1920,[],0,2000))
    print(crop(1080,1920,[(10000,0.2,0.4),(12000,0.8,0.4)],10000,2000))
    print(crop(1080,1920,[(10500,0.9,0.5),(11500,0.1,0.5)],10000,2000))
