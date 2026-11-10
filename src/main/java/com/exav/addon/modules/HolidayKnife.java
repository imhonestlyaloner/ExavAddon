package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.holiday.HandArm;
import com.exav.addon.holiday.HolidayAnimation;
import com.exav.addon.holiday.HolidayAnimation.Skin;
import com.exav.addon.holiday.HolidayModels;
import com.exav.addon.holiday.VoxelMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * Holiday skins for the sword you're holding (like Anubis's CS Knife), each with its own inspect animation:
 * Candy Cane (toss, catch by the hook, bite), Hot Cocoa (catch falling marshmallows, sip),
 * Gingerbread Man (he waves, you bite his head). Takes over from CS Knife while it's on.
 */
public final class HolidayKnife extends ClientModule {
    /** plain white 8x8 texture shipped with Anubis; colours come from the vertices */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");
    private static volatile HolidayKnife active;

    private final ModeSetting skin = addSetting("skin", new ModeSetting("Knife Skin", Skin.CANDY_CANE.label,
            Skin.CANDY_CANE.label, Skin.HOT_COCOA.label, Skin.GINGERBREAD.label));
    private final KeySetting key = addSetting("key", new KeySetting("Inspect Key", 70)); // F
    private final NumberSetting speed = addSetting("speed", new NumberSetting("Speed", 1.0F, 0.5F, 2.0F).step(0.05F));
    private final NumberSetting size = addSetting("size", new NumberSetting("Knife Size", 1.0F, 0.5F, 2.0F).step(0.05F));
    private final BooleanSetting showArm = addSetting("show-arm", new BooleanSetting("Show Arm", true));
    private final BooleanSetting sneakSkips = addSetting("sneak-skips", new BooleanSetting("Sneak Skips Inspect", true));

    private boolean inspecting;
    private long startedAt;
    private Skin playing;
    private int heldSlot = -1;
    private Item heldItem;

    private static VoxelMesh caneWhole, caneBitten, mug, mallow, gingerWhole, gingerBitten, gingerArm, crumbRed, crumbWhite, crumbCookie;

    public HolidayKnife() {
        super("Holiday Knife", "Candy cane, hot cocoa or gingerbread man skins for your sword, each with its own inspect animation (F by default).", ExavAddon.CATEGORY);
    }

    @Override
    protected void onEnable() {
        inspecting = false;
        active = this;
        // before CS Knife (HIGH) so the same key doesn't start both
        on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGHEST, this::onKey);
        on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGHEST, this::onMouse);
        on(TickEvent.TYPE, Stage.POST, this::onTick);
    }

    @Override
    protected void onDisable() {
        active = null;
        inspecting = false;
    }

    private void onKey(KeyInputEvent event) {
        if (event.action == 1 && event.key == key.value() && tryStart()) event.setCancelled(true);
    }

    private void onMouse(MouseInputEvent event) {
        if (event.action == 1 && 1000 + event.button == key.value() && tryStart()) event.setCancelled(true);
    }

    private boolean tryStart() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.screen != null || player == null || player.isSpectator()) return false;
        if (sneakSkips.get() && player.isShiftKeyDown()) return false;
        if (!isSword(player.getMainHandItem()) || player.isUsingItem()) return false;
        if (!inspecting) {
            inspecting = true;
            startedAt = System.nanoTime();
            playing = Skin.of(skin.get());
        }
        return true;
    }

    private void onTick(TickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            inspecting = false;
            return;
        }
        ItemStack held = player.getMainHandItem();
        int slot = player.getInventory().getSelectedSlot();
        // switching items or swinging stops the inspect
        if (slot != heldSlot || !held.is(heldItem) || player.swinging) {
            inspecting = false;
            heldSlot = slot;
            heldItem = held.getItem();
        }
    }

    static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_sword"));
    }

    /** Called from the hand render mixin. Returns true if it drew the item (so the normal item isn't drawn). */
    public static boolean render(PoseStack pose, ItemStack stack, ItemDisplayContext context, SubmitNodeCollector collector, int light, boolean mainHand) {
        HolidayKnife module = active;
        if (module == null || !mainHand || !isSword(stack)) return false;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return false;
        module.draw(pose, context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND, collector, light);
        return true;
    }

    private void draw(PoseStack pose, boolean left, SubmitNodeCollector collector, int light) {
        buildMeshes();
        Skin current = Skin.of(skin.get());
        float knifeSize = size.get();
        HolidayAnimation.Frame f;
        if (inspecting && playing == current) {
            float t = (System.nanoTime() - startedAt) / 1.0E9F * speed.get();
            if (t >= current.seconds) {
                inspecting = false;
                f = HolidayAnimation.rest(current);
            } else {
                f = HolidayAnimation.at(current, t, knifeSize);
            }
        } else {
            inspecting = false;
            f = HolidayAnimation.rest(current);
        }

        RenderType type = RenderTypes.entitySolid(WHITE);
        float scale = current.voxel * knifeSize;
        pose.pushPose();
        if (left) pose.scale(-1.0F, 1.0F, 1.0F);

        if (showArm.get()) {
            HandArm arm = HandArm.current();
            if (arm != null) {
                Vector3f elbow = new Vector3f(f.hand).fma(0.6F, HandArm.FOREARM);
                arm.draw(pose, f.hand, elbow, left, collector, light);
            }
        }

        // the item, in its own model space
        pose.pushPose();
        pose.translate(f.item.x, f.item.y, f.item.z);
        pose.mulPose(f.rotation);
        pose.scale(scale, scale, scale);
        pose.translate(-current.gripX, -current.gripY, -current.gripZ);
        switch (current) {
            case CANDY_CANE -> submit(pose, collector, type, f.bitten ? caneBitten : caneWhole, light);
            case HOT_COCOA -> {
                submit(pose, collector, type, mug, light);
                for (int i = 0; i < f.caught && i < HolidayAnimation.MALLOW_SLOTS.length; i++) {
                    float[] slot = HolidayAnimation.MALLOW_SLOTS[i];
                    pose.pushPose();
                    pose.translate(slot[0] + 0.5F, HolidayModels.MUG_SURFACE_Y + 1.0F, slot[1] + 0.5F);
                    pose.mulPose(Axis.YP.rotationDegrees(i * 37.0F));
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    submit(pose, collector, type, mallow, light);
                    pose.popPose();
                }
            }
            case GINGERBREAD -> {
                submit(pose, collector, type, f.bitten ? gingerBitten : gingerWhole, light);
                pose.pushPose();
                pose.translate(HolidayModels.GINGER_SHOULDER_X, HolidayModels.GINGER_SHOULDER_Y, 0.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(f.wave));
                submit(pose, collector, type, gingerArm, light);
                pose.popPose();
            }
        }
        pose.popPose();

        // marshmallows still falling
        float mugScale = Skin.HOT_COCOA.voxel * knifeSize;
        for (HolidayAnimation.Falling m : f.falling) {
            pose.pushPose();
            pose.translate(m.pos().x, m.pos().y, m.pos().z);
            pose.mulPose(Axis.XP.rotationDegrees(m.spin()));
            pose.mulPose(Axis.ZP.rotationDegrees(m.spin() * 0.6F));
            pose.scale(mugScale, mugScale, mugScale);
            pose.translate(-0.5F, -0.5F, -0.5F);
            submit(pose, collector, type, mallow, light);
            pose.popPose();
        }

        // crumbs from a bite
        for (HolidayAnimation.Crumb c : f.crumbs) {
            VoxelMesh crumb = c.rgb() == HolidayModels.CRUMB_CANE ? crumbRed : c.rgb() == HolidayModels.CRUMB_CANE_2 ? crumbWhite : crumbCookie;
            pose.pushPose();
            pose.translate(c.pos().x, c.pos().y, c.pos().z);
            float s = scale * 0.9F;
            pose.scale(s, s, s);
            pose.translate(-0.5F, -0.5F, -0.5F);
            submit(pose, collector, type, crumb, light);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, VoxelMesh mesh, int light) {
        collector.submitCustomGeometry(pose, type, (p, out) -> mesh.emit(p, out, light));
    }

    private static void buildMeshes() {
        if (caneWhole != null) return;
        caneWhole = HolidayModels.caneWhole();
        caneBitten = HolidayModels.caneBitten();
        mug = HolidayModels.mug();
        mallow = HolidayModels.marshmallow();
        gingerWhole = HolidayModels.gingerWhole();
        gingerBitten = HolidayModels.gingerBitten();
        gingerArm = HolidayModels.gingerArm();
        crumbRed = HolidayModels.crumb(HolidayModels.CRUMB_CANE);
        crumbWhite = HolidayModels.crumb(HolidayModels.CRUMB_CANE_2);
        crumbCookie = HolidayModels.crumb(HolidayModels.CRUMB_COOKIE);
    }
}
