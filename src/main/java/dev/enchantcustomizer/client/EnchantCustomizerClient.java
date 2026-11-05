package dev.enchantcustomizer.client;

import dev.enchantcustomizer.EnchantCustomizerMod;
import dev.enchantcustomizer.net.ResultPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class EnchantCustomizerClient implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.enchantcustomizer.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of(EnchantCustomizerMod.MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = false;
            while (openKey.wasPressed()) pressed = true; // drain repeated presses
            if (pressed && client.player != null && client.world != null && client.currentScreen == null) {
                client.setScreen(new CustomizerScreen());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ResultPayload.ID, (payload, context) -> {
            if (context.client().currentScreen instanceof CustomizerScreen s) s.onResult(payload);
        });
    }
}
