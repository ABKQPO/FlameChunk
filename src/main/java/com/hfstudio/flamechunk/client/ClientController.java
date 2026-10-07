package com.hfstudio.flamechunk.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.client.ui.DiagnosticScreen;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import lombok.Getter;

public class ClientController {

    public final ClientSnapshotStorage storage;
    @Getter
    public final KeyBinding diagnosticKey = new KeyBinding(
        "key.flamechunk.diagnostic",
        Keyboard.KEY_F8,
        "key.categories.flamechunk");

    public ClientController(ClientSnapshotStorage storage) {
        this.storage = storage;
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (diagnosticKey.isPressed()) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new DiagnosticScreen(storage));
        }
    }
}
