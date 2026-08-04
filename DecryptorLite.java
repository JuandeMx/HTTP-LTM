import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DecryptorLite {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java DecryptorLite <file_path>");
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

        String[] passwords = {
            "SocksHttpSecretKeySecurePreferences2024",
            "909988c9f3714225aebace9546a08a6e7a83ceb66035498e95d23f784bbd8b99#$K@!",
            "fubgf777gf6",
            "freelatam123",
            "jgjua2026"
        };

        int[] keySizes = {16, 32}; // 128 bit (16 bytes) or 256 bit (32 bytes)
        int[] iterationsList = {1000, 2000, 5000};
        String[] digests = {"PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1"};

        for (String pass : passwords) {
            for (int keySize : keySizes) {
                for (int iter : iterationsList) {
                    for (String dig : digests) {
                        try {
                            PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, iter, keySize * 8);
                            SecretKeyFactory skf = SecretKeyFactory.getInstance(dig);
                            byte[] key = skf.generateSecret(spec).getEncoded();
                            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

                            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
                            byte[] decrypted = cipher.doFinal(cipherText);
                            System.out.println("DECRYPT SUCCESS!");
                            System.out.println("Pass: " + pass + " | KeySize: " + keySize + " | Iter: " + iter + " | Dig: " + dig);
                            System.out.println("Content:\n" + new String(decrypted, StandardCharsets.UTF_8));
                            return;
                        } catch (Exception e) {}
                    }
                }
            }
        }
        System.out.println("FAILED");
    }
}
