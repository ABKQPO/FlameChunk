package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.integration.Mods;

public class ClientMapIntegrations {

    private final List<MapOverlaySink> sinks = new ArrayList<>();

    public void initialize() {
        if (Mods.JourneyMap6.isModLoaded()) {
            try {
                sinks.add(JourneyMap6Adapter.createBridge());
                FlameChunk.LOG.info("JourneyMap 6 heatmap integration enabled");
            } catch (LinkageError error) {
                FlameChunk.LOG.warn("JourneyMap 6 heatmap integration is unavailable", error);
            }
        } else if (Mods.JourneyMap5.isModLoaded()) {
            FlameChunk.LOG.info("JourneyMap 5 heatmap integration enabled");
        }
    }

    public void publish(MapOverlayModel model) {
        ClientMapOverlayState.publish(model);
        Iterator<MapOverlaySink> iterator = sinks.iterator();
        while (iterator.hasNext()) {
            MapOverlaySink sink = iterator.next();
            try {
                sink.publish(model);
            } catch (RuntimeException | LinkageError exception) {
                FlameChunk.LOG.warn("Disabling a failed map heatmap integration", exception);
                iterator.remove();
            }
        }
    }

    public void clear() {
        ClientMapOverlayState.clear();
        Iterator<MapOverlaySink> iterator = sinks.iterator();
        while (iterator.hasNext()) {
            MapOverlaySink sink = iterator.next();
            try {
                sink.clear();
            } catch (RuntimeException | LinkageError exception) {
                FlameChunk.LOG.warn("Disabling a failed map heatmap integration", exception);
                iterator.remove();
            }
        }
    }

}
