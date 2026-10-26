package port;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import port.compat.java.security.MessageDigest;
class DigestGeometryTest {
    @Test void digestsMatchJdkAcrossBlockBoundariesAndClones()throws Exception {
        Random random=new Random(1234);
        for(String algorithm:List.of("MD5","SHA-1","SHA-256"))for(int n:new int[]{0,1,55,56,63,64,65,127,128,8193}){
            byte[] bytes=new byte[n];random.nextBytes(bytes);
            var digest=MessageDigest.getInstance(algorithm);digest.update(bytes,0,n/2);
            var clone=(MessageDigest)digest.clone();digest.update(bytes,n/2,n-n/2);clone.update(bytes,n/2,n-n/2);
            byte[] expected=java.security.MessageDigest.getInstance(algorithm).digest(bytes);
            assertArrayEquals(expected,digest.digest(),algorithm+":"+n);assertArrayEquals(expected,clone.digest());
            assertArrayEquals(java.security.MessageDigest.getInstance(algorithm).digest(),digest.digest());
        }
        assertThrows(java.security.NoSuchAlgorithmException.class,()->MessageDigest.getInstance("unknown"));
        assertFalse(MessageDigest.isEqual(new byte[]{0},new byte[0]));
    }
    @Test void rectangleAndLineSemanticsMatchJdk() {
        Random random=new Random(44);
        for(int i=0;i<3000;i++){
            int x=random.nextInt(),y=random.nextInt(),w=random.nextInt(),h=random.nextInt();
            var a=new port.compat.java.awt.Rectangle(x,y,w,h);var b=new java.awt.Rectangle(x,y,w,h);
            int px=random.nextInt(),py=random.nextInt(),pw=random.nextInt(),ph=random.nextInt();
            var c=new port.compat.java.awt.Rectangle(px,py,pw,ph);var d=new java.awt.Rectangle(px,py,pw,ph);
            assertEquals(b.contains(px,py),a.contains(px,py));assertEquals(b.contains(d),a.contains(c));assertEquals(b.intersects(d),a.intersects(c));
            assertEquals(b.union(d).toString(),a.union(c).toString());
            assertEquals(java.awt.geom.Line2D.linesIntersect(x,y,w,h,px,py,pw,ph),port.compat.java.awt.geom.Line2D.linesIntersect(x,y,w,h,px,py,pw,ph));
        }
    }
}
