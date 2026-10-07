package com.hfstudio.flamechunk.server.sampler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTiming;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot;
import com.hfstudio.flamechunk.common.data.ScanLimits;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.network.PeerChannels;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsPacket;
import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.command.ServerMessages;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;
import com.hfstudio.flamechunk.server.sampler.SnapshotSubscriptions.Subscription;
import com.hfstudio.flamechunk.server.sampler.SnapshotSubscriptions.Update;

import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

public class PerformanceSampler {

    public static final int MAX_PENDING_REQUESTS = 64;
    public static final int MAX_TYPE_AGGREGATES_PER_SCAN = 8192;
    public static final int PROGRESS_UPDATE_INTERVAL_TICKS = 2;
    public static final int STALLED_SCAN_TIMEOUT_SECONDS = 5;
    public static final long STALLED_SCAN_TIMEOUT_NANOS = STALLED_SCAN_TIMEOUT_SECONDS * 1000000000L;
    public static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    public static PerformanceSampler activeSampler;

    public final Int2ObjectOpenHashMap<DimensionTimings> dimensions = new Int2ObjectOpenHashMap<>();
    public final SnapshotBuilder snapshotBuilder;
    public final ObjectHotspotStore objectHotspots = new ObjectHotspotStore();
    public final SnapshotPublisher snapshotPublisher = new SnapshotPublisher();
    public final SnapshotSubscriptions subscriptions;
    public final ServerUtilitiesBridge serverUtilities;
    public final ConcurrentLinkedQueue<PendingRequest> pendingRequests = new ConcurrentLinkedQueue<>();
    public final AtomicInteger pendingRequestCount = new AtomicInteger();
    public ICommandSender feedback;
    public int durationSeconds;
    public long totalTicks;
    public long sampledTicks;
    public long lastGcCollectionMillis;
    public boolean active;
    public int droppedDimensions;
    public int droppedChunks;
    public int typeAggregateCount;
    public ScanSnapshot lastSnapshot;
    public long nextReportId;
    public long activeReportId;
    public long lastSnapshotReportId;
    public long serverTicks;
    public long lastServerTickNanos;
    public volatile PrimaryObservationSampler primarySampler;
    public static volatile boolean serverTickInProgress;
    public static final ThreadLocal<LongArrayList> NEIGHBOR_STARTS = ThreadLocal.withInitial(LongArrayList::new);
    public static final ClassValue<String> TYPE_NAMES = new ClassValue<>() {

        @Override
        public String computeValue(Class<?> type) {
            String name = type.getSimpleName();
            return name.isEmpty() ? type.getName() : name;
        }
    };

    public static String workTypeName(Class<?> type) {
        return type == null ? "Unknown" : TYPE_NAMES.get(type);
    }

    public static int enterWork(TickCategory category, World world, String typeName) {
        PerformanceSampler sampler = activeSampler;
        PrimaryObservationSampler primary = sampler == null ? null : sampler.primarySampler;
        return primary == null || world != null && world.isRemote ? 0
            : primary.tracker.enter(
                category,
                world == null ? WorkContextTracker.NO_DIMENSION : world.provider.dimensionId,
                typeName);
    }

    public static int enterTask(Class<?> taskClass) {
        PerformanceSampler sampler = activeSampler;
        PrimaryObservationSampler primary = sampler == null ? null : sampler.primarySampler;
        return primary == null || Thread.currentThread() != primary.tracker.owner ? 0
            : primary.tracker.enter(TickCategory.TASK, WorkContextTracker.NO_DIMENSION, workTypeName(taskClass));
    }

    public static boolean isServerWorkThread() {
        PerformanceSampler sampler = activeSampler;
        PrimaryObservationSampler primary = sampler == null ? null : sampler.primarySampler;
        return primary != null && Thread.currentThread() == primary.tracker.owner;
    }

    public static void leaveWork(int token) {
        PerformanceSampler sampler = activeSampler;
        PrimaryObservationSampler primary = sampler == null ? null : sampler.primarySampler;
        if (primary != null) {
            primary.tracker.leave(token);
        }
    }

    public static void beginServerWorkTick() {
        serverTickInProgress = true;
        PerformanceSampler sampler = activeSampler;
        if (sampler != null && sampler.primarySampler != null) {
            sampler.primarySampler.beginTick();
        }
    }

    public static void endServerWorkTick() {
        serverTickInProgress = false;
        PerformanceSampler sampler = activeSampler;
        if (sampler != null && sampler.primarySampler != null) {
            sampler.primarySampler.endTick();
        }
    }

    public ScanSnapshot withObservations(ScanSnapshot snapshot) {
        return withObservations(snapshot, true);
    }

    public ScanSnapshot withObservations(ScanSnapshot snapshot, boolean includeUnknownStacks) {
        PrimaryObservationSampler primary = primarySampler;
        return new ScanSnapshot(
            snapshot.getDurationSeconds(),
            snapshot.getSampledTicks(),
            snapshot.getDimensions(),
            primary == null ? ObservationSnapshot.EMPTY : primary.snapshot(includeUnknownStacks));
    }

    public PerformanceSampler() {
        this(ServerUtilitiesBridge.NONE);
    }

    public PerformanceSampler(ServerUtilitiesBridge serverUtilities) {
        activeSampler = this;
        this.serverUtilities = serverUtilities == null ? ServerUtilitiesBridge.NONE : serverUtilities;
        snapshotBuilder = new SnapshotBuilder(this.serverUtilities);
        subscriptions = new SnapshotSubscriptions(this.serverUtilities);
    }

    public static void record(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordTiming(category, world, chunkX, chunkZ, elapsedNanos, null);
        }
    }

    public static void recordObjectTiming(TickCategory category, World world, int chunkX, int chunkZ, String typeName,
        long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordTiming(category, world, chunkX, chunkZ, elapsedNanos, typeName);
        }
    }

    public static void recordGlobal(TickCategory category, World world, long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordGlobalTiming(category, world, elapsedNanos);
        }
    }

    public static void recordEntityTiming(World world, Entity entity, long elapsedNanos) {
        if (!isActive() || !isServerWorkThread() || world == null || world.isRemote || entity == null) {
            return;
        }
        String typeName = EntityList.getEntityString(entity);
        if (typeName == null) {
            typeName = entity.getClass()
                .getSimpleName();
        }
        activeSampler
            .recordTiming(TickCategory.ENTITY, world, entity.chunkCoordX, entity.chunkCoordZ, elapsedNanos, typeName);
        activeSampler.objectHotspots.record(
            world.provider.dimensionId,
            TickCategory.ENTITY,
            typeName,
            entity.getEntityId(),
            entity.getUniqueID()
                .getMostSignificantBits(),
            entity.getUniqueID()
                .getLeastSignificantBits(),
            MathHelper.floor_double(entity.posX),
            MathHelper.floor_double(entity.posY),
            MathHelper.floor_double(entity.posZ),
            elapsedNanos);
    }

    public static void recordTileEntityTiming(World world, TileEntity tileEntity, long elapsedNanos) {
        if (tileEntity != null) {
            recordBlockTiming(
                TickCategory.BLOCK_ENTITY,
                world,
                tileEntity.xCoord,
                tileEntity.yCoord,
                tileEntity.zCoord,
                tileEntity.getClass()
                    .getSimpleName(),
                elapsedNanos);
        }
    }

    public static void recordBlockTiming(TickCategory category, World world, int x, int y, int z, String typeName,
        long elapsedNanos) {
        if (!isActive() || !isServerWorkThread() || world == null || world.isRemote) {
            return;
        }
        activeSampler.recordTiming(category, world, x >> 4, z >> 4, elapsedNanos, typeName);
        activeSampler.objectHotspots
            .record(world.provider.dimensionId, category, typeName, -1, 0L, 0L, x, y, z, elapsedNanos);
    }

    public static void recordGlobalObjectTiming(TickCategory category, World world, String typeName,
        long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.accumulateGlobalObjectTiming(category, world, typeName, elapsedNanos);
        }
    }

    public static boolean requestScan(EntityPlayerMP player, int seconds) {
        return activeSampler != null && activeSampler.startScan(player, seconds);
    }

    public static boolean requestConsoleScan(ICommandSender sender, int seconds) {
        return activeSampler != null && activeSampler.startScan(null, sender, seconds);
    }

    public static boolean enqueueScan(EntityPlayerMP player, int seconds) {
        if (activeSampler != null && player != null) {
            return activeSampler.enqueuePendingRequest(player, seconds);
        }
        return false;
    }

    public static boolean setSubscription(EntityPlayerMP player, boolean subscribed) {
        return setSubscription(player, subscribed, false);
    }

    public static boolean setSubscription(EntityPlayerMP player, boolean subscribed, boolean worldHotspots) {
        return activeSampler != null && activeSampler.updateSubscription(player, subscribed, worldHotspots);
    }

    public static void removeSubscription(EntityPlayerMP player) {
        if (activeSampler != null && player != null) {
            activeSampler.subscriptions.remove(player);
        }
    }

    public static boolean isActive() {
        return activeSampler != null && activeSampler.active;
    }

    public static boolean stopScan(ICommandSender sender) {
        if (activeSampler == null || sender == null || !activeSampler.active) {
            return false;
        }
        if (sender instanceof EntityPlayerMP player) {
            if ((ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
                || !activeSampler.serverUtilities.hasPermission(player, "flamechunk.scan")) {
                return false;
            }
        }
        activeSampler.finishScan();
        return true;
    }

    public static boolean sendLastReport(ICommandSender sender) {
        if (activeSampler == null || sender == null || activeSampler.lastSnapshot == null) {
            return false;
        }
        if (sender instanceof EntityPlayerMP player) {
            if ((ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
                || !activeSampler.serverUtilities.hasPermission(player, "flamechunk.scan")) {
                return false;
            }
        }
        activeSampler.writeReport(sender, activeSampler.lastSnapshot);
        return true;
    }

    public static long beginTiming() {
        return isActive() && isServerWorkThread() ? System.nanoTime() : 0L;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            serverTicks++;
            processPendingRequests();
            lastServerTickNanos = System.nanoTime();
            if (serverTicks % 20L == 0L) {
                subscriptions.prune();
                if (!active && lastSnapshot != null) {
                    List<Subscription> pending = subscriptions.current()
                        .stream()
                        .filter(subscription -> subscription.pendingSnapshot)
                        .toList();
                    snapshotPublisher.publish(lastSnapshot, true, lastSnapshotReportId, pending, null);
                }
            }
        }
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        sampledTicks++;
        if (sampledTicks % 20L == 0L) {
            captureGarbageCollection();
        }
        if (!subscriptions.subscribers.isEmpty()) {
            if (sampledTicks % PROGRESS_UPDATE_INTERVAL_TICKS == 0L) {
                sendProgress();
            }
            if (sampledTicks % Math.max(20, ServerConfig.liveSnapshotIntervalTicks) == 0L) {
                sendLiveSnapshot();
            }
        }
        if (sampledTicks >= totalTicks) {
            finishScan();
        }
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        active = false;
        if (primarySampler != null) {
            primarySampler.close();
            primarySampler = null;
        }
        subscriptions.clear();
        serverTicks = 0L;
        feedback = null;
        pendingRequests.clear();
        pendingRequestCount.set(0);
        lastSnapshot = null;
        activeReportId = 0L;
        lastSnapshotReportId = 0L;
        lastServerTickNanos = 0L;
        resetTimings();
    }

    public boolean startScan(EntityPlayerMP player, int seconds) {
        return startScan(player, player, seconds);
    }

    public boolean startScan(EntityPlayerMP player, ICommandSender sender, int seconds) {
        recoverStalledScan();
        if (active || !ScanLimits.isValidDuration(seconds)) {
            return false;
        }
        if (ServerConfig.requireOperator && player != null && !player.canCommandSenderUseCommand(2, "flamechunk")) {
            return false;
        }
        if (!serverUtilities.hasPermission(player, "flamechunk.scan")) {
            return false;
        }
        durationSeconds = seconds;
        nextReportId = nextReportId == Long.MAX_VALUE ? 1L : nextReportId + 1L;
        activeReportId = nextReportId;
        totalTicks = durationSeconds * 20L;
        sampledTicks = 0L;
        lastGcCollectionMillis = gcCollectionMillis();
        feedback = sender;
        if (player != null) {
            if (!subscriptions.contains(player)) {
                subscriptions.update(player, true, false);
            }
        }
        active = true;
        droppedDimensions = 0;
        droppedChunks = 0;
        resetTimings();
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.worldServers != null) {
            for (World world : server.worldServers) {
                if (world != null && !world.isRemote) {
                    getDimension(world);
                }
            }
        }
        primarySampler = new PrimaryObservationSampler(ServerConfig.sampleIntervalMicros);
        if (serverTickInProgress) {
            primarySampler.beginTick();
        }
        List<Subscription> currentSubscriptions = subscriptions.current();
        if (!currentSubscriptions.isEmpty()) {
            sendStatusToSubscribers(ScanProgressPacket.STARTED);
            for (Subscription subscriber : currentSubscriptions) {
                FlameChunk.network.sendTo(new ClearSnapshotPacket(), subscriber.player());
            }
            sendProgress();
        }
        if (feedback != null) {
            ServerMessages.send(feedback, "flamechunk.command.started", durationSeconds);
        }
        return true;
    }

    public void finishScan() {
        captureGarbageCollection();
        if (primarySampler != null) {
            primarySampler.close();
        }
        active = false;
        try {
            int actualDurationSeconds = (int) Math.max(1L, Math.min(durationSeconds, (sampledTicks + 19L) / 20L));
            ScanSnapshot snapshot = withObservations(
                snapshotBuilder.build(
                    actualDurationSeconds,
                    sampledTicks,
                    dimensions,
                    ServerConfig.maxChunksPerDimension,
                    objectHotspots));
            lastSnapshot = snapshot;
            lastSnapshotReportId = activeReportId;
            Set<NetworkManager> delivered = snapshotPublisher
                .publish(snapshot, true, lastSnapshotReportId, subscriptions.current(), objectHotspots);
            if (droppedDimensions > 0 || droppedChunks > 0) {
                FlameChunk.LOG.warn(
                    "FlameChunk scan dropped {} dimensions and {} chunks because configured bounds were reached",
                    droppedDimensions,
                    droppedChunks);
            }
            if (feedback != null) {
                ServerMessages.send(feedback, "flamechunk.command.completed", sampledTicks);
                if (!(feedback instanceof EntityPlayerMP player) || player.playerNetServerHandler == null
                    || !delivered.contains(player.playerNetServerHandler.netManager)) {
                    writeReport(feedback, snapshot);
                }
            }
        } catch (RuntimeException exception) {
            FlameChunk.LOG.error("Unable to publish FlameChunk scan", exception);
            if (feedback != null) {
                ServerMessages.send(feedback, "flamechunk.command.failed");
            }
        } finally {
            primarySampler = null;
            feedback = null;
            activeReportId = 0L;
            resetTimings();
        }
    }

    public void sendProgress() {
        ScanProgressPacket packet = new ScanProgressPacket(sampledTicks, totalTicks);
        for (Subscription subscriber : subscriptions.current()) {
            FlameChunk.network.sendTo(packet, subscriber.player());
        }
    }

    public void sendLiveSnapshot() {
        List<Subscription> currentSubscriptions = subscriptions.current();
        if (currentSubscriptions.isEmpty()) {
            return;
        }
        try {
            ScanSnapshot snapshot = withObservations(
                snapshotBuilder.build(
                    durationSeconds,
                    sampledTicks,
                    dimensions,
                    Math.min(ServerConfig.maxChunksPerDimension, ServerConfig.liveSnapshotChunkLimit),
                    null),
                false);
            snapshotPublisher.publish(snapshot, false, activeReportId, currentSubscriptions, objectHotspots);
        } catch (RuntimeException exception) {
            FlameChunk.LOG.debug("Unable to publish a live FlameChunk snapshot", exception);
        }
    }

    public void resetTimings() {
        dimensions.clear();
        objectHotspots.clear();
        typeAggregateCount = 0;
    }

    public void processPendingRequests() {
        int processed = 0;
        PendingRequest request;
        while (processed++ < 8 && (request = pendingRequests.poll()) != null) {
            pendingRequestCount.decrementAndGet();
            if (!PeerChannels.canSend(request.player)) {
                continue;
            }
            if (request.seconds == ScanRequestPacket.UNSUBSCRIBE_REQUEST) {
                updateSubscription(request.player, false, false);
            } else if (request.seconds == ScanRequestPacket.SUBSCRIBE_REQUEST
                || request.seconds == ScanRequestPacket.SUBSCRIBE_WORLD_REQUEST) {
                    updateSubscription(
                        request.player,
                        true,
                        request.seconds == ScanRequestPacket.SUBSCRIBE_WORLD_REQUEST);
                } else if (!startScan(request.player, request.player, request.seconds)) {
                    sendStatus(request.player, active ? ScanProgressPacket.BUSY : ScanProgressPacket.DENIED);
                }
        }
    }

    public void writeReport(ICommandSender sender, ScanSnapshot snapshot) {
        ObservationSnapshot observations = snapshot.observations;
        ServerMessages.send(
            sender,
            "flamechunk.command.report.observations",
            observations.averageMspt(),
            observations.peakTickNanos() / 1000000.0D,
            observations.sampleAttempts(),
            observations.completedTicks());
        if (!observations.degradationReason()
            .isEmpty()) {
            ServerMessages.send(sender, observations.degradationReason());
        }
        for (int index = 0; index < Math.min(
            8,
            observations.entries()
                .size()); index++) {
            ObservationSnapshot.Entry entry = observations.entries()
                .get(index);
            ServerMessages.send(
                sender,
                "flamechunk.command.report.observationEntry",
                entry.category()
                    .name(),
                entry.typeName(),
                entry.dimensionId(),
                entry.nanos() / 1000000.0D / Math.max(1L, observations.completedTicks()),
                entry.peakNanos() / 1000000.0D,
                entry.samples());
        }
        if (!(sender instanceof EntityPlayerMP player) || player.canCommandSenderUseCommand(2, "flamechunk")) {
            for (ObservationSnapshot.StackDetail detail : observations.unknownStacks()) {
                ServerMessages
                    .send(sender, "flamechunk.command.report.unknownStack", detail.samples(), detail.anchor());
                for (int frame = 0; frame < Math.min(
                    8,
                    detail.frames()
                        .size()); frame++) {
                    ServerMessages.send(
                        sender,
                        "flamechunk.command.report.unknownStackFrame",
                        detail.frames()
                            .get(frame));
                }
            }
        }
        sender.addChatMessage(
            ServerMessages.translated(
                sender,
                "flamechunk.command.report.header",
                snapshot.getDurationSeconds(),
                snapshot.getSampledTicks()));
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            sender.addChatMessage(
                ServerMessages.translated(sender, "flamechunk.command.report.dimension", dimension.getDimensionId()));
            long[] globalNanos = dimension.getGlobalNanos();
            for (TickCategory category : TickCategory.values()) {
                long categoryNanos = globalNanos[category.ordinal()];
                if (categoryNanos > 0L) {
                    sender.addChatMessage(
                        ServerMessages.translated(
                            sender,
                            "flamechunk.command.report.category",
                            category.name(),
                            categoryNanos / 1000000.0D / Math.max(1L, snapshot.getSampledTicks())));
                }
            }
            List<ChunkTypeTiming> globalTypeTimings = dimension.getGlobalTypeTimings();
            int globalTypeLimit = Math.min(4, globalTypeTimings.size());
            for (int typeIndex = 0; typeIndex < globalTypeLimit; typeIndex++) {
                ChunkTypeTiming timing = globalTypeTimings.get(typeIndex);
                sender.addChatMessage(
                    ServerMessages.translated(
                        sender,
                        "flamechunk.command.report.hotspot",
                        timing.getCategory()
                            .name(),
                        timing.getTypeName(),
                        timing.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                        timing.getCount(),
                        timing.getPeakNanos() / 1000000.0D));
            }
            List<ChunkSnapshot> chunks = new ArrayList<>(dimension.getChunks());
            chunks.sort((left, right) -> Long.compare(right.totalNanos(), left.totalNanos()));
            int shown = 0;
            for (ChunkSnapshot chunk : chunks) {
                if (chunk.totalNanos() <= 0L) {
                    continue;
                }
                sender.addChatMessage(
                    ServerMessages.translated(
                        sender,
                        "flamechunk.command.report.chunk",
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        chunk.calculateMspt(snapshot.getSampledTicks()),
                        chunk.getTicketSource()
                            .isEmpty() ? "none" : chunk.getTicketSource()));
                int typeCount = 0;
                for (ChunkTypeTiming typeTiming : chunk.getTypeTimings()) {
                    sender.addChatMessage(
                        ServerMessages.translated(
                            sender,
                            "flamechunk.command.report.hotspot",
                            typeTiming.getCategory()
                                .name(),
                            typeTiming.getTypeName(),
                            typeTiming.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                            typeTiming.getCount(),
                            typeTiming.getPeakNanos() / 1000000.0D));
                    if (++typeCount >= 4) {
                        break;
                    }
                }
                shown++;
                if (shown >= 5) {
                    break;
                }
            }
            int objectLimit = Math.min(5, dimension.objectHotspots.size());
            for (int index = 0; index < objectLimit; index++) {
                ObjectHotspot hotspot = dimension.objectHotspots.get(index);
                sender.addChatMessage(
                    ServerMessages.translated(
                        sender,
                        "flamechunk.command.report.object",
                        hotspot.category.name(),
                        hotspot.typeName,
                        hotspot.x,
                        hotspot.y,
                        hotspot.z,
                        hotspot.calculateMspt(snapshot.getSampledTicks()),
                        hotspot.peakNanos / 1000000.0D,
                        hotspot.count));
            }
        }
    }

    public boolean enqueuePendingRequest(EntityPlayerMP player, int seconds) {
        while (true) {
            int current = pendingRequestCount.get();
            if (current >= MAX_PENDING_REQUESTS || !pendingRequestCount.compareAndSet(current, current + 1)) {
                if (current >= MAX_PENDING_REQUESTS) {
                    return false;
                }
                continue;
            }
            pendingRequests.offer(new PendingRequest(player, seconds));
            return true;
        }
    }

    public void sendStatus(EntityPlayerMP player, int status) {
        if (PeerChannels.canSend(player)) {
            FlameChunk.network.sendTo(ScanProgressPacket.forStatus(status), player);
        }
    }

    public void sendStatusToSubscribers(int status) {
        ScanProgressPacket packet = ScanProgressPacket.forStatus(status);
        for (Subscription subscriber : subscriptions.current()) {
            FlameChunk.network.sendTo(packet, subscriber.player());
        }
    }

    public boolean updateSubscription(EntityPlayerMP player, boolean subscribed, boolean worldHotspots) {
        Update update = subscriptions.update(player, subscribed, worldHotspots);
        if (update == Update.DENIED || update == Update.FULL) {
            sendStatus(
                player,
                update == Update.FULL ? ScanProgressPacket.QUEUE_FULL : ScanProgressPacket.SUBSCRIPTION_DENIED);
            return false;
        }
        if (active && (update == Update.ADDED || update == Update.CHANGED)) {
            sendStatus(player, ScanProgressPacket.STARTED);
            FlameChunk.network.sendTo(new ScanProgressPacket(sampledTicks, totalTicks), player);
        }
        if (update != Update.UNCHANGED) {
            sendStatus(player, subscribed ? ScanProgressPacket.SUBSCRIBED : ScanProgressPacket.UNSUBSCRIBED);
        }
        return true;
    }

    private void recoverStalledScan() {
        long now = System.nanoTime();
        if (!active || lastServerTickNanos == 0L || now - lastServerTickNanos <= STALLED_SCAN_TIMEOUT_NANOS) {
            return;
        }
        if (primarySampler != null) {
            primarySampler.close();
            primarySampler = null;
        }
        active = false;
        sendStatusToSubscribers(ScanProgressPacket.SCAN_INTERRUPTED);
        if (feedback instanceof EntityPlayerMP player && PeerChannels.canSend(player)
            && !subscriptions.contains(player)) {
            sendStatus(player, ScanProgressPacket.SCAN_INTERRUPTED);
        }
        FlameChunk.LOG.warn(
            "Discarded a stalled FlameChunk scan after {} seconds without a server tick",
            (now - lastServerTickNanos) / 1000000000L);
        feedback = null;
        activeReportId = 0L;
        resetTimings();
    }

    public void sendUnknownStackDetails(EntityPlayerMP player, long requestId, long reportId) {
        if (player == null || player.playerNetServerHandler == null || !PeerChannels.canSend(player)) {
            return;
        }
        NetworkManager manager = player.playerNetServerHandler.netManager;
        Subscription subscription = subscriptions.subscribers.get(manager);
        if (subscription == null) {
            return;
        }
        long now = System.nanoTime();
        UnknownStackDetailsPacket response;
        if (subscription.lastUnknownStackRequestNanos != 0L
            && now - subscription.lastUnknownStackRequestNanos < 1_000_000_000L) {
            response = UnknownStackDetailsPacket.failed(requestId, reportId, UnknownStackDetailsPacket.BUSY);
        } else if (!player.canCommandSenderUseCommand(2, "flamechunk") || !subscriptions.canSubscribe(player)) {
            subscription.lastUnknownStackRequestNanos = now;
            response = UnknownStackDetailsPacket.failed(requestId, reportId, UnknownStackDetailsPacket.DENIED);
        } else if (active) {
            subscription.lastUnknownStackRequestNanos = now;
            response = UnknownStackDetailsPacket.failed(requestId, reportId, UnknownStackDetailsPacket.BUSY);
        } else if (lastSnapshot == null || reportId <= 0L || reportId != lastSnapshotReportId) {
            subscription.lastUnknownStackRequestNanos = now;
            response = UnknownStackDetailsPacket.failed(requestId, reportId, UnknownStackDetailsPacket.STALE);
        } else {
            subscription.lastUnknownStackRequestNanos = now;
            List<ObservationSnapshot.StackDetail> details = lastSnapshot.observations.unknownStacks();
            response = details.isEmpty()
                ? UnknownStackDetailsPacket.failed(requestId, reportId, UnknownStackDetailsPacket.EMPTY)
                : new UnknownStackDetailsPacket(requestId, reportId, UnknownStackDetailsPacket.OK, details);
        }
        FlameChunk.network.sendTo(response, player);
    }

    public void recordTiming(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos,
        String typeName) {
        if (!active || !isServerWorkThread() || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world);
        if (dimension == null) {
            return;
        }
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        ChunkTiming timing = dimension.chunks.get(key);
        if (timing == null) {
            if (dimension.chunks.size() >= ServerConfig.maxChunksPerDimension) {
                droppedChunks++;
                return;
            }
            timing = new ChunkTiming();
            dimension.chunks.put(key, timing);
        }
        if (typeName == null) {
            timing.add(category, elapsedNanos);
        } else {
            if (timing
                .addObjectTiming(category, typeName, elapsedNanos, typeAggregateCount < MAX_TYPE_AGGREGATES_PER_SCAN)) {
                typeAggregateCount++;
            }
        }
    }

    public void recordGlobalTiming(TickCategory category, World world, long elapsedNanos) {
        if (!active || !isServerWorkThread() || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world);
        if (dimension != null) {
            dimension.global.add(category, elapsedNanos);
        }
    }

    public void accumulateGlobalObjectTiming(TickCategory category, World world, String typeName, long elapsedNanos) {
        if (!active || !isServerWorkThread() || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world);
        if (dimension != null && dimension.global
            .addObjectTiming(category, typeName, elapsedNanos, typeAggregateCount < MAX_TYPE_AGGREGATES_PER_SCAN)) {
            typeAggregateCount++;
        }
    }

    public void captureGarbageCollection() {
        long currentMillis;
        try {
            currentMillis = gcCollectionMillis();
        } catch (RuntimeException exception) {
            FlameChunk.LOG.debug("Unable to read Java garbage collection time", exception);
            return;
        }
        long elapsedMillis = Math.max(0L, currentMillis - lastGcCollectionMillis);
        lastGcCollectionMillis = currentMillis;
        if (elapsedMillis == 0L) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) {
            return;
        }
        for (World world : server.worldServers) {
            if (world != null && !world.isRemote) {
                long elapsedNanos = elapsedMillis > Long.MAX_VALUE / 1000000L ? Long.MAX_VALUE
                    : elapsedMillis * 1000000L;
                recordGlobalTiming(TickCategory.GARBAGE_COLLECTION, world, elapsedNanos);
                return;
            }
        }
    }

    public long gcCollectionMillis() {
        long totalMillis = 0L;
        for (GarbageCollectorMXBean bean : GC_BEANS) {
            long collectionMillis = bean.getCollectionTime();
            if (collectionMillis > 0L) {
                totalMillis = Long.MAX_VALUE - totalMillis < collectionMillis ? Long.MAX_VALUE
                    : totalMillis + collectionMillis;
            }
        }
        return totalMillis;
    }

    public DimensionTimings getDimension(World world) {
        int dimensionId = world.provider.dimensionId;
        DimensionTimings dimension = dimensions.get(dimensionId);
        if (dimension == null) {
            if (dimensions.size() >= ServerConfig.maxDimensions) {
                droppedDimensions++;
                return null;
            }
            dimension = new DimensionTimings(dimensionId, world);
            dimensions.put(dimensionId, dimension);
        }
        return dimension;
    }

    public static class DimensionTimings {

        public final int dimensionId;
        public final World world;
        public final ChunkTiming global = new ChunkTiming();
        public final Long2ObjectOpenHashMap<ChunkTiming> chunks = new Long2ObjectOpenHashMap<>();

        public DimensionTimings(int dimensionId, World world) {
            this.dimensionId = dimensionId;
            this.world = world;
        }
    }

    public static class PendingRequest {

        public final EntityPlayerMP player;
        public final int seconds;

        public PendingRequest(EntityPlayerMP player, int seconds) {
            this.player = player;
            this.seconds = seconds;
        }
    }
}
