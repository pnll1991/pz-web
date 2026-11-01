package port.runtime.text;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * HMAC-SHA1 (RFC 2104) over java.security.MessageDigest, because TeaVM's class library has no javax.crypto.Mac.
 * port.compiler.TotpCompatibility routes de.taimos.totp.TOTP.hmac_sha1 here (the two-factor login packet reaches it).
 */
public final class HmacSha1Bridge {
    private HmacSha1Bridge() { }
    private static final int BLOCK = 64;

    public static byte[] hmac_sha1(byte[] key, byte[] data) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            if (key.length > BLOCK) key = sha1.digest(key);
            byte[] ipad = new byte[BLOCK], opad = new byte[BLOCK];
            for (int i = 0; i < BLOCK; i++) {
                byte k = i < key.length ? key[i] : 0;
                ipad[i] = (byte) (k ^ 0x36); opad[i] = (byte) (k ^ 0x5c);
            }
            sha1.reset(); sha1.update(ipad); sha1.update(data);
            byte[] inner = sha1.digest();
            sha1.reset(); sha1.update(opad); sha1.update(inner);
            return sha1.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }
}
