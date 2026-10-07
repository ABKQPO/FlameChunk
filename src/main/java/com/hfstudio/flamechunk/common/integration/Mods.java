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
    JourneyMap6("journeymap_api", Mods::isJourneyMap6),
    JourneyMapApi("journeymap_api"),
    XaeroWorldMap("XaeroWorldMap"),
    XaeroMinimap("XaeroMinimap"),
    Navigator("navigator"),
    ServerUtilities("serverutilities"),
    ;
    // spotless:on

    public final String modid;
    public final String resourceDomain;
    public final Supplier<Boolean> supplier;
    public final TargetModBuilder targetBuilder;
    public Boolean loaded;

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
        return JourneyMap5.isModLoaded() || JourneyMap6.isModLoaded()
            || XaeroWorldMap.isModLoaded()
            || XaeroMinimap.isModLoaded();
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

    public static boolean isJourneyMap5() {
        return Loader.isModLoaded(JourneyMap5.modid) && !Loader.isModLoaded(JourneyMap6.modid);
    }

    public static boolean isJourneyMap6() {
        return Loader.isModLoaded(JourneyMap6.modid);
    }

    public static boolean hasJourneyMapVersion(String prefix) {
        if (!JourneyMap.isModLoaded()) {
            return false;
        }
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(JourneyMap.modid);
        if (container == null) {
            return false;
        }
        String version = container.getVersion();
        return version != null && (version.startsWith(prefix) || version.contains("-" + prefix));
    }
}
