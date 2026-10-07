package com.hfstudio.flamechunk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.NetworkHandler;
import com.hfstudio.flamechunk.server.command.FlameChunkCommand;
import com.hfstudio.flamechunk.server.guard.EntityLoadGuard;
import com.hfstudio.flamechunk.server.guard.WeakChunkInspector;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(
    modid = FlameChunk.MODID,
    version = FlameChunk.VERSION,
    name = FlameChunk.MODNAME,
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "[1.7.10]")
public class FlameChunk {

    @Mod.Instance(Tags.MODID)
    public static FlameChunk instance;
    public static final String MODID = Tags.MODID;
    public static final String MODNAME = Tags.MODNAME;
    public static final String VERSION = Tags.VERSION;
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.hfstudio.flamechunk.ClientProxy", serverSide = "com.hfstudio.flamechunk.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;
    public static PerformanceSampler sampler;
    public static ServerUtilitiesBridge serverUtilities;
    private EntityLoadGuard entityLoadGuard;
    private WeakChunkInspector weakChunkInspector;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            ServerConfig.register();
        } catch (Exception exception) {
            throw new RuntimeException("Unable to register FlameChunk configuration", exception);
        }
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NetworkHandler.register(network);
        serverUtilities = ServerUtilitiesBridge.create();
        sampler = new PerformanceSampler(serverUtilities);
        entityLoadGuard = new EntityLoadGuard(serverUtilities);
        weakChunkInspector = new WeakChunkInspector(serverUtilities);
        FMLCommonHandler.instance()
            .bus()
            .register(sampler);
        FMLCommonHandler.instance()
            .bus()
            .register(entityLoadGuard);
        FMLCommonHandler.instance()
            .bus()
            .register(weakChunkInspector);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void completeInit(FMLLoadCompleteEvent event) {
        proxy.completeInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FlameChunkCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (sampler != null) {
            sampler.onServerStopping(event);
        }
    }
}
