package com.slipkprojects.ultrasshservice.config.maze;

public class MazeDataStore {
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

    public static String getWord(int index) {
        index = index & 0xFF;
        if (index < 64) {
            return SEGMENT_A[index];
        } else if (index < 128) {
            return SEGMENT_B[index - 64];
        } else if (index < 192) {
            return SEGMENT_C[index - 128];
        } else {
            return SEGMENT_D[index - 192];
        }
    }

    public static int getIndex(String word) {
        if (word == null) return -1;
        for (int i = 0; i < 64; i++) {
            if (word.equals(SEGMENT_A[i])) return i;
            if (word.equals(SEGMENT_B[i])) return i + 64;
            if (word.equals(SEGMENT_C[i])) return i + 128;
            if (word.equals(SEGMENT_D[i])) return i + 192;
        }
        return -1;
    }
}
