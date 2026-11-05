package dev.enchantcustomizer.client;

import dev.enchantcustomizer.EnchantLogic;
import dev.enchantcustomizer.net.ActionPayload;
import dev.enchantcustomizer.net.ResultPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CustomizerScreen extends Screen {
    private static final int ROW_H = 22;

    private final List<Integer> slots = new ArrayList<>();
    private List<RegistryEntry<Enchantment>> compat = List.of();
    private int selSlot = -1;
    private String selPrint = "";
    private Identifier selItem;
    private String selEnch;
    private int level = 1;
    private boolean locked;
    private int itemScroll, enchScroll, tickCount, busySince, rebaseUntil;
    private boolean busy, statusOk = true;
    private String status = "Select an item";
    private String lastSig = "";
    private int leftX, leftW, midX, midW, rightX, rightW, top, rows, listBottom;

    public CustomizerScreen() {
        super(Text.literal("Enchantment Customizer"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String fp(ItemStack s) {
        return EnchantLogic.fingerprint(s);
    }

    // ---------- state ----------

    private String buildSig(PlayerInventory inv, List<Integer> outSlots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < EnchantLogic.INV_SIZE && i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (EnchantLogic.eligible(s)) {
                if (outSlots != null) outSlots.add(i);
                sb.append(i).append(':').append(fp(s)).append(';');
            }
        }
        return sb.toString();
    }

    private void refreshInventory() {
        PlayerInventory inv = client.player.getInventory();
        slots.clear();
        lastSig = buildSig(inv, slots);
        if (selSlot >= 0) {
            ItemStack cur = inv.getStack(selSlot);
            boolean same = EnchantLogic.eligible(cur) && fp(cur).equals(selPrint);
            if (!same) {
                boolean rebased = false;
                if (rebaseUntil > tickCount && EnchantLogic.eligible(cur)
                        && Registries.ITEM.getId(cur.getItem()).equals(selItem)) {
                    selPrint = fp(cur); // our own change arriving from the server
                    rebased = true;
                }
                if (!rebased) {
                    int found = -1, cnt = 0;
                    for (int sl : slots) {
                        if (fp(inv.getStack(sl)).equals(selPrint)) {
                            found = sl;
                            cnt++;
                        }
                    }
                    if (cnt == 1) {
                        selSlot = found;
                    } else {
                        selSlot = -1;
                        selEnch = null;
                        status = "Selected item changed - select it again";
                        statusOk = false;
                    }
                }
            }
        }
        compat = selSlot >= 0 ? EnchantLogic.compatible(registry(), inv.getStack(selSlot)) : List.of();
    }

    private Registry<Enchantment> registry() {
        return client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
    }

    public void onResult(ResultPayload p) {
        busy = false;
        status = p.message();
        statusOk = p.ok();
        if (p.ok()) rebaseUntil = tickCount + 60;
    }

    @Override
    public void tick() {
        tickCount++;
        if (client == null || client.player == null || client.world == null) {
            close();
            return;
        }
        if (busy && tickCount - busySince > 60) {
            busy = false;
            status = "No response (is the mod installed on the server?)";
            statusOk = false;
        }
        if (tickCount % 10 == 0 && !buildSig(client.player.getInventory(), null).equals(lastSig)) {
            clearAndInit();
        }
    }

    // ---------- actions ----------

    private void send(int action, String ench, int lvl, List<Integer> sl, List<String> prints) {
        if (busy) return;
        busy = true;
        busySince = tickCount;
        status = "Working...";
        statusOk = true;
        ClientPlayNetworking.send(new ActionPayload(action, ench == null ? "" : ench, lvl, sl, prints));
    }

    /** Revalidates the selection against the live inventory before anything is sent. */
    private boolean selectionValid() {
        if (selSlot < 0) {
            status = "Select an item first";
            statusOk = false;
            return false;
        }
        ItemStack cur = client.player.getInventory().getStack(selSlot);
        if (!EnchantLogic.eligible(cur) || !fp(cur).equals(selPrint)) {
            selSlot = -1;
            selEnch = null;
            status = "Selected item changed - select it again";
            statusOk = false;
            clearAndInit();
            return false;
        }
        return true;
    }

    private void sendSingle(int action) {
        if (!selectionValid()) return;
        if ((action == ActionPayload.SET_LEVEL || action == ActionPayload.REMOVE) && selEnch == null) {
            status = "Select an enchantment first";
            statusOk = false;
            return;
        }
        send(action, selEnch, level, List.of(selSlot), List.of(selPrint));
    }

    private void confirm(String title, String msg, Runnable onYes) {
        client.setScreen(new ConfirmScreen(yes -> {
            client.setScreen(this);
            if (yes) onYes.run();
        }, Text.literal(title), Text.literal(msg)));
    }

    private void preset(boolean pro) {
        int action = pro ? ActionPayload.PRO : ActionPayload.BASIC;
        if (!locked) {
            sendSingle(action);
            return;
        }
        if (slots.isEmpty()) {
            status = "No enchantable items";
            statusOk = false;
            return;
        }
        List<Integer> sl = new ArrayList<>(slots);
        List<String> pr = new ArrayList<>();
        for (int s : sl) pr.add(fp(client.player.getInventory().getStack(s)));
        confirm("Batch mode", "Apply " + (pro ? "Pro" : "Basic") + " to " + sl.size() + " item(s)?",
                () -> send(action, null, 0, sl, pr));
    }

    private void select(int slot) {
        ItemStack s = client.player.getInventory().getStack(slot);
        if (EnchantLogic.eligible(s)) {
            selSlot = slot;
            selPrint = fp(s);
            selItem = Registries.ITEM.getId(s.getItem());
            selEnch = null;
            enchScroll = 0;
            level = 1;
            status = "";
        }
        clearAndInit();
    }

    private ButtonWidget btn(String label, Runnable r, int x, int y, int w) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> r.run())
                .dimensions(x, y, w, 20).build());
    }

    private static String slotTag(int slot) {
        if (slot < 9) return "H" + (slot + 1);
        if (slot < 36) return "S" + slot;
        return slot == 40 ? "Off" : "Eq";
    }

    // ---------- layout ----------

    @Override
    protected void init() {
        if (client == null || client.player == null) return;
        refreshInventory();
        int m = 6, total = width - 2 * m;
        leftW = (int) (total * 0.24);
        midW = (int) (total * 0.32);
        leftX = m;
        midX = leftX + leftW + 4;
        rightX = midX + midW + 4;
        rightW = width - m - rightX;
        top = 36;
        rows = Math.max(3, (height - top - 112) / ROW_H);
        listBottom = top + rows * ROW_H;

        // item list
        itemScroll = Math.max(0, Math.min(itemScroll, Math.max(0, slots.size() - rows)));
        for (int k = 0; k < rows; k++) {
            int idx = itemScroll + k;
            if (idx >= slots.size()) break;
            int slot = slots.get(idx);
            ItemStack s = client.player.getInventory().getStack(slot);
            String name = textRenderer.trimToWidth(s.getName().getString(), midW - 18 - 44);
            String label = (slot == selSlot ? "> " : "") + name + " [" + slotTag(slot) + "]";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> select(slot))
                    .dimensions(midX, top + k * ROW_H, midW - 18, ROW_H - 2).build());
        }
        btn("^", () -> { itemScroll = Math.max(0, itemScroll - 1); clearAndInit(); }, midX + midW - 16, top, 16);
        btn("v", () -> { itemScroll++; clearAndInit(); }, midX + midW - 16, listBottom - 20, 16);

        // enchantment list
        if (selSlot >= 0) {
            ItemStack cs = client.player.getInventory().getStack(selSlot);
            enchScroll = Math.max(0, Math.min(enchScroll, Math.max(0, compat.size() - rows)));
            for (int k = 0; k < rows; k++) {
                int idx = enchScroll + k;
                if (idx >= compat.size()) break;
                RegistryEntry<Enchantment> e = compat.get(idx);
                int cur = EnchantLogic.levelOf(cs, e);
                String name = textRenderer.trimToWidth(e.value().description().getString(), rightW - 18 - 40);
                String label = (e.getIdAsString().equals(selEnch) ? "> " : "") + name + "  " + (cur > 0 ? "Lv " + cur : "-");
                addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                    selEnch = e.getIdAsString();
                    int c = EnchantLogic.levelOf(client.player.getInventory().getStack(selSlot), e);
                    level = c > 0 ? c : 1;
                    clearAndInit();
                }).dimensions(rightX, top + k * ROW_H, rightW - 18, ROW_H - 2).build());
            }
            btn("^", () -> { enchScroll = Math.max(0, enchScroll - 1); clearAndInit(); }, rightX + rightW - 16, top, 16);
            btn("v", () -> { enchScroll++; clearAndInit(); }, rightX + rightW - 16, listBottom - 20, 16);

            int cy = listBottom + 14, cw = rightW / 5;
            btn("-", () -> level = Math.max(1, level - 1), rightX, cy, cw);
            btn("+", () -> level = Math.min(EnchantLogic.MAX_LEVEL, level + 1), rightX + cw, cy, cw);
            btn("Max", this::setMax, rightX + 2 * cw, cy, cw);
            btn("Set", () -> sendSingle(ActionPayload.SET_LEVEL), rightX + 3 * cw, cy, cw);
            btn("Del", () -> sendSingle(ActionPayload.REMOVE), rightX + 4 * cw, cy, rightW - 4 * cw);
        }

        // bottom rows
        int bw = (width - 2 * m - 8) / 3;
        int y1 = height - 66, y2 = height - 44, y3 = height - 22;
        btn("Basic Enchantment", () -> preset(false), m, y1, bw);
        btn("Pro Enchantment", () -> preset(true), m + bw + 4, y1, bw);
        btn("Clear All", this::clearAll, m + 2 * (bw + 4), y1, bw);
        btn(locked ? "Lock: ON (batch)" : "Lock: OFF", () -> { locked = !locked; clearAndInit(); }, m, y2, bw);
        btn("All Armor: Basic", () -> send(ActionPayload.ARMOR_BASIC, null, 0, List.of(), List.of()), m + bw + 4, y2, bw);
        btn("All Armor: Pro", () -> send(ActionPayload.ARMOR_PRO, null, 0, List.of(), List.of()), m + 2 * (bw + 4), y2, bw);
        btn("Close", this::close, m, y3, width - 2 * m);
    }

    private void setMax() {
        for (RegistryEntry<Enchantment> e : compat) {
            if (e.getIdAsString().equals(selEnch)) {
                level = Math.max(1, Math.min(EnchantLogic.MAX_LEVEL, e.value().getMaxLevel()));
                return;
            }
        }
    }

    private void clearAll() {
        if (!selectionValid()) return;
        confirm("Clear enchantments", "Remove ALL enchantments from the selected item?", () -> sendSingle(ActionPayload.CLEAR));
    }

    // ---------- rendering ----------

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, 22, statusOk ? 0xFFA0FFA0 : 0xFFFF7070);
        ctx.drawTextWithShadow(textRenderer, "Preview", leftX, top - 11, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Items", midX, top - 11, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Enchantments", rightX, top - 11, 0xFFAAAAAA);

        ctx.fill(leftX, top, leftX + leftW, listBottom, 0x66000000);
        if (client.player != null) {
            InventoryScreen.drawEntity(ctx, leftX, top, leftX + leftW, listBottom,
                    Math.max(20, (listBottom - top) / 3), 0.0625F, mx, my, client.player);
        }

        for (int k = 0; k < rows; k++) {
            int idx = itemScroll + k;
            if (idx >= slots.size()) break;
            int slot = slots.get(idx);
            int y = top + k * ROW_H;
            ctx.drawItem(client.player.getInventory().getStack(slot), midX + 2, y + 2);
            if (slot == selSlot) ctx.drawBorder(midX, y, midW - 18, ROW_H - 2, 0xFFFFD700);
        }

        if (selSlot >= 0) {
            ctx.drawTextWithShadow(textRenderer, "Level: " + level + (level > 5 ? " (custom)" : ""), rightX, listBottom + 3, 0xFFFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer, "Pick an item on the left", rightX, top + 4, 0xFF888888);
        }
        if (locked) {
            ctx.drawTextWithShadow(textRenderer, "BATCH MODE: presets affect " + slots.size() + " item(s)", midX, listBottom + 4, 0xFFFFAA00);
        }
    }
}
