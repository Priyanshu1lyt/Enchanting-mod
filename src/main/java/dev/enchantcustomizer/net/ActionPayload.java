package dev.enchantcustomizer.net;

import dev.enchantcustomizer.EnchantCustomizerMod;
import dev.enchantcustomizer.EnchantLogic;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request. Slots are player-inventory indices (0-35 main, 36-39 armor, 40 offhand). */
public record ActionPayload(int action, String enchantId, int level, List<Integer> slots, List<String> prints)
        implements CustomPayload {
    public static final int SET_LEVEL = 0, REMOVE = 1, CLEAR = 2, BASIC = 3, PRO = 4, ARMOR_BASIC = 5, ARMOR_PRO = 6;

    public static final CustomPayload.Id<ActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(EnchantCustomizerMod.MOD_ID, "action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
            PacketCodec.of(ActionPayload::write, ActionPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeString(enchantId);
        buf.writeVarInt(level);
        int n = Math.min(slots.size(), prints.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(slots.get(i));
            buf.writeString(prints.get(i));
        }
    }

    private static ActionPayload read(RegistryByteBuf buf) {
        int action = buf.readVarInt();
        String ench = buf.readString(256);
        int level = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > EnchantLogic.INV_SIZE) {
            throw new IllegalArgumentException("bad target count");
        }
        List<Integer> slots = new ArrayList<>(n);
        List<String> prints = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            slots.add(buf.readVarInt());
            prints.add(buf.readString(4096));
        }
        return new ActionPayload(action, ench, level, slots, prints);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
