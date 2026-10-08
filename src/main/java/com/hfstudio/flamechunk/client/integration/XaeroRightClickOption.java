package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.gui.GuiScreen;

import xaero.map.gui.GuiMap;
import xaero.map.gui.RightClickOption;

/** A non anonymous Xaero world map context action. */
public class XaeroRightClickOption extends RightClickOption {

    public static final int ACTION_SCAN = 1;
    public static final int ACTION_CLEAR = 2;
    public static final int ACTION_WEAK_CLEAR = 3;
    public static final int ACTION_LOADER = 4;

    private final int action;
    private final int dimensionId;
    private final int chunkX;
    private final int chunkZ;
    private final String entityType;

    public XaeroRightClickOption(String name, int index, GuiMap target, int action, int dimensionId, int chunkX,
        int chunkZ, String entityType) {
        super(name, index, target);
        this.action = action;
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entityType = entityType;
    }

    @Override
    public void onAction(GuiScreen screen) {
        switch (action) {
            case ACTION_SCAN -> MapOverlayControls.toggleScan();
            case ACTION_CLEAR -> MapOverlayControls.clear();
            case ACTION_WEAK_CLEAR -> MapOverlayControls
                .confirmWeakClear(screen, dimensionId, chunkX, chunkZ, entityType);
            case ACTION_LOADER -> MapOverlayControls.confirmLoaderToggle(screen, dimensionId, chunkX, chunkZ);
            default -> {}
        }
    }
}
