import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.security.cert.Certificate;
import java.util.Arrays;

public class GetApkCert {
    public static void main(String[] args) throws Exception {
        dumpApk("d:\\apk\\Http LTM_2.1.apk");
        dumpApk("d:\\apk\\app-release.apk");
    }
    
    private static void dumpApk(String path) throws Exception {
        System.out.println("APK: " + path);
        try (JarFile jar = new JarFile(path)) {
            JarEntry entry = jar.getJarEntry("AndroidManifest.xml");
            if (entry == null) {
                System.out.println("  No AndroidManifest.xml found");
                return;
            }
            try (InputStream is = jar.getInputStream(entry)) {
                byte[] buf = new byte[8192];
                while (is.read(buf) > 0) {}
            }
            Certificate[] certs = entry.getCertificates();
            if (certs == null || certs.length == 0) {
                System.out.println("  No certificates found");
                return;
            }
            Certificate cert = certs[0];
            byte[] certBytes = cert.getEncoded();
            int hashCode = Arrays.hashCode(certBytes);
            System.out.println("  HASHCODE_INT: " + hashCode);
            System.out.println("  HASHCODE_STR: " + String.valueOf(hashCode));
            System.out.println("  HASHCODE_HEX: " + Integer.toHexString(hashCode));
            System.out.println("  HASHCODE_HEX_UPPER: " + Integer.toHexString(hashCode).toUpperCase());
        } catch (Exception e) {
            System.out.println("  Failed: " + e.getMessage());
        }
    }
}
