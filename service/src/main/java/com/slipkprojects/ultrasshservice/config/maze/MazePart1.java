package com.slipkprojects.ultrasshservice.config.maze;

import android.content.Context;

public class MazePart1 {
    public static int getOffset(Context context) {
        // Obtains a dynamic offset using system characteristics or package name length
        int base = 13;
        if (context != null) {
            String pkg = context.getPackageName();
            if (pkg != null) {
                base += (pkg.length() % 7);
            }
        }
        return base;
    }

    public static int applyOffset(int value, int offset) {
        return (value + offset) & 0xFF;
    }

    public static int removeOffset(int value, int offset) {
        int val = (value - offset) & 0xFF;
        return val < 0 ? val + 256 : val;
    }
}
