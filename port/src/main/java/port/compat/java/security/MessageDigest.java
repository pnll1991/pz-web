package port.compat.java.security;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

/** Incremental MD5/SHA-1/SHA-256 for resource integrity; algorithms use fixed-width integer arithmetic. */
public class MessageDigest implements Cloneable {
    private final String algorithm;
    private int[] state;
    private byte[] pending=new byte[64];
    private int used;
    private long length;
    protected MessageDigest(String algorithm){this.algorithm=algorithm;reset();}
    public static MessageDigest getInstance(String algorithm)throws NoSuchAlgorithmException {
        String name=Objects.requireNonNull(algorithm).toUpperCase(Locale.ROOT);
        name=switch(name){case "SHA","SHA1"->"SHA-1";case "SHA256"->"SHA-256";default->name;};
        if(!Set.of("MD5","SHA-1","SHA-256").contains(name))throw new NoSuchAlgorithmException("Browser digest: "+algorithm);
        return new MessageDigest(name);
    }
    public static MessageDigest getInstance(String algorithm,String provider)throws NoSuchAlgorithmException,NoSuchProviderException {if(provider==null||provider.isEmpty())throw new IllegalArgumentException("Provider");throw new NoSuchProviderException("JVM provider unavailable in browser: "+provider);}
    public static MessageDigest getInstance(String algorithm,Provider provider)throws NoSuchAlgorithmException {if(provider==null)throw new IllegalArgumentException("Provider");throw new NoSuchAlgorithmException("JVM provider is not installed in browser: "+provider.getName());}
    public String getAlgorithm(){return algorithm;}
    public int getDigestLength(){return algorithm.equals("MD5")?16:algorithm.equals("SHA-1")?20:32;}
    public void reset(){used=0;length=0;state=algorithm.equals("SHA-256")?new int[]{0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19}:new int[]{0x67452301,0xefcdab89,0x98badcfe,0x10325476,0xc3d2e1f0};Arrays.fill(pending,(byte)0);}
    public void update(byte value){pending[used++]=value;length++;if(used==64){compress();used=0;}}
    public void update(byte[] input){update(input,0,input.length);}
    public void update(byte[] input,int offset,int count){Objects.checkFromIndexSize(offset,count,input.length);for(int i=0;i<count;i++)update(input[offset+i]);}
    public void update(ByteBuffer input){while(input.hasRemaining())update(input.get());}
    public byte[] digest(byte[] bytes){update(bytes);return digest();}
    public byte[] digest(){
        long bits=length*8;update((byte)0x80);while(used!=56)update((byte)0);
        boolean little=algorithm.equals("MD5");for(int i=0;i<8;i++)update((byte)(bits>>>(little?i*8:(7-i)*8)));
        byte[] result=new byte[getDigestLength()];for(int i=0;i<result.length;i++)result[i]=(byte)(state[i/4]>>>(little?(i%4)*8:(3-i%4)*8));
        reset();return result;
    }
    public int digest(byte[] output,int offset,int length)throws DigestException {int n=getDigestLength();if(length<n)throw new DigestException("Output buffer too small");Objects.checkFromIndexSize(offset,length,output.length);byte[] value=digest();System.arraycopy(value,0,output,offset,n);return n;}
    public Object clone(){try{MessageDigest copy=(MessageDigest)super.clone();copy.state=state.clone();copy.pending=pending.clone();return copy;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
    public static boolean isEqual(byte[] a,byte[] b){if(a==b)return true;if(a==null||b==null)return false;int diff=a.length^b.length;for(int i=0;i<a.length;i++)diff|=(a[i]^(b.length==0?0:b[i%b.length]));return diff==0;}
    private void compress(){if(algorithm.equals("MD5"))md5();else if(algorithm.equals("SHA-1"))sha1();else sha256();}
    private int word(int i,boolean little){int p=i*4;return little?(pending[p]&255)|(pending[p+1]&255)<<8|(pending[p+2]&255)<<16|(pending[p+3]&255)<<24:(pending[p]&255)<<24|(pending[p+1]&255)<<16|(pending[p+2]&255)<<8|(pending[p+3]&255);}
    private static final int[] MD5_SHIFT={7,12,17,22,5,9,14,20,4,11,16,23,6,10,15,21};
    private static final int[] MD5_K=new int[64];
    static{for(int i=0;i<64;i++)MD5_K[i]=(int)(long)(Math.abs(Math.sin(i+1))*4294967296.0);}
    private void md5(){int a=state[0],b=state[1],c=state[2],d=state[3];for(int i=0;i<64;i++){int f,g;if(i<16){f=(b&c)|(~b&d);g=i;}else if(i<32){f=(d&b)|(~d&c);g=(5*i+1)%16;}else if(i<48){f=b^c^d;g=(3*i+5)%16;}else{f=c^(b|~d);g=(7*i)%16;}int next=b+Integer.rotateLeft(a+f+MD5_K[i]+word(g,true),MD5_SHIFT[(i/16)*4+i%4]);a=d;d=c;c=b;b=next;}state[0]+=a;state[1]+=b;state[2]+=c;state[3]+=d;}
    private void sha1(){int[] w=new int[80];for(int i=0;i<16;i++)w[i]=word(i,false);for(int i=16;i<80;i++)w[i]=Integer.rotateLeft(w[i-3]^w[i-8]^w[i-14]^w[i-16],1);int a=state[0],b=state[1],c=state[2],d=state[3],e=state[4];for(int i=0;i<80;i++){int f,k;if(i<20){f=(b&c)|(~b&d);k=0x5a827999;}else if(i<40){f=b^c^d;k=0x6ed9eba1;}else if(i<60){f=(b&c)|(b&d)|(c&d);k=0x8f1bbcdc;}else{f=b^c^d;k=0xca62c1d6;}int t=Integer.rotateLeft(a,5)+f+e+k+w[i];e=d;d=c;c=Integer.rotateLeft(b,30);b=a;a=t;}state[0]+=a;state[1]+=b;state[2]+=c;state[3]+=d;state[4]+=e;}
    private static final int[] SHA_K={0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
    private void sha256(){int[] w=new int[64];for(int i=0;i<16;i++)w[i]=word(i,false);for(int i=16;i<64;i++){int x=w[i-15],y=w[i-2];w[i]=w[i-16]+(Integer.rotateRight(x,7)^Integer.rotateRight(x,18)^(x>>>3))+w[i-7]+(Integer.rotateRight(y,17)^Integer.rotateRight(y,19)^(y>>>10));}int a=state[0],b=state[1],c=state[2],d=state[3],e=state[4],f=state[5],g=state[6],h=state[7];for(int i=0;i<64;i++){int t=h+(Integer.rotateRight(e,6)^Integer.rotateRight(e,11)^Integer.rotateRight(e,25))+((e&f)^(~e&g))+SHA_K[i]+w[i];int u=(Integer.rotateRight(a,2)^Integer.rotateRight(a,13)^Integer.rotateRight(a,22))+((a&b)^(a&c)^(b&c));h=g;g=f;f=e;e=d+t;d=c;c=b;b=a;a=t+u;}state[0]+=a;state[1]+=b;state[2]+=c;state[3]+=d;state[4]+=e;state[5]+=f;state[6]+=g;state[7]+=h;}
}
