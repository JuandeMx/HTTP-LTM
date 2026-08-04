import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;

public class PrintLT {
    private static final String MAZE_PREFIX = "sec_maze:";
    private static final int[] XOR_KEY = {0x5A, 0xA5, 0xF0, 0x0F, 0xC3, 0x3C, 0xAA, 0x55};
    private static final String[] DICTIONARY = {
        "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa",
        "quebec", "romeo", "sierra", "tango", "uniform", "victor", "whiskey", "xray",
        "yankee", "zulu", "zero", "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "star", "orbit", "galaxy", "pulse", "cyber",
        "matrix", "quantum", "vector", "nexus", "vertex", "prism", "shadow", "ghost",
        "viper", "falcon", "raven", "phoenix", "titan", "atlas", "hyper", "turbo",
        "super", "ultra", "mega", "giga", "tera", "peta", "blaze", "storm"
    };

    private static String mazeDeobfuscate(String text) {
        if (text == null || !text.startsWith(MAZE_PREFIX)) return text;
        String body = text.substring(MAZE_PREFIX.length());
        try {
            byte[] raw = Base64.getDecoder().decode(body);
            byte[] xor = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) {
                xor[i] = (byte) (raw[i] ^ XOR_KEY[i % XOR_KEY.length]);
            }
            String decoded = new String(xor, StandardCharsets.UTF_8);
            String[] words = decoded.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                int idx = -1;
                for (int i = 0; i < DICTIONARY.length; i++) {
                    if (DICTIONARY[i].equals(w)) {
                        idx = i;
                        break;
                    }
                }
                if (idx != -1) {
                    sb.append((char) idx);
                } else if (w.matches("\\d+")) {
                    sb.append((char) Integer.parseInt(w));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return text;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) return;
        File dir = new File(args[0]);
        File[] files = dir.listFiles((d, name) -> name.toUpperCase().endsWith(".LT"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));

        String pass = "909988c9f3714225aebace9546a08a6e7a83ceb66035498e95d23f784bbd8b99#$K@!";

        for (File f : files) {
            System.out.println("==========================================");
            System.out.println("FILE: " + f.getName());
            System.out.println("==========================================");
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (content.startsWith("\uFEFF")) content = content.substring(1);

            String[] parts = content.split("\\.");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);

            PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, 1000, 128);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] key = skf.generateSecret(spec).getEncoded();
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] decrypted = cipher.doFinal(cipherText);
            String xmlStr = new String(decrypted, StandardCharsets.UTF_8);

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xmlStr.getBytes(StandardCharsets.UTF_8)));
            NodeList entries = doc.getElementsByTagName("entry");

            for (int i = 0; i < entries.getLength(); i++) {
                Element el = (Element) entries.item(i);
                String k = el.getAttribute("key");
                String rawVal = el.getTextContent();
                String val = mazeDeobfuscate(rawVal);
                if (k.equals("sshServer") || k.equals("sshPort") || k.equals("customSNI") || k.equals("proxyPayload") || k.equals("use_ssl") || k.equals("use_payload") || k.equals("tunnelType")) {
                    System.out.println("  " + k + " = " + val);
                }
            }
            System.out.println();
        }
    }
}
