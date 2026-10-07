package com.hfstudio.flamechunk.client.render;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.integration.Mods;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class WorldPerformanceOverlay {

    private static final int MAX_MARKERS = 96;
    private static final int MAX_BEAMS = 32;
    private static final int MAX_LABELS = 16;
    private static final int CAPTURE_INTERVAL_FRAMES = 4;
    private static final double MAX_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final float MINIMUM_VISIBLE_MSPT = 0.05F;

    private final ClientSnapshotStorage storage;
    public List<ObjectHotspot> hotspots = Collections.emptyList();
    public Frustrum frustum;
    private final Marker[] markers = new Marker[MAX_MARKERS];
    private final Beam[] beams = new Beam[MAX_BEAMS];
    private final ColorCalculator colorCalculator = new ColorCalculator();
    private ScanSnapshot indexedSnapshot;
    private WeakChunkSnapshot indexedWeakSnapshot;
    private int indexedDimension = Integer.MIN_VALUE;
    private long sampledTicks;
    private int markerCount;
    private int beamCount;
    private int captureFrame;

    public WorldPerformanceOverlay(ClientSnapshotStorage storage) {
        this.storage = storage;
        for (int index = 0; index < markers.length; index++) {
            markers[index] = new Marker();
        }
        for (int index = 0; index < beams.length; index++) {
            beams[index] = new Beam();
        }
    }

    public void clear() {
        clearOverlay();
        hotspots = Collections.emptyList();
        indexedSnapshot = null;
        indexedWeakSnapshot = null;
        indexedDimension = Integer.MIN_VALUE;
        sampledTicks = 0L;
        captureFrame = 0;
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        Entity viewEntity = minecraft.renderViewEntity;
        if (!ClientConfig.worldOverlayEnabled || !Mods.hasMapIntegration()
            || storage == null
            || world == null
            || viewEntity == null) {
            if (markerCount != 0 || beamCount != 0 || indexedSnapshot != null || indexedWeakSnapshot != null) {
                clear();
            }
            return;
        }
        ScanSnapshot snapshot = storage.getSnapshot();
        int dimensionId = world.provider.dimensionId;
        WeakChunkSnapshot weakSnapshot = storage.getWeakSnapshot(dimensionId);
        if ((snapshot == null || snapshot.getSampledTicks() <= 0L) && weakSnapshot == null) {
            if (markerCount != 0 || beamCount != 0 || indexedSnapshot != null || indexedWeakSnapshot != null) {
                clear();
            }
            return;
        }
        if (snapshot != indexedSnapshot || weakSnapshot != indexedWeakSnapshot || dimensionId != indexedDimension) {
            rebuildIndex(snapshot, weakSnapshot, dimensionId);
        }
        if (snapshot != null && sampledTicks > 0L && ++captureFrame >= CAPTURE_INTERVAL_FRAMES) {
            captureFrame = 0;
            captureMarkers(world, viewEntity);
        }
        renderMarkers(viewEntity, event.partialTicks);
    }

    private void rebuildIndex(ScanSnapshot snapshot, WeakChunkSnapshot weakSnapshot, int dimensionId) {
        clearOverlay();
        hotspots = Collections.emptyList();
        if (snapshot != null && snapshot.getSampledTicks() > 0L) {
            for (DimensionSnapshot dimension : snapshot.getDimensions()) {
                if (dimension.getDimensionId() != dimensionId) {
                    continue;
                }
                hotspots = dimension.objectHotspots;
                break;
            }
        }
        sampledTicks = snapshot == null ? 0L : snapshot.getSampledTicks();
        addWeakBeams(weakSnapshot);
        addPerformanceBeams(snapshot, dimensionId);
        indexedSnapshot = snapshot;
        indexedWeakSnapshot = weakSnapshot;
        indexedDimension = dimensionId;
    }

    private void captureMarkers(World world, Entity viewEntity) {
        clearMarkers();
        for (ObjectHotspot hotspot : hotspots) {
            if (markerCount >= MAX_MARKERS) {
                break;
            }
            Entity entity = null;
            TileEntity tileEntity = null;
            if (hotspot.category == TickCategory.ENTITY) {
                entity = world.getEntityByID(hotspot.entityId);
                if (entity == null || entity.isDead
                    || entity.boundingBox == null
                    || entity.getDistanceSqToEntity(viewEntity) > MAX_DISTANCE_SQUARED) {
                    continue;
                }
                String typeName = EntityList.getEntityString(entity);
                if (typeName == null) {
                    typeName = entity.getClass()
                        .getSimpleName();
                }
                // Legacy spawn packets do not synchronize non-player UUIDs.
                if (!typeName.equals(hotspot.typeName)) {
                    continue;
                }
            } else {
                if (distanceSquared(hotspot.x + 0.5D, hotspot.y + 0.5D, hotspot.z + 0.5D, viewEntity)
                    > MAX_DISTANCE_SQUARED
                    || !world.getChunkProvider()
                        .chunkExists(hotspot.x >> 4, hotspot.z >> 4)) {
                    continue;
                }
                if (hotspot.category == TickCategory.BLOCK_ENTITY) {
                    tileEntity = world.getTileEntity(hotspot.x, hotspot.y, hotspot.z);
                    if (tileEntity == null || tileEntity.isInvalid()
                        || !tileEntity.getClass()
                            .getSimpleName()
                            .equals(hotspot.typeName)) {
                        continue;
                    }
                } else if (!world.getBlock(hotspot.x, hotspot.y, hotspot.z)
                    .getClass()
                    .getSimpleName()
                    .equals(hotspot.typeName)) {
                        continue;
                    }
            }
            addMarker(entity, tileEntity, hotspot);
        }
    }

    private void clearMarkers() {
        markerCount = 0;
        for (Marker marker : markers) {
            marker.entity = null;
            marker.tileEntity = null;
            marker.typeName = null;
            marker.color = 0;
            marker.bounds = null;
            marker.members = 0;
        }
    }

    private void clearOverlay() {
        clearMarkers();
        beamCount = 0;
    }

    private void addWeakBeams(WeakChunkSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (ChunkEntry chunk : snapshot.getChunks()) {
            if (beamCount >= MAX_BEAMS) {
                return;
            }
            addBeam(chunk.getChunkX(), chunk.getChunkZ(), ColorUtils.rgb(ColorUtils.HEAT_HIGH), Double.MAX_VALUE);
        }
    }

    private void addPerformanceBeams(ScanSnapshot snapshot, int dimensionId) {
        if (snapshot == null || sampledTicks <= 0L) {
            return;
        }
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            if (dimension.getDimensionId() != dimensionId) {
                continue;
            }
            for (ChunkSnapshot chunk : dimension.getChunks()) {
                double totalNanos = 0.0D;
                long[] categoryNanos = chunk.getNanos();
                int excludedCategory = TickCategory.BLOCK_UPDATE.ordinal();
                for (int categoryIndex = 0; categoryIndex < TickCategory.COUNT; categoryIndex++) {
                    if (categoryIndex != excludedCategory) {
                        totalNanos += categoryNanos[categoryIndex];
                    }
                }
                float mspt = (float) (totalNanos / 1000000.0D / sampledTicks);
                if (mspt >= MINIMUM_VISIBLE_MSPT) {
                    addBeam(
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        colorCalculator.colorForMspt(mspt, ClientConfig.heatThresholdMspt),
                        mspt);
                }
            }
            break;
        }
    }

    private void addBeam(int chunkX, int chunkZ, int color, double score) {
        for (int index = 0; index < beamCount; index++) {
            Beam beam = beams[index];
            if (beam.chunkX == chunkX && beam.chunkZ == chunkZ) {
                return;
            }
        }
        int targetIndex = beamCount;
        if (targetIndex >= MAX_BEAMS) {
            targetIndex = 0;
            for (int index = 1; index < beamCount; index++) {
                if (beams[index].score < beams[targetIndex].score) {
                    targetIndex = index;
                }
            }
            if (beams[targetIndex].score >= score) {
                return;
            }
        } else {
            beamCount++;
        }
        Beam beam = beams[targetIndex];
        beam.chunkX = chunkX;
        beam.chunkZ = chunkZ;
        beam.color = color;
        beam.score = score;
    }

    private void addMarker(Entity entity, TileEntity tileEntity, ObjectHotspot hotspot) {
        if (sampledTicks <= 0L) {
            return;
        }
        float mspt = (float) hotspot.calculateMspt(sampledTicks);
        if (!ClientConfig.worldOverlayShowAll && mspt < MINIMUM_VISIBLE_MSPT) {
            return;
        }
        if (entity instanceof EntityItem item) {
            for (int index = 0; index < markerCount; index++) {
                Marker cluster = markers[index];
                if (cluster.entity instanceof EntityItem clusteredItem
                    && item.getDistanceSqToEntity(clusteredItem) <= 4.0D) {
                    cluster.members++;
                    cluster.mspt += mspt;
                    cluster.bounds = cluster.bounds.func_111270_a(entity.boundingBox);
                    updateMarkerLabel(cluster);
                    return;
                }
            }
        }
        Marker marker = markers[markerCount++];
        marker.entity = entity;
        marker.tileEntity = tileEntity;
        marker.sourceName = displayType(hotspot.typeName);
        marker.members = 1;
        marker.mspt = mspt;
        marker.bounds = entity == null
            ? AxisAlignedBB
                .getBoundingBox(hotspot.x, hotspot.y, hotspot.z, hotspot.x + 1.0D, hotspot.y + 1.0D, hotspot.z + 1.0D)
            : entity.boundingBox.copy();
        updateMarkerLabel(marker);
    }

    public void updateMarkerLabel(Marker marker) {
        marker.typeName = StatCollector.translateToLocalFormatted(
            "flamechunk.client.worldHotspot",
            marker.sourceName,
            marker.members,
            String.format(Locale.ENGLISH, "%.3f", marker.mspt));
        marker.color = colorCalculator.colorForMspt(marker.mspt, ClientConfig.heatThresholdMspt);
    }

    private void renderMarkers(Entity viewEntity, float partialTicks) {
        if (markerCount == 0 && beamCount == 0) {
            return;
        }
        double cameraX = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double cameraY = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double cameraZ = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;
        if (frustum == null) {
            frustum = new Frustrum();
        } else {
            ClippingHelperImpl.getInstance();
        }
        frustum.setPosition(cameraX, cameraY, cameraZ);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);
        GL11.glDepthMask(false);
        GL11.glBegin(GL11.GL_LINES);
        try {
            renderGeometry(viewEntity, cameraX, cameraY, cameraZ);
        } finally {
            GL11.glEnd();
            GL11.glPopAttrib();
        }
        renderLabels(viewEntity, cameraX, cameraY, cameraZ);
    }

    public void renderGeometry(Entity viewEntity, double cameraX, double cameraY, double cameraZ) {
        for (int index = 0; index < markerCount; index++) {
            Marker marker = markers[index];
            if (!isVisible(marker, viewEntity)) {
                continue;
            }
            ColorUtils.applyGlColor(marker.color, ColorUtils.HOTSPOT_OPACITY);
            drawBox(marker.currentBounds(), cameraX, cameraY, cameraZ);
        }
        double beamBottom = Math.floor(viewEntity.posY) - 6.0D - cameraY;
        double beamTop = Math.floor(viewEntity.posY) + 12.0D - cameraY;
        for (int index = 0; index < beamCount; index++) {
            Beam beam = beams[index];
            double worldX = beam.chunkX * 16.0D + 8.0D;
            double worldZ = beam.chunkZ * 16.0D + 8.0D;
            if (distanceSquared(worldX, viewEntity.posY, worldZ, viewEntity) > 128.0D * 128.0D
                || !frustum.isBoundingBoxInFrustum(
                    AxisAlignedBB.getBoundingBox(
                        worldX - 1.0D,
                        viewEntity.posY - 6.0D,
                        worldZ - 1.0D,
                        worldX + 1.0D,
                        viewEntity.posY + 12.0D,
                        worldZ + 1.0D))) {
                continue;
            }
            ColorUtils.applyGlColor(beam.color, ColorUtils.BEAM_OPACITY);
            double minX = (beam.chunkX << 4) + 7.5D - cameraX;
            double maxX = minX + 1.0D;
            double minZ = (beam.chunkZ << 4) + 7.5D - cameraZ;
            double maxZ = minZ + 1.0D;
            line(minX, beamBottom, minZ, minX, beamTop, minZ);
            line(maxX, beamBottom, minZ, maxX, beamTop, minZ);
            line(maxX, beamBottom, maxZ, maxX, beamTop, maxZ);
            line(minX, beamBottom, maxZ, minX, beamTop, maxZ);
        }
    }

    private void renderLabels(Entity viewEntity, double cameraX, double cameraY, double cameraZ) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int labels = Math.min(markerCount, MAX_LABELS);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_COLOR_BUFFER_BIT);
        try {
            renderLabelBatch(minecraft, viewEntity, cameraX, cameraY, cameraZ, labels);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void renderLabelBatch(Minecraft minecraft, Entity viewEntity, double cameraX, double cameraY, double cameraZ,
        int labels) {
        for (int index = 0; index < labels; index++) {
            Marker marker = markers[index];
            if (!isVisible(marker, viewEntity) || marker.typeName == null) {
                continue;
            }
            double x;
            double y;
            double z;
            AxisAlignedBB bounds = marker.currentBounds();
            x = (bounds.minX + bounds.maxX) * 0.5D - cameraX;
            y = bounds.maxY - cameraY + 0.15D;
            z = (bounds.minZ + bounds.maxZ) * 0.5D - cameraZ;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                GL11.glRotatef(-viewEntity.rotationYaw, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(viewEntity.rotationPitch, 1.0F, 0.0F, 0.0F);
                GL11.glScalef(-0.025F, -0.025F, 0.025F);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                int textWidth = minecraft.fontRenderer.getStringWidth(marker.typeName);
                minecraft.fontRenderer.drawStringWithShadow(marker.typeName, -textWidth / 2, 0, marker.color);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    private static void drawBox(AxisAlignedBB box, double cameraX, double cameraY, double cameraZ) {
        double minX = box.minX - cameraX;
        double minY = box.minY - cameraY;
        double minZ = box.minZ - cameraZ;
        double maxX = box.maxX - cameraX;
        double maxY = box.maxY - cameraY;
        double maxZ = box.maxZ - cameraZ;
        line(minX, minY, minZ, maxX, minY, minZ);
        line(maxX, minY, minZ, maxX, minY, maxZ);
        line(maxX, minY, maxZ, minX, minY, maxZ);
        line(minX, minY, maxZ, minX, minY, minZ);
        line(minX, maxY, minZ, maxX, maxY, minZ);
        line(maxX, maxY, minZ, maxX, maxY, maxZ);
        line(maxX, maxY, maxZ, minX, maxY, maxZ);
        line(minX, maxY, maxZ, minX, maxY, minZ);
        line(minX, minY, minZ, minX, maxY, minZ);
        line(maxX, minY, minZ, maxX, maxY, minZ);
        line(maxX, minY, maxZ, maxX, maxY, maxZ);
        line(minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    private static double distanceSquared(double x, double y, double z, Entity entity) {
        double dx = x - entity.posX;
        double dy = y - entity.posY;
        double dz = z - entity.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isVisible(Marker marker, Entity viewEntity) {
        if (!marker.isPresent()) {
            return false;
        }
        AxisAlignedBB bounds = marker.currentBounds();
        return distanceSquared(
            (bounds.minX + bounds.maxX) * 0.5D,
            (bounds.minY + bounds.maxY) * 0.5D,
            (bounds.minZ + bounds.maxZ) * 0.5D,
            viewEntity) <= MAX_DISTANCE_SQUARED && frustum.isBoundingBoxInFrustum(bounds);
    }

    private static String displayType(String typeName) {
        return typeName.length() <= 30 ? typeName : typeName.substring(0, 27) + "...";
    }

    public static class Marker {

        public Entity entity;
        public TileEntity tileEntity;
        public String typeName;
        public int color;
        public String sourceName;
        public int members;
        public float mspt;
        public AxisAlignedBB bounds;

        public boolean isPresent() {
            return bounds != null && (entity == null || !entity.isDead && entity.boundingBox != null)
                && (tileEntity == null || !tileEntity.isInvalid());
        }

        public AxisAlignedBB currentBounds() {
            return entity != null && members == 1 ? entity.boundingBox : bounds;
        }

    }

    public static class Beam {

        public int chunkX;
        public int chunkZ;
        public int color;
        public double score;
    }
}
