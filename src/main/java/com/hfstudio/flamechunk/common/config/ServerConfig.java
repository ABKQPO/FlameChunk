package com.hfstudio.flamechunk.common.config;

import com.hfstudio.flamechunk.FlameChunk;
import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

@Config(modid = FlameChunk.MODID, filename = "flamechunk", configSubDirectory = "flamechunk")
@Config.LangKeyPattern(pattern = "flamechunk.gui.config.%cat.%field", fullyQualified = true)
@Config.Comment("Server-side performance sampling settings")
public class ServerConfig {

    @Config.Comment("Default scan duration in seconds")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 1, max = 60)
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

    @Config.Comment("Require operator permission for scans")
    @Config.DefaultBoolean(true)
    public static boolean requireOperator = true;

    public static void register() throws ConfigException {
        ConfigurationManager.registerConfig(ServerConfig.class);
    }
}
