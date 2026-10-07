package com.hfstudio.flamechunk.client.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.data.ScanLimits;
import com.hfstudio.flamechunk.common.tick.TickCategory;

@Config(modid = FlameChunk.MODID, filename = "flamechunk", configSubDirectory = "flamechunk", category = "client")
@Config.LangKeyPattern(pattern = "flamechunk.gui.config.%cat.%field", fullyQualified = true)
@Config.Comment("Client-side performance display preferences")
public class ClientConfig {

    @Config.Ignore
    public static final int MIN_SCAN_SECONDS = ScanLimits.MIN_SECONDS;
    @Config.Ignore
    public static final int MAX_SCAN_SECONDS = ScanLimits.MAX_SECONDS;
    @Config.Ignore
    public static final float MIN_HEAT_THRESHOLD = 0.05F;
    @Config.Ignore
    public static final float MAX_HEAT_THRESHOLD = 50.0F;
    @Config.Ignore
    public static final float MIN_HEAT_ALPHA = 0.05F;
    @Config.Ignore
    public static final float MAX_HEAT_ALPHA = 1.0F;

    @Config.Comment("Scan duration requested by the client in seconds")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = MIN_SCAN_SECONDS, max = MAX_SCAN_SECONDS)
    public static int scanSeconds = 2;

    @Config.Comment("Scale heat colors relative to the current snapshot")
    @Config.DefaultBoolean(false)
    public static boolean relativeHeatColor;

    @Config.Comment("Absolute heat color threshold in milliseconds per tick")
    @Config.DefaultFloat(1.5F)
    @Config.RangeFloat(min = MIN_HEAT_THRESHOLD, max = MAX_HEAT_THRESHOLD)
    public static float heatThresholdMspt = 1.5F;

    @Config.Comment("Maximum heat overlay opacity")
    @Config.DefaultFloat(0.35F)
    @Config.RangeFloat(min = MIN_HEAT_ALPHA, max = MAX_HEAT_ALPHA)
    public static float heatAlpha = 0.35F;

    @Config.Comment("Display weakly loaded chunks without recorded activity")
    @Config.DefaultBoolean(true)
    public static boolean showWeakIdleChunks = true;

    @Config.Comment("Display chunk loader source borders")
    @Config.DefaultBoolean(true)
    public static boolean showLoaderSources = true;

    @Config.Comment("Display object hotspots and chunk beams when a map integration is available")
    @Config.DefaultBoolean(true)
    public static boolean worldOverlayEnabled = true;

    @Config.Comment("Display all captured object hotspots instead of only expensive objects")
    @Config.DefaultBoolean(false)
    public static boolean worldOverlayShowAll;

    @Config.Comment("Include chunk coordinates in map tooltips")
    @Config.DefaultBoolean(true)
    public static boolean tooltipCoordinates = true;

    @Config.Comment("Include entity counts in map tooltips")
    @Config.DefaultBoolean(true)
    public static boolean tooltipEntityCount = true;

    @Config.Comment("Include total timing in map tooltips")
    @Config.DefaultBoolean(true)
    public static boolean tooltipTotal = true;

    @Config.Comment("Include chunk load levels in map tooltips")
    @Config.DefaultBoolean(true)
    public static boolean tooltipLoadLevel = true;

    @Config.Comment("Include ticket sources in map tooltips")
    @Config.DefaultBoolean(true)
    public static boolean tooltipTicketSource = true;

    @Config.Comment("Use abbreviated timing category names in map tooltips")
    @Config.DefaultBoolean(false)
    public static boolean tooltipCategoryNamesShort;

    @Config.Comment("Append timing units to each category in map tooltips")
    @Config.DefaultBoolean(false)
    public static boolean tooltipCategoryUnits;

    @Config.Comment("Where completed scan reports are displayed")
    @Config.DefaultEnum("SCREEN")
    public static ReportOutputMode reportOutputMode = ReportOutputMode.SCREEN;

    @Config.Comment("Timing categories displayed in map tooltips")
    public static TooltipCategories tooltipCategories = new TooltipCategories();

    public static void register() throws ConfigException {
        ConfigurationManager.registerConfig(ClientConfig.class);
    }

    public static void save() {
        ConfigurationManager.save(ClientConfig.class);
    }

    public static void resetToDefaults() {
        scanSeconds = 2;
        relativeHeatColor = false;
        heatThresholdMspt = 1.5F;
        heatAlpha = 0.35F;
        showWeakIdleChunks = true;
        showLoaderSources = true;
        worldOverlayEnabled = true;
        worldOverlayShowAll = false;
        tooltipCoordinates = true;
        tooltipEntityCount = true;
        tooltipTotal = true;
        tooltipLoadLevel = true;
        tooltipTicketSource = true;
        tooltipCategoryNamesShort = false;
        tooltipCategoryUnits = false;
        reportOutputMode = ReportOutputMode.SCREEN;
        tooltipCategories = new TooltipCategories();
    }

    public static int boundedScanSeconds(int value) {
        return Math.max(MIN_SCAN_SECONDS, Math.min(MAX_SCAN_SECONDS, value));
    }

    @Config.LangKeyPattern(pattern = "flamechunk.gui.config.%cat.%field", fullyQualified = true)
    public static class TooltipCategories {

        @Config.DefaultBoolean(true)
        public boolean randomTick = true;
        @Config.DefaultBoolean(true)
        public boolean scheduledTick = true;
        @Config.DefaultBoolean(true)
        public boolean blockEntity = true;
        @Config.DefaultBoolean(true)
        public boolean entity = true;
        @Config.DefaultBoolean(true)
        public boolean mobSpawning = true;
        @Config.DefaultBoolean(true)
        public boolean blockUpdate = true;
        @Config.DefaultBoolean(true)
        public boolean blockEvent = true;
        @Config.DefaultBoolean(true)
        public boolean handler = true;
        @Config.DefaultBoolean(true)
        public boolean garbageCollection = true;

        public boolean contains(TickCategory category) {
            return switch (category) {
                case RANDOM_TICK -> randomTick;
                case SCHEDULED_TICK -> scheduledTick;
                case BLOCK_ENTITY -> blockEntity;
                case ENTITY -> entity;
                case MOB_SPAWNING -> mobSpawning;
                case BLOCK_UPDATE -> blockUpdate;
                case BLOCK_EVENT -> blockEvent;
                case HANDLER -> handler;
                case GARBAGE_COLLECTION -> garbageCollection;
            };
        }

        public void toggle(TickCategory category) {
            switch (category) {
                case RANDOM_TICK -> randomTick = !randomTick;
                case SCHEDULED_TICK -> scheduledTick = !scheduledTick;
                case BLOCK_ENTITY -> blockEntity = !blockEntity;
                case ENTITY -> entity = !entity;
                case MOB_SPAWNING -> mobSpawning = !mobSpawning;
                case BLOCK_UPDATE -> blockUpdate = !blockUpdate;
                case BLOCK_EVENT -> blockEvent = !blockEvent;
                case HANDLER -> handler = !handler;
                case GARBAGE_COLLECTION -> garbageCollection = !garbageCollection;
            }
        }
    }
}
