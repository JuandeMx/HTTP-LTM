package com.slipkprojects.ultrasshservice.config.maze;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class MazePart4 {
    public static String translateToWords(byte[] data, Context context) {
        if (data == null) return null;
        int offset = MazePart1.getOffset(context);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            int shifted = MazePart1.applyOffset(b, offset);
            int scrambled = MazeShuffler.shuffle(shifted);
            String word = MazeDataStore.getWord(scrambled);
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(word);
        }
        return sb.toString();
    }

    public static byte[] translateToBytes(String wordsStr, Context context) {
        if (wordsStr == null || wordsStr.trim().isEmpty()) {
            return new byte[0];
        }
        int offset = MazePart1.getOffset(context);
        String[] words = wordsStr.split(" ");
        byte[] data = new byte[words.length];
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            int scrambled = MazeDataStore.getIndex(word);
            if (scrambled == -1) {
                // Word not found in segment dictionary, corrupted or invalid format
                return null;
            }
            int shifted = MazeShuffler.unshuffle(scrambled);
            int b = MazePart1.removeOffset(shifted, offset);
            data[i] = (byte) b;
        }
        return data;
    }
}
