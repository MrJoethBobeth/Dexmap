package com.dexmap.input;

import com.dexmap.gui.WorldMapScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeybindingManager {
    private static KeyBinding worldMapKey;
    private static KeyBinding minimapToggleKey;

    public static void register() {
        worldMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dexmap.worldmap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.dexmap.general"
        ));

        minimapToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dexmap.toggle_minimap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.dexmap.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (worldMapKey.wasPressed()) {
                client.setScreen(new WorldMapScreen());
            }

            if (minimapToggleKey.wasPressed()) {
                // Toggle minimap visibility
                // Implementation depends on config system
            }
        });
    }
}