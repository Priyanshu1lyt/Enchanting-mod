package dev.enchantcustomizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

/** Shared (client + server) enchantment rules. Everything here is registry-driven. */
public final class EnchantLogic {
    public static final int MAX_LEVEL = 255; // limit of the enchantments component codec
    public static final int INV_SIZE = 41;   // 0-35 main, 36-39 armor, 40 offhand
    public static final int ARMOR_FIRST = 36, ARMOR_LAST = 39;

    private static final Set<String> PRO_EXCLUDED = Set.of(
            "blast_protection", "projectile_protection", "thorns", "knockback", "bane_of_arthropods");
    /** Only used to break ties between mutually exclusive enchantments in the Basic preset. */
    private static final List<String> PRIORITY = List.of(
            "protection", "sharpness", "power", "efficiency", "density", "piercing", "fortune", "looting");

    private EnchantLogic() {}

    public static boolean eligible(ItemStack s) {
        if (s == null || s.isEmpty() || s.isOf(Items.ENCHANTED_BOOK)) return false;
        return s.contains(DataComponentTypes.ENCHANTABLE) || s.contains(DataComponentTypes.ENCHANTMENTS);
    }

    public static ItemEnchantmentsComponent enchants(ItemStack s) {
        return s.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
    }

    public static int levelOf(ItemStack s, RegistryEntry<Enchantment> e) {
        return enchants(s).getLevel(e);
    }

    /** Registry-independent identity string, identical on client and server for a synced stack. */
    public static String fingerprint(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        ItemEnchantmentsComponent c = enchants(s);
        List<String> parts = new ArrayList<>();
        for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
            parts.add(e.getIdAsString() + "@" + c.getLevel(e));
        }
        Collections.sort(parts);
        return Registries.ITEM.getId(s.getItem()) + "|" + s.getCount() + "|" + s.getDamage() + "|"
                + s.getName().getString().hashCode() + "|" + String.join(",", parts);
    }

    public static List<RegistryEntry<Enchantment>> compatible(Registry<Enchantment> reg, ItemStack s) {
        List<RegistryEntry<Enchantment>> out = new ArrayList<>();
        if (!eligible(s)) return out;
        reg.streamEntries().forEach(e -> {
            try {
                if (e.value().isAcceptableItem(s)) out.add(e);
            } catch (RuntimeException ignored) {
                // another mod's broken enchantment must not crash the GUI
            }
        });
        out.sort(Comparator.comparing(EnchantLogic::idStr));
        return out;
    }

    public static String idStr(RegistryEntry<Enchantment> e) {
        return e.getIdAsString();
    }

    private static String path(RegistryEntry<Enchantment> e) {
        String id = e.getIdAsString();
        return id.substring(id.indexOf(':') + 1);
    }

    private static boolean combinable(RegistryEntry<Enchantment> e, Collection<RegistryEntry<Enchantment>> others) {
        for (RegistryEntry<Enchantment> o : others) {
            if (!o.equals(e) && !Enchantment.canBeCombined(e, o)) return false;
        }
        return true;
    }

    private static void commit(ItemStack s, ItemEnchantmentsComponent c) {
        if (c.isEmpty()) {
            s.remove(DataComponentTypes.ENCHANTMENTS);
        } else {
            s.set(DataComponentTypes.ENCHANTMENTS, c);
        }
    }

    /** @return null on success, otherwise an error message. Only the enchantments component is touched. */
    public static String setLevel(ItemStack s, RegistryEntry<Enchantment> e, int level) {
        if (!eligible(s)) return "Item cannot be enchanted";
        if (level < 1 || level > MAX_LEVEL) return "Level must be 1-" + MAX_LEVEL;
        if (!e.value().isAcceptableItem(s)) return "Not supported by this item";
        ItemEnchantmentsComponent cur = enchants(s);
        for (RegistryEntry<Enchantment> o : cur.getEnchantments()) {
            if (!o.equals(e) && !Enchantment.canBeCombined(e, o)) {
                return "Incompatible with " + o.value().description().getString();
            }
        }
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(cur);
        b.set(e, level);
        commit(s, b.build());
        return null;
    }

    public static String remove(ItemStack s, RegistryEntry<Enchantment> e) {
        if (!eligible(s)) return "Item cannot be enchanted";
        if (levelOf(s, e) <= 0) return "Enchantment not on item";
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(enchants(s));
        b.remove(o -> o.equals(e));
        commit(s, b.build());
        return null;
    }

    public static String clear(ItemStack s) {
        if (!eligible(s)) return "Item cannot be enchanted";
        if (enchants(s).isEmpty()) return "No enchantments to clear";
        s.remove(DataComponentTypes.ENCHANTMENTS);
        return null;
    }

    private static int prio(RegistryEntry<Enchantment> e) {
        int i = PRIORITY.indexOf(path(e));
        return i < 0 ? 1000 : i;
    }

    private static LinkedHashMap<String, Integer> proTable(ItemStack s) {
        LinkedHashMap<String, Integer> t = new LinkedHashMap<>();
        if (s.isIn(ItemTags.SWORDS)) {
            t.put("sharpness", 5); t.put("fire_aspect", 2); t.put("looting", 3); t.put("sweeping_edge", 3);
        } else if (s.isIn(ItemTags.AXES)) {
            t.put("sharpness", 5); t.put("efficiency", 5);
        } else if (s.isIn(ItemTags.PICKAXES) || s.isIn(ItemTags.SHOVELS) || s.isIn(ItemTags.HOES)) {
            t.put("efficiency", 5); t.put("fortune", 3);
        } else if (s.isIn(ItemTags.HEAD_ARMOR)) {
            t.put("protection", 5); t.put("respiration", 3); t.put("aqua_affinity", 1);
        } else if (s.isIn(ItemTags.CHEST_ARMOR)) {
            t.put("protection", 5);
        } else if (s.isIn(ItemTags.LEG_ARMOR)) {
            t.put("protection", 5); t.put("swift_sneak", 3);
        } else if (s.isIn(ItemTags.FOOT_ARMOR)) {
            t.put("protection", 5); t.put("feather_falling", 4); t.put("depth_strider", 3); t.put("soul_speed", 3);
        } else if (s.isOf(Items.BOW)) {
            t.put("power", 5); t.put("flame", 1); t.put("infinity", 1);
        } else if (s.isOf(Items.CROSSBOW)) {
            t.put("quick_charge", 3); t.put("piercing", 4);
        } else if (s.isOf(Items.TRIDENT)) {
            t.put("riptide", 3); t.put("impaling", 5); // never loyalty / channeling
        } else if (s.isOf(Items.MACE)) {
            t.put("density", 5);
        } else if (s.isOf(Items.FISHING_ROD)) {
            t.put("luck_of_the_sea", 3); t.put("lure", 3);
        }
        t.put("unbreaking", 3);
        t.put("mending", 1);
        return t;
    }

    public static LinkedHashMap<RegistryEntry<Enchantment>, Integer> preset(
            Registry<Enchantment> reg, ItemStack s, boolean pro, List<String> notes) {
        LinkedHashMap<RegistryEntry<Enchantment>, Integer> out = new LinkedHashMap<>();
        if (pro) {
            for (Map.Entry<String, Integer> t : proTable(s).entrySet()) {
                Optional<RegistryEntry.Reference<Enchantment>> opt = reg.getEntry(Identifier.ofVanilla(t.getKey()));
                if (opt.isEmpty()) continue;
                RegistryEntry<Enchantment> e = opt.get();
                if (PRO_EXCLUDED.contains(t.getKey()) || e.isIn(EnchantmentTags.CURSE)) continue;
                if (!e.value().isAcceptableItem(s)) continue;
                if (!combinable(e, out.keySet())) {
                    notes.add(t.getKey() + " skipped (incompatible)");
                    continue;
                }
                out.put(e, Math.min(MAX_LEVEL, t.getValue()));
            }
        } else {
            List<RegistryEntry<Enchantment>> c = new ArrayList<>(compatible(reg, s));
            c.removeIf(e -> !e.getIdAsString().startsWith("minecraft:") || e.isIn(EnchantmentTags.CURSE));
            c.sort(Comparator.comparingInt((RegistryEntry<Enchantment> e) -> prio(e)).thenComparing(EnchantLogic::idStr));
            for (RegistryEntry<Enchantment> e : c) {
                if (combinable(e, out.keySet())) {
                    out.put(e, Math.max(1, Math.min(MAX_LEVEL, e.value().getMaxLevel())));
                }
            }
        }
        return out;
    }

    /** Applies a preset; unrelated enchantments that do not conflict are kept. @return null on success, else error. */
    public static String applyPreset(Registry<Enchantment> reg, ItemStack s, boolean pro, List<String> notes) {
        if (!eligible(s)) return "Item cannot be enchanted";
        LinkedHashMap<RegistryEntry<Enchantment>, Integer> want = preset(reg, s, pro, notes);
        if (want.isEmpty()) return "No applicable enchantments";
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(enchants(s));
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> en : want.entrySet()) {
            RegistryEntry<Enchantment> e = en.getKey();
            b.remove(o -> !o.equals(e) && !Enchantment.canBeCombined(e, o));
            b.set(e, en.getValue());
        }
        commit(s, b.build());
        return null;
    }
}
