package dev.enchantcustomizer;

import dev.enchantcustomizer.net.ActionPayload;
import dev.enchantcustomizer.net.ResultPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantCustomizerMod implements ModInitializer {
    public static final String MOD_ID = "enchantcustomizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResultPayload.ID, ResultPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID,
                (payload, context) -> ServerHandler.handle(context.player(), payload));
    }
}
