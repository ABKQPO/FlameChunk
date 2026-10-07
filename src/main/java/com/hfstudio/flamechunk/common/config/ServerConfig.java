package com.hfstudio.flamechunk.common.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.data.ScanLimits;

@Config(modid = FlameChunk.MODID, filename = "flamechunk", configSubDirectory = "flamechunk")
@Config.LangKeyPattern(pattern = "flamechunk.gui.config.%cat.%field", fullyQualified = true)
@Config.Comment("Server-side performance sampling settings")
public class ServerConfig {

    @Config.Comment("Default scan duration in seconds")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = ScanLimits.MIN_SECONDS, max = ScanLimits.MAX_SECONDS)
    public static int scanSeconds = 2;

    @Config.Comment("Maximum chunks retained per dimension")
    @Config.DefaultInt(4096)
    @Config.RangeInt(min = 256, max = 32768)
    public static int maxChunksPerDimension = 4096;

    @Config.Comment("Maximum dimensions retained in one scan")
    @Config.DefaultInt(16)
    @Config.RangeInt(min = 1, max = 32)
    public static int maxDimensions = 16;

    @Config.Comment("Maximum encoded packet size in bytes")
    @Config.DefaultInt(1048576)
    @Config.RangeInt(min = 65536, max = 8388608)
    public static int maxPacketBytes = 1048576;

    @Config.Comment("Server tick interval between live performance snapshots")
    @Config.DefaultInt(40)
    @Config.RangeInt(min = 20, max = 200)
    public static int liveSnapshotIntervalTicks = 40;

    @Config.Comment("Maximum chunks sent per dimension in a live performance snapshot")
    @Config.DefaultInt(256)
    @Config.RangeInt(min = 64, max = 2048)
    public static int liveSnapshotChunkLimit = 256;

    @Config.Comment("Require operator permission for scans")
    @Config.DefaultBoolean(true)
    public static boolean requireOperator = true;

    @Config.Comment("Enable bounded entity load diagnostics")
    @Config.DefaultBoolean(true)
    public static boolean entityLoadDiagnostics = true;

    @Config.Comment("Enable entity load protection when chunks are loaded")
    @Config.DefaultBoolean(false)
    public static boolean entityLoadProtection = false;

    @Config.Comment("Entity count per chunk that enables load protection")
    @Config.DefaultInt(5000)
    @Config.RangeInt(min = 256, max = 100000)
    public static int entityProtectionThreshold = 5000;

    @Config.Comment("Number of most common entity types eligible for load protection")
    @Config.DefaultInt(5)
    @Config.RangeInt(min = 1, max = 32)
    public static int entityProtectionTopTypes = 5;

    @Config.Comment("Minimum entities retained for each protected type")
    @Config.DefaultInt(256)
    @Config.RangeInt(min = 0, max = 10000)
    public static int entityProtectionRetainedPerType = 256;

    @Config.Comment("Maximum entities inspected from one chunk during load protection")
    @Config.DefaultInt(100000)
    @Config.RangeInt(min = 1000, max = 250000)
    public static int entityProtectionMaximumInspected = 100000;

    @Config.Comment("Maximum entities removed from one chunk during load protection")
    @Config.DefaultInt(1024)
    @Config.RangeInt(min = 1, max = 10000)
    public static int entityProtectionMaximumRemoved = 1024;

    @Config.Comment("Broadcast a message when entity load protection removes entities")
    @Config.DefaultBoolean(false)
    public static boolean entityProtectionBroadcast = false;

    @Config.Comment("Entity count per chunk that triggers a warning")
    @Config.DefaultInt(512)
    @Config.RangeInt(min = 64, max = 100000)
    public static int entityWarningThreshold = 512;

    @Config.Comment("Seconds between repeated entity load warnings")
    @Config.DefaultInt(10)
    @Config.RangeInt(min = 1, max = 300)
    public static int entityWarningIntervalSeconds = 10;

    @Config.Comment("Enable weak chunk diagnostics")
    @Config.DefaultBoolean(true)
    public static boolean weakChunkDiagnostics = true;

    @Config.Comment("Append bounded chunk ticket diagnostics to server crash reports")
    @Config.DefaultBoolean(true)
    public static boolean watchdogTicketDiagnostics = true;

    @Config.Comment("Loaded chunks below this count are reported as weakly loaded")
    @Config.DefaultInt(1)
    @Config.RangeInt(min = 1, max = 1024)
    public static int weakChunkMinimum = 1;

    @Config.Comment("World ticks between weak chunk diagnostics")
    @Config.DefaultInt(100)
    @Config.RangeInt(min = 20, max = 1200)
    public static int weakChunkCheckIntervalTicks = 100;

    @Config.Comment("Entity count needed for a weakly loaded chunk to be reported")
    @Config.DefaultInt(50)
    @Config.RangeInt(min = 1, max = 100000)
    public static int weakChunkEntityThreshold = 50;

    @Config.Comment("Maximum loaded entities inspected in one weak chunk scan")
    @Config.DefaultInt(100000)
    @Config.RangeInt(min = 1000, max = 1000000)
    public static int weakChunkMaximumScannedEntities = 100000;

    @Config.Comment("Maximum entities inspected per world tick by weak chunk diagnostics")
    @Config.DefaultInt(2048)
    @Config.RangeInt(min = 128, max = 32768)
    public static int weakChunkEntitiesPerTick = 2048;

    public static void register() throws ConfigException {
        ConfigurationManager.registerConfig(ServerConfig.class);
    }
}
