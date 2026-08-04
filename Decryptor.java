import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.PBEParametersGenerator;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.digests.SHA512Digest;
import org.spongycastle.crypto.params.KeyParameter;

public class Decryptor {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Decryptor <file_path>");
            return;
        }
        String filePath = args[0];
        String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8).trim();
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        String[] parts = content.split("\\.");
        if (parts.length != 3) {
            System.out.println("Malformed data string. Parts count: " + parts.length);
            return;
        }

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] cipherText = Base64.getDecoder().decode(parts[2]);

        System.out.println("Salt length: " + salt.length);
        System.out.println("IV length: " + iv.length);
        System.out.println("Ciphertext length: " + cipherText.length);

        Set<String> passwords = new LinkedHashSet<>();

        // Add hardcoded known keys from native-lib
        passwords.add("909988c9f3714225aebace9546a08a6e7a83ceb66035498e95d23f784bbd8b99#$K@!");
        passwords.add("909988c9f3714225aebeace9546a08a6e7a83ceb66035498e95d23f784bbd8b99#$K@!");
        
        // Add dynamically calculated keys from getSecureMasterKey obfuscated array
        int[] obf = {
            153, 144, 153, 153, 152, 152, 99, 153,
            102, 147, 151, 145, 148, 146, 146, 149,
            97, 101, 98, 97, 99, 101, 153, 149,
            148, 150, 97, 144, 152, 97, 150, 101,
            151, 97, 152, 147, 99, 101, 98, 150,
            150, 144, 147, 149, 148, 153, 152, 101,
            153, 149, 100, 146, 147, 102, 151, 152,
            148, 98, 98, 100, 152, 98, 153, 153,
            35, 36, 75, 64, 33
        };
        
        int[] xorKeys = {0xAA, 0xA0, 0x0A, 0x00};
        String[] charsets = {"UTF-8", "ISO-8859-1", "US-ASCII"};
        for (int xor : xorKeys) {
            byte[] keyBytes = new byte[obf.length];
            for (int i = 0; i < obf.length; i++) {
                keyBytes[i] = (byte)(obf[i] ^ xor);
            }
            passwords.add(new String(keyBytes));
            for (String cs : charsets) {
                try {
                    passwords.add(new String(keyBytes, cs));
                } catch (Exception e) {}
            }
        }
        
        passwords.add("fubgf777gf6");
        passwords.add("wrong_signature_key");
        passwords.add("wrong_signature");
        passwords.add("freelatam123");
        passwords.add("android");
        passwords.add("password");
        passwords.add("jgjua2026");
        passwords.add("com.httpltm.app");
        passwords.add("com.slipkprojects.sockshttp");
        passwords.add("Maximus");
        passwords.add("jgjua_alias");
        passwords.add("release");
        passwords.add("SecureData");
        passwords.add("LT");
        passwords.add("ILT");
        passwords.add("ilt");
        passwords.add("ILTTunnel");
        passwords.add("ILTKey");
        passwords.add("com.ilt.app");
        passwords.add("com.ilt.sockshttp");
        passwords.add("com.ilttunnel.app");
        passwords.add("com.ilttunnel.sockshttp");
        passwords.add("com.slipkprojects.ilt");
        passwords.add("com.slipkprojects.sockshttp.ilt");
        passwords.add("-1177947249");
        passwords.add("-1177922705");
        passwords.add("B9C9F38F");
        passwords.add("b9c9f38f");
        passwords.add("B9CA536F");
        passwords.add("b9ca536f");
        passwords.add("1647EF690F5751F3CEC7B3396BAFACB2EBFCF17D262AF4B1C28236AAA4C50081");
        passwords.add("1647ef690f5751f3cec7b3396bafacb2ebfcf17d262af4b1c28236aaa4c50081");
        passwords.add("16:47:EF:69:0F:57:51:F3:CE:C7:B3:39:6B:AF:AC:B2:EB:FC:F1:7D:26:2A:F4:B1:C2:82:36:AA:A4:C5:00:81");
        passwords.add("16:47:ef:69:0f:57:51:f3:ce:c7:b3:39:6b:af:ac:b2:eb:fc:f1:7d:26:2a:f4:b1:c2:82:36:aa:a4:c5:00:81");
        passwords.add("C5B83250164BF81CB786013AA661E30D46F5D37FC1EB5CABB3CC35D325ABF74A");
        passwords.add("c5b83250164bf81cb786013aa661e30d46f5d37fc1eb5cabb3cc35d325abf74a");
        passwords.add("C5:B8:32:50:16:4B:F8:1C:B7:86:01:3A:A6:61:E3:0D:46:F5:D3:7F:C1:EB:5C:AB:B3:CC:35:D3:25:AB:F7:4A");
        passwords.add("c5:b8:32:50:16:4b:f8:1c:b7:86:01:3a:a6:61:e3:0d:46:f5:d3:7f:c1:eb:5c:ab:b3:cc:35:d3:25:ab:f7:4a");
        passwords.add("1DEFC9D7C44E6DC982DCF6A211FA580112E8CE762A8B0AC5D3415E4220147F3B");
        passwords.add("1defc9d7c44e6dc982dcf6a211fa580112e8ce762a8b0ac5d3415e4220147f3b");
        passwords.add("1D:EF:C9:D7:C4:4E:6D:C9:82:DC:F6:A2:11:FA:58:01:12:E8:CE:76:2A:8B:0A:C5:D3:41:5E:42:20:14:7F:3B");
        passwords.add("1d:ef:c9:d7:c4:4e:6d:c9:82:dc:f6:a2:11:fa:58:01:12:e8:ce:76:2a:8b:0a:c5:d3:41:5e:42:20:14:7f:3b");
        passwords.add("1C9929B785E85DA903BC11097E66646423BD23869B26F8748F90DDD38035000F");
        passwords.add("1c9929b785e85da903bc11097e66646423bd23869b26f8748f90ddd38035000f");
        passwords.add("1C:99:29:B7:85:E8:5D:A9:03:BC:11:09:7E:66:64:64:23:BD:23:86:9B:26:F8:74:8F:90:DD:D3:80:35:00:0F");
        passwords.add("1c:99:29:b7:85:e8:5d:a9:03:bc:11:09:7e:66:64:64:23:bd:23:86:9b:26:f8:74:8f:90:dd:d3:80:35:00:0f");

        // Try extracting certs from all JKS in app directory
        String[] keystores = {
            "d:\\apk\\app\\freelatam_release.jks", "freelatam123", "maximus",
            "d:\\apk\\app\\jgjua_release.jks", "jgjua2026", "jgjua_alias",
            "d:\\apk\\app\\release.keystore", "password", "release",
            "d:\\apk\\release.keystore", "password", "release"
        };
        for (int i = 0; i < keystores.length; i += 3) {
            try {
                File f = new File(keystores[i]);
                if (f.exists()) {
                    KeyStore ks = KeyStore.getInstance("PKCS12");
                    ks.load(new FileInputStream(f), keystores[i+1].toCharArray());
                    Certificate cert = ks.getCertificate(keystores[i+2]);
                    if (cert != null) {
                        addCertDerivations(cert, passwords);
                    }
                }
            } catch (Exception e) {
                System.out.println("Skipping keystore " + keystores[i] + ": " + e.getMessage());
            }
        }

        // Try extracting certs from APK files
        String[] apks = {
            "d:\\apk\\app-debug.apk",
            "d:\\apk\\app-release.apk"
        };
        for (String apk : apks) {
            try {
                File f = new File(apk);
                if (f.exists()) {
                    try (JarFile jar = new JarFile(f)) {
                        JarEntry entry = jar.getJarEntry("AndroidManifest.xml");
                        if (entry != null) {
                            try (InputStream is = jar.getInputStream(entry)) {
                                byte[] buf = new byte[8192];
                                while (is.read(buf) > 0) {}
                            }
                            Certificate[] certs = entry.getCertificates();
                            if (certs != null && certs.length > 0) {
                                addCertDerivations(certs[0], passwords);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Skipping APK " + apk + ": " + e.getMessage());
            }
        }

        System.out.println("Total candidate passwords to check: " + passwords.size());

        int[] keySizes = {128, 192, 196, 256};
        String[] digestAlgs = {"SHA256", "SHA1", "SHA512"};
        int[] iterationsList = {1000, 2000, 5000};

        for (String password : passwords) {
            for (int keySize : keySizes) {
                for (String digestAlg : digestAlgs) {
                    for (int iterations : iterationsList) {
                        try {
                            byte[] keyBytes = pbkdf2(salt, password, keySize, iterations, digestAlg);
                            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

                            for (int mode = 0; mode < 3; mode++) {
                                try {
                                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                                    if (mode == 0) {
                                        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
                                    } else if (mode == 1) {
                                        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(96, iv));
                                    } else {
                                        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
                                    }

                                    byte[] decrypted = cipher.doFinal(cipherText);
                                    String decryptedStr = new String(decrypted, StandardCharsets.UTF_8);

                                    System.out.println("=== DECRYPT SUCCESS ===");
                                    System.out.println("Key: " + password);
                                    System.out.println("Size: " + keySize);
                                    System.out.println("Digest: " + digestAlg);
                                    System.out.println("Iterations: " + iterations);
                                    System.out.println("GCM Mode: " + mode);
                                    System.out.println("Result:\n" + decryptedStr);
                                    return;
                                } catch (Exception e) {
                                    // silent
                                }
                            }
                        } catch (Exception e) {
                            // silent
                        }
                    }
                }
            }
        }
        System.out.println("All combinations failed.");
    }

    private static byte[] pbkdf2(byte[] salt, String password, int keySizeInBits, int iterations, String digestAlg) {
        byte[] passwordBytes = PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password.toCharArray());
        PKCS5S2ParametersGenerator mGenerator;
        if (digestAlg.contains("SHA256")) {
            mGenerator = new PKCS5S2ParametersGenerator(new SHA256Digest());
        } else if (digestAlg.contains("SHA1")) {
            mGenerator = new PKCS5S2ParametersGenerator(new SHA1Digest());
        } else {
            mGenerator = new PKCS5S2ParametersGenerator(new SHA512Digest());
        }
        mGenerator.init(passwordBytes, salt, iterations);
        return ((KeyParameter) mGenerator.generateDerivedParameters(keySizeInBits)).getKey();
    }

    private static void addCertDerivations(Certificate cert, Set<String> candidates) {
        try {
            byte[] bytes = cert.getEncoded();
            
            // 1. Arrays.hashCode
            int h = java.util.Arrays.hashCode(bytes);
            candidates.add(String.valueOf(h));
            candidates.add(Integer.toHexString(h));
            candidates.add(Integer.toHexString(h).toLowerCase());
            candidates.add(Integer.toHexString(h).toUpperCase());
            
            // 2. SHA-256
            byte[] sha256 = hash(bytes, "SHA-256");
            candidates.add(toHex(sha256));
            candidates.add(toHex(sha256).toLowerCase());
            candidates.add(toHex(sha256).toUpperCase());
            candidates.add(toHexColon(sha256));
            candidates.add(toHexColon(sha256).toLowerCase());
            candidates.add(toHexColon(sha256).toUpperCase());
            
            // 3. SHA-1
            byte[] sha1 = hash(bytes, "SHA-1");
            candidates.add(toHex(sha1));
            candidates.add(toHex(sha1).toLowerCase());
            candidates.add(toHex(sha1).toUpperCase());
            candidates.add(toHexColon(sha1));
            candidates.add(toHexColon(sha1).toLowerCase());
            candidates.add(toHexColon(sha1).toUpperCase());

            // 4. MD5
            byte[] md5 = hash(bytes, "MD5");
            candidates.add(toHex(md5));
            candidates.add(toHex(md5).toLowerCase());
            candidates.add(toHex(md5).toUpperCase());
            candidates.add(toHexColon(md5));
            candidates.add(toHexColon(md5).toLowerCase());
            candidates.add(toHexColon(md5).toUpperCase());
            
            // 5. Base64
            candidates.add(Base64.getEncoder().encodeToString(bytes));
            candidates.add(Base64.getEncoder().encodeToString(bytes).trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] hash(byte[] bytes, String alg) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(alg);
        return digest.digest(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String toHexColon(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
