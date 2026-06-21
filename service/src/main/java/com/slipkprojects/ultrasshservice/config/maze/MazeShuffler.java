package com.slipkprojects.ultrasshservice.config.maze;

public class MazeShuffler {
    private static final int MULTIPLIER = 157; // coprime to 256
    private static final int ADDEND = 83;
    private static final int INVERSE_MULTIPLIER;

    static {
        // Dynamically compute the modular inverse of MULTIPLIER modulo 256
        int inv = 1;
        for (int i = 1; i < 256; i += 2) {
            if ((MULTIPLIER * i) % 256 == 1) {
                inv = i;
                break;
            }
        }
        INVERSE_MULTIPLIER = inv;
    }

    public static int shuffle(int index) {
        return ((index & 0xFF) * MULTIPLIER + ADDEND) & 0xFF;
    }

    public static int unshuffle(int shuffledIndex) {
        int val = (shuffledIndex & 0xFF) - ADDEND;
        if (val < 0) {
            val += 256 * ((Math.abs(val) / 256) + 1);
        }
        return (val * INVERSE_MULTIPLIER) & 0xFF;
    }
}
