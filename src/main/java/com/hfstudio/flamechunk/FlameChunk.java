package com.hfstudio.flamechunk;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.NetworkHandler;
import com.hfstudio.flamechunk.common.network.PeerChannels;
import com.hfstudio.flamechunk.server.command.FlameChunkCommand;
import com.hfstudio.flamechunk.server.guard.EntityLoadGuard;
import com.hfstudio.flamechunk.server.guard.WeakChunkClearService;
import com.hfstudio.flamechunk.server.guard.WeakChunkInspector;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;
import com.hfstudio.flamechunk.server.sampler.LoaderTicketControlService;
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
import lombok.Getter;

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
    public EntityLoadGuard entityLoadGuard;
    public WeakChunkInspector weakChunkInspector;
    @Getter
    public WeakChunkClearService weakChunkClearService;
    public LoaderTicketControlService loaderTicketControlService;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            ServerConfig.register();
        } catch (Exception exception) {
            throw new RuntimeException("Unable to register FlameChunk configuration", exception);
        }
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NetworkHandler.register(network);
        FMLCommonHandler.instance()
            .bus()
            .register(new PeerChannels());
        serverUtilities = ServerUtilitiesBridge.create();
        sampler = new PerformanceSampler(serverUtilities);
        entityLoadGuard = new EntityLoadGuard(serverUtilities);
        weakChunkInspector = new WeakChunkInspector(serverUtilities);
        weakChunkClearService = new WeakChunkClearService(serverUtilities);
        loaderTicketControlService = new LoaderTicketControlService();
        FMLCommonHandler.instance()
            .bus()
            .register(sampler);
        FMLCommonHandler.instance()
            .bus()
            .register(entityLoadGuard);
        FMLCommonHandler.instance()
            .bus()
            .register(weakChunkInspector);
        FMLCommonHandler.instance()
            .bus()
            .register(weakChunkClearService);
        MinecraftForge.EVENT_BUS.register(loaderTicketControlService);
        MinecraftForge.EVENT_BUS.register(weakChunkClearService);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (serverUtilities != null && serverUtilities.isAvailable()) {
            serverUtilities.registerPermissions();
        }
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
        event.registerServerCommand(new FlameChunkCommand(weakChunkClearService, weakChunkInspector));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LoaderTicketControlService.clearRuntimeState();
        if (sampler != null) {
            sampler.onServerStopping(event);
        }
        if (weakChunkClearService != null) {
            weakChunkClearService.onServerStopping(event);
        }
        if (weakChunkInspector != null) {
            weakChunkInspector.onServerStopping(event);
        }
    }
}
