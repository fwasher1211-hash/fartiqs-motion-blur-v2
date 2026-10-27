package com.fartiqs.motionblur;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class FartiqsMotionBlur implements ClientModInitializer {
    public static KeyBinding openMenu;

    @Override
    public void onInitializeClient() {
        Config.load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fartiqsmotionblur", "main"));
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fartiqsmotionblur.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MotionBlurScreen(null));
                }
            }
        });
    }
}
