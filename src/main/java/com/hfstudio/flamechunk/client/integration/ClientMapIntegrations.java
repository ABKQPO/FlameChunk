package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.api.client.map.MapOverlayApi;
import com.hfstudio.flamechunk.common.integration.Mods;

public class ClientMapIntegrations {

    public final List<MapOverlaySink> sinks = new ArrayList<>();

    public static boolean hasMapIntegration() {
        return Mods.hasMapIntegration() || MapOverlayApi.hasRegisteredIntegration();
    }

    public void initialize() {
        for (MapOverlaySink integration : MapOverlayApi.registeredIntegrations()) {
            register(integration);
        }
        if (Mods.JourneyMap6.isModLoaded() && Mods.JourneyMapApi.isModLoaded()) {
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

    public void register(MapOverlaySink sink) {
        if (!sinks.contains(sink)) {
            sinks.add(sink);
            try {
                sink.publish(ClientMapOverlayState.get());
            } catch (RuntimeException | LinkageError exception) {
                sinks.remove(sink);
                FlameChunk.LOG.warn("Unable to initialize a map heatmap integration", exception);
            }
        }
    }

    public void unregister(MapOverlaySink sink) {
        if (sinks.remove(sink)) {
            try {
                sink.clear();
            } catch (RuntimeException | LinkageError exception) {
                FlameChunk.LOG.debug("Unable to clear a removed map integration", exception);
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
