#include <jni.h>
#include <string>
#include <string.h>
#include <stdio.h>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_slipkprojects_ultrasshservice_util_securepreferences_SecurePreferences_getNativeKey(
        JNIEnv* env,
        jclass /* clazz */) {
    char key[] = {'f', 'u', 'b', 'g', 'f', '7', '7', '7', 'g', 'f', '6', '\0'};
    return env->NewStringUTF(key);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_slipkprojects_ultrasshservice_util_securepreferences_SecurePreferences_getExpectedSignature(
        JNIEnv* env,
        jclass /* clazz */) {
    return env->NewStringUTF("1647EF690F5751F3CEC7B3396BAFACB2EBFCF17D262AF4B1C28236AAA4C50081");
}

// Helper to check the app's signature hash
bool verifyAppSignature(JNIEnv* env, jobject context) {
    const char* allowedHashes[] = {
        "1647EF690F5751F3CEC7B3396BAFACB2EBFCF17D262AF4B1C28236AAA4C50081", // Original production
        "C5B83250164BF81CB786013AA661E30D46F5D37FC1EB5CABB3CC35D325ABF74A", // freelatam_release.jks
        "1DEFC9D7C44E6DC982DCF6A211FA580112E8CE762A8B0AC5D3415E4220147F3B", // Local debug keystore
        "1C9929B785E85DA903BC11097E66646423BD23869B26F8748F90DDD38035000F"  // release.keystore
    };
    int allowedCount = sizeof(allowedHashes) / sizeof(allowedHashes[0]);

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);

    jmethodID getPackageNameMethod = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring packageName = (jstring)env->CallObjectMethod(context, getPackageNameMethod);

    jclass packageManagerClass = env->GetObjectClass(packageManager);
    jmethodID getPackageInfoMethod = env->GetMethodID(packageManagerClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");

    // PackageManager.GET_SIGNATURES = 64
    jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 64);

    jclass packageInfoClass = env->GetObjectClass(packageInfo);
    jfieldID signaturesField = env->GetFieldID(packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    jobjectArray signaturesArray = (jobjectArray)env->GetObjectField(packageInfo, signaturesField);

    if (signaturesArray == nullptr || env->GetArrayLength(signaturesArray) == 0) {
        return false;
    }

    jobject signature = env->GetObjectArrayElement(signaturesArray, 0);

    jclass signatureClass = env->GetObjectClass(signature);
    jmethodID toByteArrayMethod = env->GetMethodID(signatureClass, "toByteArray", "()[B");
    jbyteArray signatureBytes = (jbyteArray)env->CallObjectMethod(signature, toByteArrayMethod);

    jclass messageDigestClass = env->FindClass("java/security/MessageDigest");
    jmethodID getInstanceMethod = env->GetStaticMethodID(messageDigestClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jobject messageDigest = env->CallStaticObjectMethod(messageDigestClass, getInstanceMethod, env->NewStringUTF("SHA-256"));

    jmethodID digestMethod = env->GetMethodID(messageDigestClass, "digest", "([B)[B");
    jbyteArray digestBytes = (jbyteArray)env->CallObjectMethod(messageDigest, digestMethod, signatureBytes);

    jsize len = env->GetArrayLength(digestBytes);
    jbyte* bytes = env->GetByteArrayElements(digestBytes, nullptr);

    char hexHash[65];
    for (int i = 0; i < len; i++) {
        sprintf(&hexHash[i * 2], "%02X", (unsigned char)bytes[i]);
    }
    hexHash[64] = '\0';

    env->ReleaseByteArrayElements(digestBytes, bytes, JNI_ABORT);

    for (int i = 0; i < allowedCount; i++) {
        if (strcmp(hexHash, allowedHashes[i]) == 0) {
            return true;
        }
    }
    return false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_slipkprojects_ultrasshservice_config_ConfigParser_getSecureConfigKey(
        JNIEnv* env,
        jclass /* clazz */,
        jobject context) {
    char key[] = {'9', '0', '9', '9', '8', '8', 'c', '9', 'f', '3', '7', '1', '4', '2', '2', '5', 'a', 'e', 'b', 'a', 'c', 'e', '9', '5', '4', '6', 'a', '0', '8', 'a', '6', 'e', '7', 'a', '8', '3', 'c', 'e', 'b', '6', '6', '0', '3', '5', '4', '9', '8', 'e', '9', '5', 'd', '2', '3', 'f', '7', '8', '4', 'b', 'b', 'd', '8', 'b', '9', '9', '#', '$', 'K', '@', '!', '\0'};
    return env->NewStringUTF(key);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_slipkprojects_ultrasshservice_config_ConfigParser_getObfuscationKey(
        JNIEnv* env,
        jclass /* clazz */,
        jobject context) {
    jbyteArray arr = env->NewByteArray(8);
    jbyte key[] = {0x3A, 0x7F, 0x1C, 0x5D, 0x6E, 0x2B, 0x4C, 0x07};
    env->SetByteArrayRegion(arr, 0, 8, key);
    return arr;
}

const char* dictionary[256] = {
    "alpha", "anchor", "apple", "apron", "arena", "armor", "arrow", "artist", "ash", "aspect",
    "atlas", "atom", "attic", "audio", "audit", "aura", "autumn", "avatar", "award", "awful",
    "bacon", "badge", "baker", "ballot", "banana", "banker", "banner", "barber", "barley", "barrel",
    "basket", "baton", "beacon", "beetle", "belfry", "bellows", "bench", "berry", "bicycle", "bison",
    "bitter", "blanket", "blazer", "blossom", "bonfire", "bonnet", "border", "bottle", "boulder", "bounce",
    "bracket", "bramble", "branch", "brass", "breeze", "brewer", "bridal", "bridge", "bristle", "bronze",
    "bubble", "bucket", "budget", "buffalo", "bullet", "bundle", "burden", "bureau", "butter", "cabin",
    "cable", "cactus", "caesar", "camera", "canvas", "canyon", "captain", "caramel", "carbon", "cardiac",
    "cargo", "carpet", "carrot", "carton", "castle", "cater", "cattle", "cavern", "cavity", "cedar",
    "celery", "cellar", "census", "center", "cereal", "chalet", "chalk", "chamber", "channel", "chapel",
    "chapter", "chariot", "charter", "cheese", "cherry", "chestnut", "chimney", "chisel", "chorus", "cider",
    "cigar", "cinema", "circle", "circus", "cistern", "citrus", "civic", "clamor", "claret", "classic",
    "clover", "cobalt", "cobra", "cobweb", "cocoa", "coffee", "coffin", "collar", "college", "colony",
    "comet", "compass", "concert", "cookie", "copper", "coral", "corner", "cornet", "cosmos", "cotton",
    "county", "cougar", "coyote", "cradle", "crater", "crayon", "cricket", "crimson", "critic", "crystal",
    "cubit", "cuckoo", "cuddle", "curfew", "cushion", "cutter", "cyclone", "cynic", "dagger", "dairy",
    "daisy", "damage", "dancer", "danger", "dapper", "darling", "dealer", "debate", "debris", "decade",
    "decimal", "decree", "degree", "deluge", "denim", "dental", "depot", "depth", "derby", "desert",
    "design", "desk", "detail", "detect", "device", "devil", "dialog", "diamond", "diary", "diesel",
    "diet", "differ", "digest", "digital", "dilemma", "dinner", "diode", "diploma", "direct", "dirt",
    "disaster", "disc", "discus", "disease", "dish", "dislike", "ditch", "diver", "divide", "divine",
    "dock", "doctor", "dogma", "dollar", "dolphin", "domain", "dome", "donor", "donut", "door",
    "dose", "double", "doubt", "dough", "dragon", "drain", "drama", "drawer", "dream", "dress",
    "drift", "drill", "drink", "drip", "drive", "drone", "drop", "drown", "drum", "dryer",
    "duck", "duct", "duel", "duet", "duke", "dull", "duly", "dummy", "dump", "dune",
    "dusk", "dust", "duty", "dwarf", "dwell", "dying"
};

extern "C" JNIEXPORT jstring JNICALL
Java_com_slipkprojects_ultrasshservice_config_ConfigParser_translate(
        JNIEnv* env,
        jclass /* clazz */,
        jstring input,
        jobject context) {
    if (input == nullptr) return nullptr;

    const char* inChars = env->GetStringUTFChars(input, nullptr);
    std::string inStr(inChars);
    env->ReleaseStringUTFChars(input, inChars);

    if (inStr.empty()) {
        return env->NewStringUTF("");
    }

    if (!verifyAppSignature(env, context)) {
        return env->NewStringUTF("secure apple banana cherry donut");
    }

    jbyte key[] = {0x3A, 0x7F, 0x1C, 0x5D, 0x6E, 0x2B, 0x4C, 0x07};
    int keyLen = sizeof(key);

    std::string result = "secure";

    for (size_t i = 0; i < inStr.length(); i++) {
        unsigned char b = (unsigned char)inStr[i];
        unsigned char obf = b ^ (unsigned char)key[i % keyLen];
        result += " ";
        result += dictionary[obf];
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_slipkprojects_ultrasshservice_config_ConfigParser_detranslate(
        JNIEnv* env,
        jclass /* clazz */,
        jstring input,
        jobject context) {
    if (input == nullptr) return nullptr;

    const char* inChars = env->GetStringUTFChars(input, nullptr);
    std::string inStr(inChars);
    env->ReleaseStringUTFChars(input, inChars);

    if (inStr.empty()) {
        return env->NewStringUTF("");
    }

    if (inStr.rfind("secure", 0) != 0) {
        return env->NewStringUTF(inStr.c_str());
    }

    if (!verifyAppSignature(env, context)) {
        return env->NewStringUTF("wrong_signature");
    }

    std::vector<std::string> words;
    size_t start = 0;
    size_t end = inStr.find(' ');
    while (end != std::string::npos) {
        words.push_back(inStr.substr(start, end - start));
        start = end + 1;
        end = inStr.find(' ', start);
    }
    words.push_back(inStr.substr(start));

    if (words.size() <= 1) {
        return env->NewStringUTF("");
    }

    jbyte key[] = {0x3A, 0x7F, 0x1C, 0x5D, 0x6E, 0x2B, 0x4C, 0x07};
    int keyLen = sizeof(key);

    std::string decrypted = "";
    for (size_t i = 1; i < words.size(); i++) {
        std::string w = words[i];
        int index = -1;
        for (int j = 0; j < 256; j++) {
            if (w == dictionary[j]) {
                index = j;
                break;
            }
        }
        if (index == -1) {
            return env->NewStringUTF(inStr.c_str());
        }
        unsigned char obf = (unsigned char)index;
        unsigned char orig = obf ^ (unsigned char)key[(i - 1) % keyLen];
        decrypted += (char)orig;
    }

    return env->NewStringUTF(decrypted.c_str());
}
