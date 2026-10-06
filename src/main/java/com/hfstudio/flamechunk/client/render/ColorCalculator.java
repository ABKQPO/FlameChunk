package com.hfstudio.flamechunk.client.render;

public class ColorCalculator {

    public int colorForMspt(float mspt, float budgetMspt) {
        float ratio = normalize(mspt, budgetMspt);
        if (ratio < 0.5F) {
            return blend(0x35B779, 0xFDE047, ratio * 2.0F);
        }
        return blend(0xFDE047, 0xEF4444, (ratio - 0.5F) * 2.0F);
    }

    public float normalize(float mspt, float budgetMspt) {
        if (Float.isNaN(mspt) || mspt <= 0.0F || Float.isNaN(budgetMspt) || budgetMspt <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, mspt / budgetMspt);
    }

    private int blend(int first, int second, float ratio) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        int red = interpolate((first >> 16) & 0xff, (second >> 16) & 0xff, clamped);
        int green = interpolate((first >> 8) & 0xff, (second >> 8) & 0xff, clamped);
        int blue = interpolate(first & 0xff, second & 0xff, clamped);
        return red << 16 | green << 8 | blue;
    }

    private int interpolate(int first, int second, float ratio) {
        return Math.round(first + (second - first) * ratio);
    }
}
