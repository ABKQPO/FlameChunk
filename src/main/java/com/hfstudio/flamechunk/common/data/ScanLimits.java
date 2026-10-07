package com.hfstudio.flamechunk.common.data;

public class ScanLimits {

    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 86400;

    public static boolean isValidDuration(int seconds) {
        return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS;
    }
}
