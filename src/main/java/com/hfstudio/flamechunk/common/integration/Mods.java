package com.hfstudio.flamechunk.common.integration;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.network.NetworkManager;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.IMod;
import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;
import com.hfstudio.flamechunk.common.network.PeerChannels;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public enum Mods implements IMod, ITargetMod {

    // spotless:off
    NotEnoughItems("NotEnoughItems"),
    Angelica("angelica"),
    JourneyMap("journeymap"),
    JourneyMap5("journeymap", Mods::isJourneyMap5),
    JourneyMap6("journeymap", Mods::isJourneyMap6),
    XaeroWorldMap("XaeroWorldMap"),
    XaeroMinimap("XaeroMinimap"),
    ServerUtilities("serverutilities"),
    ;
    // spotless:on

    public final String modid;
    public final String resourceDomain;
    private final Supplier<Boolean> supplier;
    private final TargetModBuilder targetBuilder;
    private Boolean loaded;

    Mods(String modid) {
        this(modid, null, null);
    }

    Mods(Supplier<Boolean> supplier) {
        this(null, supplier, null);
    }

    Mods(String modid, Supplier<Boolean> supplier) {
        this(modid, supplier, null);
    }

    Mods(String modid, Supplier<Boolean> supplier, String coreModClass) {
        this.modid = modid;
        this.resourceDomain = modid != null ? modid.toLowerCase(Locale.ENGLISH) : null;
        this.supplier = supplier;
        this.targetBuilder = new TargetModBuilder().setModId(modid)
            .setCoreModClass(coreModClass);
    }

    @NotNull
    @Override
    public TargetModBuilder getBuilder() {
        return targetBuilder;
    }

    @Override
    public boolean isModLoaded() {
        if (loaded == null) {
            if (supplier != null) {
                loaded = supplier.get();
            } else if (modid != null) {
                loaded = Loader.isModLoaded(modid);
            } else loaded = false;
        }
        return loaded;
    }

    public static boolean hasMapIntegration() {
        return JourneyMap.isModLoaded() || XaeroWorldMap.isModLoaded() || XaeroMinimap.isModLoaded();
    }

    public static boolean hasRemoteFlameChunk(NetworkManager manager) {
        return PeerChannels.isAvailable(manager);
    }

    @Override
    public String getID() {
        return modid;
    }

    @Override
    public String getResourceLocation() {
        return resourceDomain;
    }

    private static boolean isJourneyMap5() {
        return hasJourneyMapVersion("5.");
    }

    private static boolean isJourneyMap6() {
        return hasJourneyMapVersion("6.");
    }

    private static boolean hasJourneyMapVersion(String prefix) {
        if (!JourneyMap.isModLoaded()) {
            return false;
        }
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(JourneyMap.modid);
        if (container == null || container.getVersion() == null) {
            return false;
        }
        String version = container.getVersion();
        return version.startsWith(prefix) || version.contains("-" + prefix);
    }
}
