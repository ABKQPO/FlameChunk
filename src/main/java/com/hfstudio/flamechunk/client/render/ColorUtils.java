package com.hfstudio.flamechunk.client.render;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.color.ColorResource;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;

public class ColorUtils {

    public static final ColorResource.Factory color = new ColorResource.Factory("flamechunk");

    public static final ColorResource TEXT_PRIMARY = color.rgb("textPrimary", "0xFFFFFF");
    public static final ColorResource TEXT_SECONDARY = color.rgb("textSecondary", "0xE5E7EB");
    public static final ColorResource TEXT_MUTED = color.rgb("textMuted", "0xC0C0C0");
    public static final ColorResource TEXT_WARNING = color.rgb("textWarning", "0xFBBF24");
    public static final ColorResource TEXT_WEAK_CHUNK = color.rgb("textWeakChunk", "0xC026D3");
    public static final ColorResource PANEL_BACKGROUND = color.argb("panelBackground", "0xC0000000");
    public static final ColorResource SELECTION_BACKGROUND = color.argb("selectionBackground", "0x80404040");
    public static final ColorResource SCROLL_TRACK = color.argb("scrollTrack", "0x60404040");
    public static final ColorResource SCROLL_THUMB = color.argb("scrollThumb", "0xC0E5E7EB");
    public static final ColorResource HEAT_LOW = color.rgb("heatLow", "0x35B779");
    public static final ColorResource HEAT_MEDIUM = color.rgb("heatMedium", "0xFDE047");
    public static final ColorResource HEAT_HIGH = color.rgb("heatHigh", "0xEF4444");
    public static final ColorResource WEAK_IDLE = color.rgb("weakIdle", "0x9CA3AF");
    public static final ColorResource WEAK_CLEAR = color.rgb("weakClear", "0xF97316");
    public static final ColorResource WEAK_CRITICAL = color.rgb("weakCritical", "0xA855F7");
    public static final ColorResource TICKET_PLAYER = color.rgb("ticketPlayer", "0x38BDF8");
    public static final ColorResource TICKET_ENTITY = color.rgb("ticketEntity", "0xA78BFA");
    public static final ColorResource TICKET_MOD = color.rgb("ticketMod", "0xFB923C");

    public static final int RGB_MASK = 0xFFFFFF;
    public static final int ALPHA_MASK = 0xFF000000;
    public static final float HOTSPOT_OPACITY = 0.9F;
    public static final float FLAME_OPACITY = 0.7F;
    public static final float BEAM_OPACITY = 0.65F;
    public static final float TICKET_MINIMUM_OPACITY = 0.8F;
    public static final float TICKET_STROKE_OPACITY = 0.9F;
    public static final float WEAK_IDLE_OPACITY = 0.18F;

    public static int rgb(ColorResource resource) {
        return resource.getColor() & RGB_MASK;
    }

    public static int opaque(int rgb) {
        return ALPHA_MASK | (rgb & RGB_MASK);
    }

    public static int heatColor(float mspt, float budgetMspt) {
        float ratio = normalize(mspt, budgetMspt);
        return ratio < 0.5F ? blend(rgb(HEAT_LOW), rgb(HEAT_MEDIUM), ratio * 2.0F)
            : blend(rgb(HEAT_MEDIUM), rgb(HEAT_HIGH), (ratio - 0.5F) * 2.0F);
    }

    public static float normalize(float mspt, float budgetMspt) {
        if (Float.isNaN(mspt) || mspt <= 0.0F || Float.isNaN(budgetMspt) || budgetMspt <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, mspt / budgetMspt);
    }

    public static int blend(int first, int second, float ratio) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        int red = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * clamped);
        int green = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * clamped);
        int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * clamped);
        return red << 16 | green << 8 | blue;
    }

    public static int weakChunkColor(int entityCount) {
        if (entityCount >= WeakChunkSnapshot.ENTITY_PURPLE_THRESHOLD) {
            return rgb(WEAK_CRITICAL);
        }
        if (entityCount >= WeakChunkSnapshot.ENTITY_RED_THRESHOLD) {
            return rgb(HEAT_HIGH);
        }
        return entityCount >= WeakChunkSnapshot.ENTITY_CLEAR_THRESHOLD ? rgb(WEAK_CLEAR) : rgb(HEAT_MEDIUM);
    }

    public static float heatOpacity(float maximumOpacity, float mspt, float budgetMspt) {
        return maximumOpacity * (0.55F + 0.45F * normalize(mspt, budgetMspt));
    }

    public static int ticketSourceColor(int sourceCode) {
        return switch (sourceCode) {
            case 2 -> rgb(TICKET_PLAYER);
            case 3 -> rgb(TICKET_ENTITY);
            case 4 -> rgb(TICKET_MOD);
            default -> 0;
        };
    }

    public static int alpha(float opacity) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F);
    }

    public static void applyGlColor(int rgb, float opacity) {
        GL11.glColor4f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, opacity);
    }
}
