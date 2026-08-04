import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class LtFullDecoder {
    public static void main(String[] args) throws Exception {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        File folder = new File("d:/apk/plantillas");
        File[] files = folder.listFiles((dir, name) -> name.toUpperCase().endsWith(".LT"));
        if (files == null || files.length == 0) {
            System.out.println("No se encontraron archivos .LT en d:/apk/plantillas");
            return;
        }

        for (File f : files) {
            System.out.println("========================================");
            System.out.println("PLANTILLA: " + f.getName());
            System.out.println("========================================");
            try {
                String rawStr = new String(readFileToBytes(f), StandardCharsets.UTF_8).trim();
                if (rawStr.startsWith("\uFEFF")) {
                    rawStr = rawStr.substring(1);
                }

                String[] parts = rawStr.split("\\.");
                byte[] salt = Base64.getDecoder().decode(parts[0].trim());
                byte[] iv = Base64.getDecoder().decode(parts[1].trim());
                byte[] cipherText = Base64.getDecoder().decode(parts[2].trim());

                // Keys candidates from C++ native-lib getSecureConfigKey
                String[] nativeKeys = new String[]{
                    "909988c9f3714225aebace9546a08a6e7a83ceb66035498e95d23f784bbd8b99#$K@!",
                    "wrong_signature_key",
                    "fubgf777gf6"
                };

                byte[] xmlBytes = null;
                for (String key : nativeKeys) {
                    try {
                        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "BC");
                        PBEKeySpec spec = new PBEKeySpec(key.toCharArray(), salt, 1000, 128);
                        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
                        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
                        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
                        xmlBytes = cipher.doFinal(cipherText);
                        System.out.println("--> DESCIFRADO EXITOSO CON CLAVE: " + key);
                        break;
                    } catch (Exception ignored) {}
                }

                if (xmlBytes == null) {
                    System.out.println("ERROR: No se pudo descifrar con ninguna clave nativa.");
                    continue;
                }

                Properties props = new Properties();
                props.loadFromXML(new ByteArrayInputStream(xmlBytes));

                for (String k : props.stringPropertyNames()) {
                    String val = props.getProperty(k);
                    System.out.println("KEY: " + k);
                    System.out.println("RAW_VAL: " + val);
                }

            } catch (Exception e) {
                System.out.println("ERROR EN ARCHIVO " + f.getName() + ": " + e.getMessage());
            }
            System.out.println();
        }
    }

    private static byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = fis.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        }
    }
}
