package com.hfstudio.flamechunk.server.integration;

import net.minecraft.world.World;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.Optional;

public class AppliedEnergisticsBridge {

    public static long begin() {
        return PerformanceSampler.beginTiming();
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static void recordMachineTick(IGridNode node, Object machine, long startedAt) {
        if (startedAt == 0L || node == null) {
            return;
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        DimensionalCoord location = location(node);
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        PerformanceSampler.recordBlockTiming(
            TickCategory.AE2_NETWORK,
            world,
            location.x,
            location.y,
            location.z,
            PerformanceSampler.workTypeName(machine == null ? null : machine.getClass()),
            elapsedNanos);
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static void recordGridTick(IGridNode pivot, String typeName, long startedAt) {
        if (startedAt == 0L || pivot == null) {
            return;
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        DimensionalCoord location = location(pivot);
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        PerformanceSampler.recordBlockTiming(
            TickCategory.AE2_NETWORK,
            world,
            location.x,
            location.y,
            location.z,
            typeName,
            elapsedNanos);
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static int enterWork(IGridNode node, Object machine) {
        if (!PerformanceSampler.isActive()) {
            return 0;
        }
        DimensionalCoord location = node == null ? null : location(node);
        World world = location == null ? null : location.getWorld();
        return PerformanceSampler.enterWork(
            TickCategory.AE2_NETWORK,
            world,
            PerformanceSampler.workTypeName(machine == null ? null : machine.getClass()));
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static void leaveWork(int token) {
        PerformanceSampler.leaveWork(token);
    }

    @Optional.Method(modid = "appliedenergistics2")
    public static DimensionalCoord location(IGridNode node) {
        try {
            return node.getGridBlock() == null ? null
                : node.getGridBlock()
                    .getLocation();
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }
}
