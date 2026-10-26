package port.compat.java.awt;
/** Integer geometry only; no windowing dependency. */
public class Rectangle implements Cloneable,java.io.Serializable {
    public int x,y,width,height;
    public Rectangle() { }
    public Rectangle(int x,int y,int width,int height) { setBounds(x,y,width,height); }
    public Rectangle(Rectangle other) { this(other.x,other.y,other.width,other.height); }
    public void setBounds(int x,int y,int width,int height) { this.x=x;this.y=y;this.width=width;this.height=height; }
    public void setBounds(Rectangle value) { setBounds(value.x,value.y,value.width,value.height); }
    public double getX(){return x;} public double getY(){return y;} public double getWidth(){return width;} public double getHeight(){return height;}
    public boolean isEmpty(){return width<=0||height<=0;}
    public boolean contains(int px,int py){return width>0&&height>0&&px>=x&&py>=y&&(long)px<(long)x+width&&(long)py<(long)y+height;}
    public boolean contains(Rectangle r){return contains(r.x,r.y,r.width,r.height);}
    public boolean contains(int px,int py,int w,int h){return w>=0&&h>=0&&contains(px,py)&&(long)px+w<=(long)x+width&&(long)py+h<=(long)y+height;}
    public boolean intersects(Rectangle r){return !isEmpty()&&!r.isEmpty()&&(long)x+width>r.x&&(long)r.x+r.width>x&&(long)y+height>r.y&&(long)r.y+r.height>y;}
    public Rectangle union(Rectangle r){
        if(width<0||height<0)return new Rectangle(r);if(r.width<0||r.height<0)return new Rectangle(this);
        int left=Math.min(x,r.x),top=Math.min(y,r.y);
        long right=Math.max((long)x+width,(long)r.x+r.width),bottom=Math.max((long)y+height,(long)r.y+r.height);
        return new Rectangle(left,top,(int)Math.min(Integer.MAX_VALUE,right-left),(int)Math.min(Integer.MAX_VALUE,bottom-top));
    }
    public void add(Rectangle r){setBounds(union(r));}
    public Object clone(){return new Rectangle(this);}
    public boolean equals(Object o){return o instanceof Rectangle r&&x==r.x&&y==r.y&&width==r.width&&height==r.height;}
    public int hashCode(){return java.util.Objects.hash(x,y,width,height);}
    public String toString(){return "java.awt.Rectangle[x="+x+",y="+y+",width="+width+",height="+height+"]";}
}
