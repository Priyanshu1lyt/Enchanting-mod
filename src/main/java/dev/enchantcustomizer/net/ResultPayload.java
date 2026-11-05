package dev.enchantcustomizer.net;

import dev.enchantcustomizer.EnchantCustomizerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client feedback. */
public record ResultPayload(boolean ok, String message) implements CustomPayload {
    public static final CustomPayload.Id<ResultPayload> ID =
            new CustomPayload.Id<>(Identifier.of(EnchantCustomizerMod.MOD_ID, "result"));
    public static final PacketCodec<RegistryByteBuf, ResultPayload> CODEC =
            PacketCodec.of(ResultPayload::write, ResultPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(ok);
        buf.writeString(message);
    }

    private static ResultPayload read(RegistryByteBuf buf) {
        return new ResultPayload(buf.readBoolean(), buf.readString(1024));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
