package com.hfstudio.flamechunk.client.render;

public class ColorCalculator {

    public int colorForMspt(float mspt, float budgetMspt) {
        return ColorUtils.heatColor(mspt, budgetMspt);
    }

    public float normalize(float mspt, float budgetMspt) {
        return ColorUtils.normalize(mspt, budgetMspt);
    }
}
