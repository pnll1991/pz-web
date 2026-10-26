package port.compat.java.awt.geom;
public abstract class Line2D {
    public static int relativeCCW(double x1,double y1,double x2,double y2,double px,double py){
        x2-=x1;y2-=y1;px-=x1;py-=y1;double cross=px*y2-py*x2;
        if(cross==0){cross=px*x2+py*y2;if(cross>0){px-=x2;py-=y2;cross=px*x2+py*y2;if(cross<0)cross=0;}}
        return cross<0?-1:cross>0?1:0;
    }
    public static boolean linesIntersect(double x1,double y1,double x2,double y2,double x3,double y3,double x4,double y4){
        return relativeCCW(x1,y1,x2,y2,x3,y3)*relativeCCW(x1,y1,x2,y2,x4,y4)<=0&&relativeCCW(x3,y3,x4,y4,x1,y1)*relativeCCW(x3,y3,x4,y4,x2,y2)<=0;
    }
}
