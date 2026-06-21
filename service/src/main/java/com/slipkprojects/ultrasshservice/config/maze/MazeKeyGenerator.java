package com.slipkprojects.ultrasshservice.config.maze;

public class MazeKeyGenerator {
    public static byte[] generateKey(int length) {
        byte[] key = new byte[length];
        for (int i = 0; i < length; i++) {
            // A polynomial formula combined with bitwise operators to generate a pseudo-random looking byte array
            int val = (i * i * 31 + 47 * i + 97) & 0xFF;
            key[i] = (byte) (val ^ 0x7A);
        }
        return key;
    }
}
