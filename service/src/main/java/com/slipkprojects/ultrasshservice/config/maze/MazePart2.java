package com.slipkprojects.ultrasshservice.config.maze;

import android.content.Context;

public class MazePart2 {
    public static byte[] xorBytes(byte[] input, Context context) {
        if (input == null) return null;
        byte[] key = MazePart3.getKeySegment(context, input.length);
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        return output;
    }
}
