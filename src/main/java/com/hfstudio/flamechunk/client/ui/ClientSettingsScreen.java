package com.hfstudio.flamechunk.client.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.config.ReportOutputMode;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class ClientSettingsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final Map<Integer, Integer> originalButtonY = new HashMap<>();
    private int[] categoryButtonIds;
    private int scrollOffset;
    private int maximumScrollOffset;

    public ClientSettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int center = width / 2;
        int left = center - 150;
        int right = center + 8;
        int top = 40;
        int row = 24;
        buttonList.add(new GuiButton(0, left, top, 142, 20, ""));
        buttonList.add(new GuiButton(1, left, top + row, 142, 20, ""));
        buttonList.add(new GuiButton(2, left, top + row * 3, 68, 20, ""));
        buttonList.add(new GuiButton(3, left + 74, top + row * 3, 68, 20, ""));
        buttonList.add(new GuiButton(4, left, top + row * 4, 68, 20, ""));
        buttonList.add(new GuiButton(5, left + 74, top + row * 4, 68, 20, ""));
        buttonList.add(new GuiButton(6, left, top + row * 2, 142, 20, ""));
        buttonList.add(new GuiButton(7, left, top + row * 5, 142, 20, ""));
        buttonList.add(new GuiButton(8, right, top, 142, 20, ""));
        buttonList.add(new GuiButton(9, right, top + row, 142, 20, ""));
        buttonList.add(new GuiButton(10, right, top + row * 2, 142, 20, ""));
        buttonList.add(new GuiButton(11, right, top + row * 3, 142, 20, ""));
        buttonList.add(new GuiButton(12, right, top + row * 4, 142, 20, ""));
        buttonList.add(new GuiButton(13, right, top + row * 5, 142, 20, ""));
        buttonList.add(new GuiButton(14, right, top + row * 6, 142, 20, ""));
        buttonList.add(new GuiButton(15, left, top + row * 6, 142, 20, ""));
        buttonList.add(new GuiButton(16, left, top + row * 7, 142, 20, ""));
        buttonList.add(new GuiButton(17, left, top + row * 8, 142, 20, ""));
        buttonList.add(new GuiButton(18, left, top + row * 9, 142, 20, ""));
        buttonList.add(new GuiButton(19, left, top + row * 10, 142, 20, ""));
        categoryButtonIds = new int[TickCategory.COUNT];
        for (int index = 0; index < categoryButtonIds.length; index++) {
            int id = 20 + index;
            categoryButtonIds[index] = id;
            int y = top + row * (index + 7);
            buttonList.add(new GuiButton(id, right, y, 142, 20, ""));
        }
        buttonList.add(new GuiButton(40, center - 96, height - 28, 90, 20, ""));
        buttonList.add(new GuiButton(41, center + 6, height - 28, 90, 20, ""));
        originalButtonY.clear();
        int maximumContentBottom = top;
        for (GuiButton button : buttonList) {
            originalButtonY.put(button.id, button.yPosition);
            if (button.id != 40 && button.id != 41) {
                maximumContentBottom = Math.max(maximumContentBottom, button.yPosition + button.height);
            }
        }
        maximumScrollOffset = Math.max(0, maximumContentBottom - (height - 34));
        scrollOffset = bounded(scrollOffset, 0, maximumScrollOffset);
        updateButtonPositions();
        updateButtonLabels();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                ClientConfig.scanSeconds = bounded(
                    ClientConfig.scanSeconds - 1,
                    ClientConfig.MIN_SCAN_SECONDS,
                    ClientConfig.MAX_SCAN_SECONDS);
                break;
            case 1:
                ClientConfig.scanSeconds = bounded(
                    ClientConfig.scanSeconds + 1,
                    ClientConfig.MIN_SCAN_SECONDS,
                    ClientConfig.MAX_SCAN_SECONDS);
                break;
            case 2:
                ClientConfig.heatThresholdMspt = Math
                    .max(ClientConfig.MIN_HEAT_THRESHOLD, ClientConfig.heatThresholdMspt - 0.25F);
                break;
            case 3:
                ClientConfig.heatThresholdMspt = Math
                    .min(ClientConfig.MAX_HEAT_THRESHOLD, ClientConfig.heatThresholdMspt + 0.25F);
                break;
            case 4:
                ClientConfig.heatAlpha = Math.max(ClientConfig.MIN_HEAT_ALPHA, ClientConfig.heatAlpha - 0.05F);
                break;
            case 5:
                ClientConfig.heatAlpha = Math.min(ClientConfig.MAX_HEAT_ALPHA, ClientConfig.heatAlpha + 0.05F);
                break;
            case 6:
                ClientConfig.relativeHeatColor = !ClientConfig.relativeHeatColor;
                break;
            case 7:
                ClientConfig.showWeakIdleChunks = !ClientConfig.showWeakIdleChunks;
                break;
            case 8:
                ClientConfig.tooltipCoordinates = !ClientConfig.tooltipCoordinates;
                break;
            case 9:
                ClientConfig.tooltipEntityCount = !ClientConfig.tooltipEntityCount;
                break;
            case 10:
                ClientConfig.tooltipTotal = !ClientConfig.tooltipTotal;
                break;
            case 11:
                ClientConfig.tooltipLoadLevel = !ClientConfig.tooltipLoadLevel;
                break;
            case 12:
                ClientConfig.tooltipTicketSource = !ClientConfig.tooltipTicketSource;
                break;
            case 13:
                ClientConfig.tooltipCategoryNamesShort = !ClientConfig.tooltipCategoryNamesShort;
                break;
            case 14:
                ClientConfig.tooltipCategoryUnits = !ClientConfig.tooltipCategoryUnits;
                break;
            case 15:
                ClientConfig.showLoaderSources = !ClientConfig.showLoaderSources;
                break;
            case 16:
                ClientConfig.reportOutputMode = nextReportOutputMode(ClientConfig.reportOutputMode);
                break;
            case 17:
                ClientConfig.worldOverlayEnabled = !ClientConfig.worldOverlayEnabled;
                break;
            case 18:
                ClientConfig.worldOverlayShowAll = !ClientConfig.worldOverlayShowAll;
                break;
            case 19:
                ClientConfig.liveUpdates = !ClientConfig.liveUpdates;
                MapOverlayControls.subscriptionDenied = false;
                break;
            case 40:
                mc.displayGuiScreen(parent);
                return;
            case 41:
                ClientConfig.resetToDefaults();
                break;
            default:
                toggleCategory(button.id);
                break;
        }
        if (parent instanceof DiagnosticScreen diagnosticScreen) {
            diagnosticScreen.refreshOverlay();
        }
        updateButtonLabels();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("flamechunk.client.settings"),
            width / 2,
            16,
            ColorUtils.TEXT_PRIMARY.getColor());
        if (maximumScrollOffset > 0) {
            int trackTop = 36;
            int trackHeight = Math.max(1, height - 70);
            int thumbHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + maximumScrollOffset));
            int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / maximumScrollOffset;
            drawRect(width - 7, trackTop, width - 4, trackTop + trackHeight, ColorUtils.SCROLL_TRACK.getColor());
            drawRect(width - 7, thumbTop, width - 4, thumbTop + thumbHeight, ColorUtils.SCROLL_THUMB.getColor());
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollOffset = bounded(scrollOffset + (wheel > 0 ? -24 : 24), 0, maximumScrollOffset);
            updateButtonPositions();
        }
    }

    @Override
    public void onGuiClosed() {
        ClientConfig.save();
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void toggleCategory(int id) {
        int index = id - 20;
        if (index < 0 || index >= TickCategory.COUNT) {
            return;
        }
        TickCategory category = TickCategory.values()[index];
        ClientConfig.tooltipCategories.toggle(category);
    }

    private void updateButtonLabels() {
        label(0, translated("flamechunk.settings.scanDecrease", ClientConfig.scanSeconds));
        label(1, translated("flamechunk.settings.scanIncrease", ClientConfig.scanSeconds));
        label(
            2,
            translated(
                "flamechunk.settings.thresholdDecrease",
                String.format(Locale.ENGLISH, "%.2f", ClientConfig.heatThresholdMspt)));
        label(
            3,
            translated(
                "flamechunk.settings.thresholdIncrease",
                String.format(Locale.ENGLISH, "%.2f", ClientConfig.heatThresholdMspt)));
        label(
            4,
            translated(
                "flamechunk.settings.alphaDecrease",
                String.format(Locale.ENGLISH, "%.2f", ClientConfig.heatAlpha)));
        label(
            5,
            translated(
                "flamechunk.settings.alphaIncrease",
                String.format(Locale.ENGLISH, "%.2f", ClientConfig.heatAlpha)));
        label(6, translated("flamechunk.settings.relative", enabled(ClientConfig.relativeHeatColor)));
        label(7, translated("flamechunk.settings.weak", enabled(ClientConfig.showWeakIdleChunks)));
        label(8, translated("flamechunk.settings.coordinates", enabled(ClientConfig.tooltipCoordinates)));
        label(9, translated("flamechunk.settings.entities", enabled(ClientConfig.tooltipEntityCount)));
        label(10, translated("flamechunk.settings.total", enabled(ClientConfig.tooltipTotal)));
        label(11, translated("flamechunk.settings.load", enabled(ClientConfig.tooltipLoadLevel)));
        label(12, translated("flamechunk.settings.tickets", enabled(ClientConfig.tooltipTicketSource)));
        label(13, translated("flamechunk.settings.shortCategories", enabled(ClientConfig.tooltipCategoryNamesShort)));
        label(14, translated("flamechunk.settings.units", enabled(ClientConfig.tooltipCategoryUnits)));
        label(15, translated("flamechunk.settings.loaderSources", enabled(ClientConfig.showLoaderSources)));
        label(
            16,
            translated(
                "flamechunk.settings.output",
                StatCollector.translateToLocal(
                    "flamechunk.settings.output." + ClientConfig.reportOutputMode.name()
                        .toLowerCase(Locale.ENGLISH))));
        label(17, translated("flamechunk.settings.worldOverlay", enabled(ClientConfig.worldOverlayEnabled)));
        label(18, translated("flamechunk.settings.worldOverlayShowAll", enabled(ClientConfig.worldOverlayShowAll)));
        label(19, translated("flamechunk.settings.liveUpdates", enabled(ClientConfig.liveUpdates)));
        label(40, StatCollector.translateToLocal("flamechunk.settings.done"));
        label(41, StatCollector.translateToLocal("flamechunk.settings.reset"));
        for (int index = 0; index < categoryButtonIds.length; index++) {
            TickCategory category = TickCategory.values()[index];
            label(
                categoryButtonIds[index],
                translated(
                    "flamechunk.settings.category",
                    StatCollector.translateToLocal(
                        "flamechunk.category." + category.name()
                            .toLowerCase(Locale.ENGLISH)),
                    enabled(ClientConfig.tooltipCategories.contains(category))));
        }
    }

    private void updateButtonPositions() {
        for (GuiButton button : buttonList) {
            Integer originalY = originalButtonY.get(button.id);
            if (originalY == null) {
                continue;
            }
            if (button.id == 40 || button.id == 41) {
                button.yPosition = originalY;
                button.visible = true;
                continue;
            }
            button.yPosition = originalY - scrollOffset;
            button.visible = button.yPosition >= 34 && button.yPosition + button.height <= height - 34;
        }
    }

    private ReportOutputMode nextReportOutputMode(ReportOutputMode current) {
        ReportOutputMode[] modes = ReportOutputMode.values();
        return modes[(current.ordinal() + 1) % modes.length];
    }

    private void label(int id, String value) {
        for (GuiButton button : buttonList) {
            if (button.id == id) {
                button.displayString = value;
                return;
            }
        }
    }

    public String translated(String key, String... values) {
        return StatCollector.translateToLocalFormatted(key, (Object[]) values);
    }

    private String translated(String key, int value) {
        return StatCollector.translateToLocalFormatted(key, value);
    }

    private String enabled(boolean value) {
        return StatCollector.translateToLocal(value ? "flamechunk.settings.enabled" : "flamechunk.settings.disabled");
    }

    private int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
