import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;

public class GetHashCode {
    public static void main(String[] args) {
        tryKeystore("d:\\apk\\app\\freelatam_release.jks", "freelatam123", "maximus");
        tryKeystore("d:\\apk\\app\\jgjua_release.jks", "jgjua2026", "jgjua_alias");
        tryKeystore("d:\\apk\\app\\release.keystore", "password", "release");
        tryKeystore("d:\\apk\\release.keystore", "password", "release");
        tryKeystore("C:\\Users\\JGJua\\.android\\debug.keystore", "android", "androiddebugkey");
    }

    private static void tryKeystore(String path, String password, String alias) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new FileInputStream(path), password.toCharArray());
            Certificate cert = ks.getCertificate(alias);
            if (cert == null) {
                System.out.println("Alias '" + alias + "' not found in " + path);
                return;
            }
            byte[] certBytes = cert.getEncoded();
            int hashCode = Arrays.hashCode(certBytes);
            System.out.println("Keystore: " + path);
            System.out.println("  HASHCODE_INT: " + hashCode);
            System.out.println("  HASHCODE_STR: " + String.valueOf(hashCode));
            System.out.println("  HASHCODE_HEX: " + Integer.toHexString(hashCode));
            System.out.println("  HASHCODE_HEX_UPPER: " + Integer.toHexString(hashCode).toUpperCase());
        } catch (Exception e) {
            System.out.println("Failed to read " + path + ": " + e.getMessage());
        }
    }
}
