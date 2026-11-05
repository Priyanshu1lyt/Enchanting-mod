package dev.enchantcustomizer;

import dev.enchantcustomizer.net.ActionPayload;
import dev.enchantcustomizer.net.ResultPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Server-authoritative handling: OP check + every target is revalidated before it is touched. */
public final class ServerHandler {
    private record Result(boolean ok, String msg) {}

    private ServerHandler() {}

    public static void handle(ServerPlayerEntity player, MinecraftServer server, ActionPayload a) {
        Result r;
        try {
            if (server.isDedicated() && !server.getPlayerManager().isOperator(player.getPlayerConfigEntry())) {
                r = new Result(false, "Only server OPs can use the Enchantment Customizer");
            } else {
                r = run(player, a);
            }
        } catch (Exception ex) {
            EnchantCustomizerMod.LOGGER.error("Enchantment action failed", ex);
            r = new Result(false, "Operation failed - see server log");
        }
        ServerPlayNetworking.send(player, new ResultPayload(r.ok(), r.msg()));
    }

    private static Result fail(String m) {
        return new Result(false, m);
    }

    private static Result run(ServerPlayerEntity p, ActionPayload a) {
        int act = a.action();
        if (act < 0 || act > ActionPayload.ARMOR_PRO) return fail("Unknown action");
        Registry<Enchantment> reg = p.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        PlayerInventory inv = p.getInventory();

        Map<Integer, String> targets = new LinkedHashMap<>(); // insertion order + no duplicate slots
        if (act >= ActionPayload.ARMOR_BASIC) {
            for (int i = EnchantLogic.ARMOR_FIRST; i <= EnchantLogic.ARMOR_LAST; i++) targets.put(i, "");
        } else {
            int n = Math.min(a.slots().size(), a.prints().size());
            for (int i = 0; i < n; i++) {
                int sl = a.slots().get(i);
                if (sl >= 0 && sl < EnchantLogic.INV_SIZE && sl < inv.size()) targets.putIfAbsent(sl, a.prints().get(i));
            }
        }
        if (targets.isEmpty()) return fail("Nothing selected");

        RegistryEntry<Enchantment> ench = null;
        if (act == ActionPayload.SET_LEVEL || act == ActionPayload.REMOVE) {
            Identifier id = Identifier.tryParse(a.enchantId());
            Optional<RegistryEntry.Reference<Enchantment>> opt = id == null ? Optional.empty() : reg.getEntry(id);
            if (opt.isEmpty()) return fail("Unknown enchantment");
            ench = opt.get();
        }

        int done = 0, skipped = 0;
        String lastErr = null;
        List<String> notes = new ArrayList<>();
        for (Map.Entry<Integer, String> t : targets.entrySet()) {
            ItemStack s = inv.getStack(t.getKey());
            if (!EnchantLogic.eligible(s)) {
                skipped++;
                lastErr = "Item missing or not enchantable";
                continue;
            }
            if (!t.getValue().isEmpty() && !t.getValue().equals(EnchantLogic.fingerprint(s))) {
                skipped++;
                lastErr = "Item moved or changed - select it again";
                continue;
            }
            String err;
            switch (act) {
                case ActionPayload.SET_LEVEL -> err = EnchantLogic.setLevel(s, ench, a.level());
                case ActionPayload.REMOVE -> err = EnchantLogic.remove(s, ench);
                case ActionPayload.CLEAR -> err = EnchantLogic.clear(s);
                case ActionPayload.BASIC, ActionPayload.ARMOR_BASIC -> err = EnchantLogic.applyPreset(reg, s, false, notes);
                default -> err = EnchantLogic.applyPreset(reg, s, true, notes);
            }
            if (err == null) {
                done++;
            } else {
                skipped++;
                lastErr = err;
            }
        }
        inv.markDirty();

        if (targets.size() == 1) {
            return done > 0 ? new Result(true, "Done" + (notes.isEmpty() ? "" : " (" + String.join(", ", notes) + ")"))
                    : fail(lastErr == null ? "Nothing changed" : lastErr);
        }
        String m = "Modified " + done + " item(s), skipped " + skipped;
        if (skipped > 0 && lastErr != null) m += " (" + lastErr + ")";
        return new Result(done > 0, m);
    }
}
