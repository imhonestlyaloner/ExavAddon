package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Look at a chest (or barrel, shulker box, hopper, furnace, ender chest...) to see what's inside, in a little grid
 * floating above it. The server only sends a container's items when you open it, so this remembers what was in
 * each one the last time you had it open (for this world, until you leave).
 */
public final class ChestPreview extends ClientModule {
    private static volatile ChestPreview active;

    private final NumberSetting scale = addSetting("scale", new NumberSetting("Size", 1.0F, 0.5F, 1.5F).step(0.05F));
    private final NumberSetting opacity = addSetting("opacity", new NumberSetting("Background Opacity", 0.75F, 0.0F, 1.0F).step(0.05F));
    private final BooleanSetting showEmpty = addSetting("show-empty", new BooleanSetting("Show Empty Slots", true));
    private final BooleanSetting hint = addSetting("hint", new BooleanSetting("Hint On Unknown Containers", true));

    /** what was in each container you opened (both halves of a double chest point at the same list) */
    private final Map<BlockPos, List<ItemStack>> remembered = new HashMap<>();
    private List<ItemStack> enderChest;
    private ClientLevel level;
    private BlockPos lastClicked;
    private long lastClickedAt;
    private BlockPos open;

    public ChestPreview() {
        super("Chest Preview", "Shows what's inside chests and other containers when you look at them (ones you've opened before).", ExavAddon.CATEGORY);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** from the right-click mixin: which block you just used */
    public static void clicked(BlockPos pos) {
        ChestPreview m = active;
        if (m != null) {
            m.lastClicked = pos.immutable();
            m.lastClickedAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            remembered.clear();
            enderChest = null;
        }
        if (mc.level == null || mc.player == null) return;
        // while a container screen is open, keep a copy of its items (it updates as you move things)
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            if (open == null && lastClicked != null && System.currentTimeMillis() - lastClickedAt < 3000) open = lastClicked;
            if (open == null) return;
            List<ItemStack> items = new ArrayList<>();
            for (Slot slot : screen.getMenu().slots) {
                if (!(slot.container instanceof Inventory)) items.add(slot.getItem().copy());
            }
            if (items.isEmpty()) return;
            BlockState state = mc.level.getBlockState(open);
            if (state.getBlock() instanceof EnderChestBlock) {
                enderChest = items;
                return;
            }
            remembered.put(open, items);
            BlockPos other = otherHalf(state, open);
            if (other != null) remembered.put(other, items);
        } else {
            open = null;
        }
    }

    private static BlockPos otherHalf(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) return null;
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        Direction d = ChestBlock.getConnectedDirection(state);
        return pos.relative(d);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        boolean ender = be instanceof EnderChestBlockEntity;
        if (!(be instanceof BaseContainerBlockEntity) && !ender) return;
        List<ItemStack> items = ender ? enderChest : remembered.get(pos);
        GuiGraphics g = event.graphics;
        // float it above the block (above the middle of a double chest)
        Vec3 anchor = Vec3.atCenterOf(pos).add(0, 0.95, 0);
        BlockPos other = otherHalf(mc.level.getBlockState(pos), pos);
        if (other != null) anchor = anchor.add(Vec3.atCenterOf(other).subtract(Vec3.atCenterOf(pos)).scale(0.5));
        float[] at = WorldLabel.project(mc, anchor, g.guiWidth(), g.guiHeight());
        if (at == null) return;
        String name = mc.level.getBlockState(pos).getBlock().getName().getString();
        if (items == null) {
            if (hint.get()) label(g, mc.font, at[0], at[1], name + " - open it once to preview");
            return;
        }
        draw(g, mc.font, at[0], at[1], name, items);
    }

    private void label(GuiGraphics g, Font font, float x, float y, String text) {
        int w = font.width(text) + 8;
        g.fill((int) x - w / 2, (int) y - 14, (int) x + w / 2, (int) y, alpha(0x101018));
        g.drawString(font, text, (int) x - w / 2 + 4, (int) y - 11, 0xFFE0E0E0);
    }

    private void draw(GuiGraphics g, Font font, float x, float y, String name, List<ItemStack> items) {
        int cols = Math.min(9, items.size());
        int rows = (items.size() + cols - 1) / cols;
        int cell = 18, pad = 4, titleH = 11;
        int w = cols * cell + pad * 2, h = rows * cell + pad * 2 + titleH;
        int filled = 0;
        for (ItemStack s : items) if (!s.isEmpty()) filled++;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale.get(), scale.get());
        pose.translate(-w / 2.0F, -h);
        g.fill(0, 0, w, h, alpha(0x101018));
        g.fill(0, 0, w, 1, 0xFF8A5CFF);                        // a thin accent line on top
        g.drawString(font, name + " (" + filled + "/" + items.size() + ")", pad, pad - 1, 0xFFFFFFFF);
        for (int i = 0; i < items.size(); i++) {
            int cx = pad + (i % cols) * cell, cy = pad + titleH + (i / cols) * cell;
            ItemStack stack = items.get(i);
            if (showEmpty.get() || !stack.isEmpty()) g.fill(cx, cy, cx + 16, cy + 16, 0x30FFFFFF);
            if (!stack.isEmpty()) {
                g.renderItem(stack, cx, cy);
                g.renderItemDecorations(font, stack, cx, cy);
            }
        }
        pose.popMatrix();
    }

    private int alpha(int rgb) {
        return (Math.round(opacity.get() * 255) << 24) | rgb;
    }
}
