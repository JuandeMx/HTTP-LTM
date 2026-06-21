package com.slipkprojects.ultrasshservice.config.maze;

import android.content.Context;
import java.io.UnsupportedEncodingException;

public class MazeDecrypter {
    public static final String PREFIX = "sec_maze:";

    public static String encrypt(String input, Context context) {
        if (input == null) return null;
        if (input.isEmpty()) return "";

        try {
            byte[] inputBytes = input.getBytes("UTF-8");
            byte[] obfuscatedBytes = MazePart2.xorBytes(inputBytes, context);
            String words = MazePart4.translateToWords(obfuscatedBytes, context);
            return PREFIX + words;
        } catch (UnsupportedEncodingException e) {
            return input;
        }
    }

    public static String decrypt(String input, Context context) {
        if (input == null) return null;
        if (input.isEmpty()) return "";

        if (!input.startsWith(PREFIX)) {
            return input;
        }

        try {
            String wordsStr = input.substring(PREFIX.length());
            byte[] obfuscatedBytes = MazePart4.translateToBytes(wordsStr, context);
            if (obfuscatedBytes == null) {
                // If it fails parsing or is corrupted, fallback gracefully to returning input
                return input;
            }
            byte[] originalBytes = MazePart2.xorBytes(obfuscatedBytes, context);
            return new String(originalBytes, "UTF-8");
        } catch (Exception e) {
            return input;
        }
    }
}
