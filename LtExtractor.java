import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class LtExtractor {
    private static final String SECURE_KEY = "fubgf777gf6";
    private static final String MAZE_PREFIX = "sec_maze:";
    private static final byte[] XOR_KEY = new byte[]{(byte)0x5A, (byte)0xA5, (byte)0xF0, (byte)0xC3, (byte)0x3C, (byte)0xAA, (byte)0x55};
    private static final String[] DICTIONARY = new String[]{
        "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa",
        "quebec", "romeo", "sierra", "tango", "uniform", "victor", "whiskey", "xray",
        "yankee", "zulu", "zero", "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "star", "orbit", "galaxy", "pulse", "cyber",
        "matrix", "quantum", "vector", "nexus", "vertex", "prism", "shadow", "ghost",
        "viper", "falcon", "raven", "phoenix", "titan", "atlas", "hyper", "turbo",
        "super", "ultra", "mega", "giga", "tera", "peta", "blaze", "storm",
        "thunder", "frost", "inferno", "apex", "zenith", "nadir", "vortex", "cosmo",
        "astro", "lunar", "stellar", "nebula", "comet", "meteor", "pulsar",
        "quasar", "aurora", "corona", "eclipse", "horizon", "equinox", "solstice", "beacon",
        "signal", "beacon", "radar", "sonar", "laser", "photon", "proton", "electron",
        "neutron", "quark", "lepton", "boson", "hadron", "muon", "tau", "gluon",
        "graviton", "tachy", "plasma", "magma", "lava", "crystal", "gem", "diamond",
        "ruby", "sapphire", "emerald", "topaz", "opal", "amber", "pearl", "jade",
        "onyx", "quartz", "flint", "slate", "granite", "marble", "basalt", "pumice",
        "obsidian", "silver", "gold", "copper", "iron", "steel", "bronze", "brass",
        "cobalt", "nickel", "chrome", "titanium", "platinum", "zinc", "tin", "lead",
        "bismuth", "carbon", "silicon", "sulfur", "sodium", "argon", "krypton", "xenon",
        "neon", "helium", "lithium", "boron", "radon", "ferrum", "aurum", "argentum",
        "plumbum", "cuprum", "stannum", "hydra", "draco", "orion", "cygnus", "lyra",
        "pegasus", "taurus", "aries", "gemini", "cancer", "leo", "virgo", "libra",
        "scorpio", "sagittar", "capricorn", "aquarius", "pisces", "phoenix", "centaur", "pegasus",
        "griffin", "chimera", "sphinx", "kraken", "golem", "titan", "giant", "cyclops", "gorgon",
        "siren", "harpy", "minotaur", "valkyrie", "banshee", "specter", "wraith", "phantom",
        "shadow", "shade", "spirit", "demon", "angel", "seraph", "cherub", "deity"
    };

    private static Map<String, Integer> DICT_MAP = new HashMap<>();
    static {
        for (int i = 0; i < DICTIONARY.length; i++) {
            DICT_MAP.put(DICTIONARY[i], i);
        }
    }

    public static void main(String[] args) throws Exception {
        File folder = new File("d:/apk/plantillas");
        File[] files = folder.listFiles((dir, name) -> name.toUpperCase().endsWith(".LT"));
        if (files == null || files.length == 0) {
            System.out.println("No se encontraron archivos .LT en d:/apk/plantillas");
            return;
        }

        StringBuilder outTxt = new StringBuilder();
        outTxt.append("========================================================================\n");
        outTxt.append("MÉTODOS DESCIFRADOS DESDE PLANTILLAS .LT (FORMATO PARA PANEL MAESTRO)\n");
        outTxt.append("========================================================================\n\n");

        for (File f : files) {
            try {
                String rawStr = new String(readFileToBytes(f), StandardCharsets.UTF_8).trim();
                if (rawStr.startsWith("\uFEFF")) {
                    rawStr = rawStr.substring(1);
                }

                String[] parts = rawStr.split("\\.");
                byte[] p0 = Base64.getDecoder().decode(parts[0].trim());
                byte[] p1 = Base64.getDecoder().decode(parts[1].trim());
                byte[] cipherText = Base64.getDecoder().decode(parts[2].trim());

                byte[] xmlBytes = decryptWithParams(p0, p1, cipherText, "909988c9f3714225aebace9546a08a6e7a83ceb66035498e95d23f784bbd8b99#$K@!");

                Properties props = new Properties();
                props.loadFromXML(new ByteArrayInputStream(xmlBytes));

                String name = f.getName().replace(".LT", "").replace(".lt", "");
                String host = deobfuscate(props.getProperty("sshServer"));
                String port = deobfuscate(props.getProperty("sshPort"));
                String user = deobfuscate(props.getProperty("sshUser"));
                String pass = deobfuscate(props.getProperty("sshPass"));
                String sni = deobfuscate(props.getProperty("customSNI"));
                String payload = deobfuscate(props.getProperty("proxyPayload"));
                String proxyHost = deobfuscate(props.getProperty("proxyRemoto"));
                String proxyPort = deobfuscate(props.getProperty("proxyRemotoPorta"));

                if (port == null || port.isEmpty()) port = "22";

                outTxt.append("========================================\n");
                outTxt.append("MÉTODO: ").append(name).append("\n");
                outTxt.append("========================================\n");
                outTxt.append("NOMBRE: ").append(name).append("\n");
                outTxt.append("SSH_HOST: ").append(host != null ? host : "").append("\n");
                outTxt.append("SSH_PORT: ").append(port).append("\n");
                outTxt.append("SSH_USER: ").append(user != null ? user : "").append("\n");
                outTxt.append("SSH_PASS: ").append(pass != null ? pass : "").append("\n");
                outTxt.append("SNI: ").append(sni != null ? sni : "").append("\n");
                outTxt.append("PROXY_HOST: ").append(proxyHost != null ? proxyHost : "").append("\n");
                outTxt.append("PROXY_PORT: ").append(proxyPort != null ? proxyPort : "").append("\n");
                outTxt.append("PAYLOAD: ").append(payload != null ? payload.replace("\r\n", "[crlf]").replace("\n", "[crlf]") : "").append("\n\n");

            } catch (Exception e) {
                outTxt.append("ERROR AL DESCIFRAR ").append(f.getName()).append(": ").append(e.getMessage()).append("\n\n");
            }
        }

        File resultFile = new File("d:/apk/metodos_panel_maestro.txt");
        try (FileOutputStream fos = new FileOutputStream(resultFile)) {
            fos.write(outTxt.toString().getBytes(StandardCharsets.UTF_8));
        }

        System.out.println("EXITO: Archivo generado en " + resultFile.getAbsolutePath());
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

    private static byte[] decryptWithParams(byte[] salt, byte[] iv, byte[] cipherText, String secretKey) throws Exception {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "BC");
        PBEKeySpec spec = new PBEKeySpec(secretKey.toCharArray(), salt, 1000, 128);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        return cipher.doFinal(cipherText);
    }

    private static final String[] SEGMENT_A = {
        "alpha", "anchor", "apple", "apron", "arena", "armor", "arrow", "artist", "ash", "aspect",
        "atlas", "atom", "attic", "audio", "audit", "aura", "autumn", "avatar", "award", "awful",
        "bacon", "badge", "baker", "ballot", "banana", "banker", "banner", "barber", "barley", "barrel",
        "basket", "baton", "beacon", "beetle", "belfry", "bellows", "bench", "berry", "bicycle", "bison",
        "bitter", "blanket", "blazer", "blossom", "bonfire", "bonnet", "border", "bottle", "boulder", "bounce",
        "bracket", "bramble", "branch", "brass", "breeze", "brewer", "bridal", "bridge", "bristle", "bronze",
        "bubble", "bucket", "budget", "buffalo"
    };
    private static final String[] SEGMENT_B = {
        "bullet", "bundle", "burden", "bureau", "butter", "cabin", "cable", "cactus", "caesar", "camera",
        "canvas", "canyon", "captain", "caramel", "carbon", "cardiac", "cargo", "carpet", "carrot", "carton",
        "castle", "cater", "cattle", "cavern", "cavity", "cedar", "celery", "cellar", "census", "center",
        "cereal", "chalet", "chalk", "chamber", "channel", "chapel", "chapter", "chariot", "charter", "cheese",
        "cherry", "chestnut", "chimney", "chisel", "chorus", "cider", "cigar", "cinema", "circle", "circus",
        "cistern", "citrus", "civic", "clamor", "claret", "classic", "clover", "cobalt", "cobra", "cobweb",
        "cocoa", "coffee", "coffin", "collar"
    };
    private static final String[] SEGMENT_C = {
        "college", "colony", "comet", "compass", "concert", "cookie", "copper", "coral", "corner", "cornet",
        "cosmos", "cotton", "county", "cougar", "coyote", "cradle", "crater", "crayon", "cricket", "crimson",
        "critic", "crystal", "cubit", "cuckoo", "cuddle", "curfew", "cushion", "cutter", "cyclone", "cynic",
        "dagger", "dairy", "daisy", "damage", "dancer", "danger", "dapper", "darling", "dealer", "debate",
        "debris", "decade", "decimal", "decree", "degree", "deluge", "denim", "dental", "depot", "depth",
        "derby", "desert", "design", "desk", "detail", "detect", "device", "devil", "dialog", "diamond",
        "diary", "diesel", "diet", "differ"
    };
    private static final String[] SEGMENT_D = {
        "digest", "digital", "dilemma", "dinner", "diode", "diploma", "direct", "dirt", "disaster", "disc",
        "discus", "disease", "dish", "dislike", "ditch", "diver", "divide", "divine", "dock", "doctor",
        "dogma", "dollar", "dolphin", "domain", "dome", "donor", "donut", "door", "dose", "double",
        "doubt", "dough", "dragon", "drain", "drama", "drawer", "dream", "dress", "drift", "drill",
        "drink", "drip", "drive", "drone", "drop", "drown", "drum", "dryer", "duck", "duct",
        "duel", "duet", "duke", "dull", "duly", "dummy", "dump", "dune", "dusk", "dust",
        "duty", "dwarf", "dwell", "dying"
    };

    private static int getWordIndex(String word) {
        if (word == null) return -1;
        for (int i = 0; i < 64; i++) {
            if (word.equals(SEGMENT_A[i])) return i;
            if (word.equals(SEGMENT_B[i])) return i + 64;
            if (word.equals(SEGMENT_C[i])) return i + 128;
            if (word.equals(SEGMENT_D[i])) return i + 192;
        }
        return -1;
    }

    private static int unshuffle(int shuffledIndex) {
        int inv = 1;
        for (int i = 1; i < 256; i += 2) {
            if ((157 * i) % 256 == 1) {
                inv = i;
                break;
            }
        }
        int val = (shuffledIndex & 0xFF) - 83;
        if (val < 0) {
            val += 256 * ((Math.abs(val) / 256) + 1);
        }
        return (val * inv) & 0xFF;
    }

    private static final byte[] OBFUSCATION_KEY = new byte[]{0x3A, 0x7F, (byte)0x1C, 0x5D, 0x6E, 0x2B, 0x4C, 0x07};

    private static byte[] getMazeKey() {
        byte[] genKey = new byte[8];
        for (int i = 0; i < 8; i++) {
            int val = (i * i * 31 + 47 * i + 97) & 0xFF;
            genKey[i] = (byte) (val ^ 0x7A);
        }
        byte[] finalKey = new byte[8];
        for (int i = 0; i < 8; i++) {
            int step = (i * 7 + 13) % 8;
            finalKey[i] = (byte) (genKey[step] ^ (0x5F + i));
        }
        return finalKey;
    }    private static final int[] NATIVE_XOR = {0x5A, 0xA5, 0xF0, 0x0F, 0xC3, 0x3C, 0xAA, 0x55};

    private static String deobfuscate(String val) {
        if (val == null) return "";
        if (val.isEmpty()) return "";

        if (val.startsWith("secure")) {
            String[] words = val.trim().split("\\s+");
            if (words.length <= 1) return "";
            byte[] bytes = new byte[words.length - 1];
            for (int i = 1; i < words.length; i++) {
                int idx = getWordIndex(words[i]);
                if (idx == -1) return val;
                bytes[i - 1] = (byte) (idx ^ (OBFUSCATION_KEY[(i - 1) % OBFUSCATION_KEY.length] & 0xFF));
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        if (val.startsWith(MAZE_PREFIX)) {
            String body = val.substring(MAZE_PREFIX.length()).trim();
            // 1. Intentar descifrado Base64 + XOR (HTTP Custom / SocksHTTP standard)
            try {
                byte[] raw = Base64.getDecoder().decode(body);
                byte[] xor = new byte[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    xor[i] = (byte) (raw[i] ^ NATIVE_XOR[i % NATIVE_XOR.length]);
                }
                String decoded = new String(xor, StandardCharsets.UTF_8);
                String[] words = decoded.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    int idx = getWordIndex(w);
                    if (idx != -1) {
                        sb.append((char) idx);
                    } else if (w.matches("\\d+")) {
                        sb.append((char) Integer.parseInt(w));
                    }
                }
                String res = sb.toString();
                if (!res.isEmpty()) return res;
            } catch (Exception ignored) {}

            // 2. Si es formato de palabras espacio-separadas sec_maze:word1 word2...
            String[] words = body.split("\\s+");
            byte[] obfuscatedBytes = new byte[words.length];
            int offset = 13 + ("com.httpltm.app".length() % 7);
            boolean ok = true;
            for (int i = 0; i < words.length; i++) {
                int scrambled = getWordIndex(words[i]);
                if (scrambled == -1) {
                    ok = false;
                    break;
                }
                int shifted = unshuffle(scrambled);
                int b = (shifted - offset) & 0xFF;
                if (b < 0) b += 256;
                obfuscatedBytes[i] = (byte) b;
            }
            if (ok) {
                byte[] key = getMazeKey();
                byte[] originalBytes = new byte[obfuscatedBytes.length];
                for (int i = 0; i < obfuscatedBytes.length; i++) {
                    originalBytes[i] = (byte) (obfuscatedBytes[i] ^ key[i % key.length]);
                }
                return new String(originalBytes, StandardCharsets.UTF_8);
            }
        }

        return val;
    }
}
