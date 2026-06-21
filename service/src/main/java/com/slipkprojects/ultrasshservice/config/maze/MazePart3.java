package com.slipkprojects.ultrasshservice.config.maze;

import android.content.Context;

public class MazePart3 {
    public static byte[] getKeySegment(Context context, int length) {
        byte[] genKey = MazeKeyGenerator.generateKey(8);
        byte[] finalKey = new byte[8];
        for (int i = 0; i < 8; i++) {
            // Further obfuscate the generated key bytes using an arithmetic rotation
            int step = (i * 7 + 13) % 8;
            finalKey[i] = (byte) (genKey[step] ^ (0x5F + i));
        }
        return finalKey;
    }
}
