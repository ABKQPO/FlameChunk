package com.hfstudio.flamechunk.mixins.late.xaero;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import xaero.common.IXaeroMinimap;
import xaero.common.gui.GuiAddWaypoint;
import xaero.common.gui.GuiEntityRadar;
import xaero.common.gui.GuiTransfer;
import xaero.common.gui.GuiWaypoints;
import xaero.common.gui.ScreenBase;

// Fuck lwjgl3ify me.eigenraven.lwjgl3ify.mixins.late.xaeros.XaerosMinimapScrolling;
@Pseudo
@Mixin(value = { GuiAddWaypoint.class, GuiEntityRadar.class, GuiTransfer.class, GuiWaypoints.class }, priority = 2000)
public abstract class MixinLwjgl3ifyCompat extends ScreenBase {

    protected MixinLwjgl3ifyCompat(IXaeroMinimap modMain, GuiScreen parent, GuiScreen escape) {
        super(modMain, parent, escape);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }
}
