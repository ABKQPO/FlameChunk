package com.hfstudio.flamechunk.client.integration;

import java.util.List;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.gtnewhorizons.navigator.api.model.buttons.ButtonManager;
import com.gtnewhorizons.navigator.api.model.layers.LayerManager;
import com.gtnewhorizons.navigator.api.model.layers.LayerRenderer;
import com.gtnewhorizons.navigator.api.model.layers.UniversalLayerRenderer;

/**
 * Exposes a one-shot FlameChunk action through Navigator's map button list.
 *
 * <p>
 * The action layer has an empty renderer so the button is visible to Navigator without creating another map
 * overlay. Its button deliberately does not become an active Navigator layer: scan and clear are commands, while the
 * heatmap visibility remains controlled by {@link NavigatorHeatmapLayer}.
 * </p>
 */
public class NavigatorActionLayer extends LayerManager {

    public static final NavigatorActionLayer SCAN = new NavigatorActionLayer(
        "flamechunk.client.map.scan",
        new ResourceLocation("flamechunk", "textures/icons/scan.png"),
        MapOverlayControls::toggleScan);
    public static final NavigatorActionLayer CLEAR = new NavigatorActionLayer(
        "flamechunk.client.map.clear",
        new ResourceLocation("flamechunk", "textures/icons/clear.png"),
        MapOverlayControls::clear);

    public static final List<NavigatorActionLayer> BUTTONS = List.of(SCAN, CLEAR);

    private NavigatorActionLayer(String textKey, ResourceLocation icon, Runnable action) {
        super(new ActionButton(textKey, icon, action));
    }

    @Override
    protected LayerRenderer addLayerRenderer(LayerManager manager, SupportedMods mod) {
        if (mod != SupportedMods.XaeroWorldMap) {
            return null;
        }
        return new UniversalLayerRenderer(manager);
    }

    /** Navigator button that invokes a command without toggling a rendered layer. */
    public static class ActionButton extends ButtonManager {

        private final String textKey;
        private final ResourceLocation icon;
        private final Runnable action;

        public ActionButton(String textKey, ResourceLocation icon, Runnable action) {
            this.textKey = textKey;
            this.icon = icon;
            this.action = action;
        }

        @Override
        public ResourceLocation getIcon(SupportedMods mod, String theme) {
            return icon;
        }

        @Override
        public String getButtonText() {
            return StatCollector.translateToLocal(textKey);
        }

        @Override
        public void toggle() {
            action.run();
        }

        @Override
        public void activate() {
            action.run();
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
