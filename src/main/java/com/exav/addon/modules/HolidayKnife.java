package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.holiday.CakeShow;
import com.exav.addon.holiday.HandArm;
import com.exav.addon.holiday.HolidayAnimation;
import com.exav.addon.holiday.HolidayAnimation.Skin;
import com.exav.addon.holiday.HolidayModels;
import com.exav.addon.holiday.SmoothMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.TickEvent;
import imgui.ImDrawList;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
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
public final class HolidayKnife extends ClientModule implements HudOverlayProvider {
    /** plain white 8x8 texture shipped with Anubis; colours come from the vertices */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");
    private static volatile HolidayKnife active;

    private final ModeSetting skin = addSetting("skin", new ModeSetting("Knife Skin", Skin.CANDY_CANE.label,
            Skin.CANDY_CANE.label, Skin.HOT_COCOA.label, Skin.GINGERBREAD.label, Skin.BIRTHDAY_CAKE.label));
    private final ModeSetting caneColors = addSetting("cane-colors", new ModeSetting("Cane Colors", "Red & White", "Red & White", "Red & Green"));
    private final KeySetting key = addSetting("key", new KeySetting("Inspect Key", 70)); // F
    private final NumberSetting speed = addSetting("speed", new NumberSetting("Speed", 1.0F, 0.5F, 2.0F).step(0.05F));
    private final NumberSetting size = addSetting("size", new NumberSetting("Knife Size", 1.0F, 0.5F, 2.0F).step(0.05F));
    private final BooleanSetting hideHand = addSetting("hide-hand", new BooleanSetting("Hide Hand Model", false));
    private final NumberSetting volume = addSetting("volume", new NumberSetting("Sound Volume", 100.0F, 0.0F, 100.0F).step(5.0F));
    // birthday cake
    private final StringSetting cakeText = addSetting("cake-text", new StringSetting("Birthday Text", "Happy Birthday Valk"));
    private final NumberSetting textSize = addSetting("text-size", new NumberSetting("Text Size", 1.0F, 0.4F, 2.0F).step(0.05F));
    private final NumberSetting textOpacity = addSetting("text-opacity", new NumberSetting("Text Opacity", 100.0F, 10.0F, 100.0F).step(5.0F));
    private final NumberSetting textX = addSetting("text-x", new NumberSetting("Text Left/Right", 0.0F, -0.6F, 0.6F).step(0.01F));
    private final NumberSetting textY = addSetting("text-y", new NumberSetting("Text Up/Down", 0.0F, -0.6F, 0.6F).step(0.01F));
    private final NumberSetting textZ = addSetting("text-z", new NumberSetting("Text Distance", 0.0F, -0.4F, 0.4F).step(0.01F));
    private final BooleanSetting sparklers = addSetting("sparklers", new BooleanSetting("Sparklers", true));
    private final NumberSetting sparkAmount = addSetting("spark-amount", new NumberSetting("Sparkle Amount", 1.0F, 0.2F, 3.0F).step(0.1F));
    private final NumberSetting confettiOpacity = addSetting("confetti-opacity", new NumberSetting("Confetti Opacity", 90.0F, 10.0F, 100.0F).step(5.0F));
    private final NumberSetting confettiSize = addSetting("confetti-size", new NumberSetting("Confetti Size", 1.0F, 0.4F, 2.0F).step(0.05F));
    private final NumberSetting confettiAmount = addSetting("confetti-amount", new NumberSetting("Confetti Amount", 2.0F, 1.0F, 4.0F).step(1.0F));
    private final NumberSetting confettiFlutter = addSetting("confetti-flutter", new NumberSetting("Confetti Flutter", 1.0F, 0.0F, 3.0F).step(0.1F));
    private final ModeSetting confettiColors = addSetting("confetti-colors", new ModeSetting("Confetti Colors", "Rainbow", CakeShow.PALETTE_NAMES));
    private final BooleanSetting showImage = addSetting("show-image", new BooleanSetting("Show Picture", true));
    private final NumberSetting imageSize = addSetting("image-size", new NumberSetting("Picture Size", 1.0F, 0.4F, 2.0F).step(0.05F));
    private final NumberSetting imageOpacity = addSetting("image-opacity", new NumberSetting("Picture Opacity", 100.0F, 10.0F, 100.0F).step(5.0F));
    private final NumberSetting imageX = addSetting("image-x", new NumberSetting("Picture Left/Right", 0.0F, -0.6F, 0.6F).step(0.01F));
    private final NumberSetting imageY = addSetting("image-y", new NumberSetting("Picture Up/Down", 0.0F, -0.6F, 0.6F).step(0.01F));
    private final NumberSetting imageZ = addSetting("image-z", new NumberSetting("Picture Distance", 0.0F, -0.4F, 0.4F).step(0.01F));
    private final BooleanSetting faceSplat = addSetting("face-splat", new BooleanSetting("Cake In Your Face", true));
    private final BooleanSetting sneakSkips = addSetting("sneak-skips", new BooleanSetting("Sneak Skips Inspect", true));

    private boolean inspecting;
    private long startedAt;
    private Skin playing;
    private float lastT;
    private int heldSlot = -1;
    private final CakeShow cakeShow = new CakeShow();
    private static final SoundEvent PARTY_HORN = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("exav-addon", "party_horn"));
    private Item heldItem;

    private static SmoothMesh caneRW, caneRWBitten, caneRG, caneRGBitten, mug, mallow, gingerWhole, gingerBitten, gingerUpper, gingerFore,
            crumbRed, crumbWhite, crumbGreen, crumbCookie, cake, flame, dynamite, puff;
    private static SmoothMesh[] cakeChunks;

    public HolidayKnife() {
        super("Holiday Knife", "Candy cane, hot cocoa, gingerbread man or birthday cake skins for your sword, each with its own inspect animation (F by default).", ExavAddon.CATEGORY);
        caneColors.visibleWhen(() -> skin.is(Skin.CANDY_CANE.label));
        for (var s : new net.anubis.client.module.setting.Setting[]{cakeText, textSize, textOpacity, textX, textY, textZ, sparklers, sparkAmount, confettiOpacity, confettiSize, confettiAmount, confettiFlutter,
                confettiColors, showImage, imageSize, imageOpacity, faceSplat}) {
            s.visibleWhen(() -> skin.is(Skin.BIRTHDAY_CAKE.label));
        }
        sparkAmount.visibleWhen(() -> skin.is(Skin.BIRTHDAY_CAKE.label) && sparklers.get());
        imageSize.visibleWhen(() -> skin.is(Skin.BIRTHDAY_CAKE.label) && showImage.get());
        imageOpacity.visibleWhen(() -> skin.is(Skin.BIRTHDAY_CAKE.label) && showImage.get());
        for (var s : new NumberSetting[]{imageX, imageY, imageZ}) {
            s.visibleWhen(() -> skin.is(Skin.BIRTHDAY_CAKE.label) && showImage.get());
        }
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
            lastT = -1;
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
        float t = -1;
        if (inspecting && playing == current) {
            t = (System.nanoTime() - startedAt) / 1.0E9F * speed.get();
            if (t >= current.seconds) {
                inspecting = false;
                t = -1;
                f = HolidayAnimation.rest(current);
            } else {
                f = HolidayAnimation.at(current, t, knifeSize);
            }
        } else {
            inspecting = false;
            f = HolidayAnimation.rest(current);
        }

        RenderType type = RenderTypes.entityCutoutNoCull(WHITE);
        float scale = current.voxel * knifeSize;
        pose.pushPose();
        if (left) pose.scale(-1.0F, 1.0F, 1.0F);

        if (!hideHand.get()) {
            HandArm arm = HandArm.current();
            if (arm != null) {
                Vector3f elbow = new Vector3f(f.hand).fma(0.6F, HandArm.FOREARM);
                arm.draw(pose, f.hand, elbow, left, collector, light);
            }
        }

        if (current == Skin.BIRTHDAY_CAKE) {
            if (t >= 0) {
                sounds(t);
                cakeShow.draw(pose, collector, light, t, knifeSize, cakeConfig(), dynamite, puff, flame, cakeChunks);
            }
            lastT = t;
        }

        // the item, in its own model space
        pose.pushPose();
        pose.translate(f.item.x, f.item.y, f.item.z);
        pose.mulPose(f.rotation);
        float shown = scale * f.itemScale;
        pose.scale(shown, shown, shown);
        pose.translate(-current.gripX, -current.gripY, -current.gripZ);
        switch (current) {
            case CANDY_CANE -> {
                boolean green = caneColors.is("Red & Green");
                submit(pose, collector, type, green ? (f.bitten ? caneRGBitten : caneRG) : (f.bitten ? caneRWBitten : caneRW), light);
            }
            case HOT_COCOA -> {
                submit(pose, collector, type, mug, light);
                for (int i = 0; i < f.caught && i < HolidayAnimation.MALLOW_SLOTS.length; i++) {
                    float[] slot = HolidayAnimation.MALLOW_SLOTS[i];
                    pose.pushPose();
                    pose.translate(slot[0], HolidayModels.MUG_SURFACE_Y + 0.7F, slot[1]);
                    pose.mulPose(Axis.YP.rotationDegrees(i * 37.0F));
                    pose.mulPose(Axis.XP.rotationDegrees(i % 2 == 0 ? 12.0F : -9.0F));
                    submit(pose, collector, type, mallow, light);
                    pose.popPose();
                }
            }
            case BIRTHDAY_CAKE -> {
                if (f.itemScale > 0.001F) {
                    submit(pose, collector, type, cake, light);
                    float now = System.nanoTime() / 1.0E9F;
                    for (int i = 0; i < HolidayModels.CANDLES.length; i++) {
                        float[] c = HolidayModels.CANDLES[i];
                        pose.pushPose();
                        pose.translate(c[0], HolidayModels.CANDLE_TOP_Y + 0.35F, c[1]);
                        float flick = 1.0F + 0.12F * (float) Math.sin(now * 17 + i * 2.3F) + 0.06F * (float) Math.sin(now * 29 + i);
                        pose.mulPose(Axis.ZP.rotationDegrees(6.0F * (float) Math.sin(now * 9 + i)));
                        pose.scale(1.0F, flick, 1.0F);
                        submit(pose, collector, type, flame, 0xF000F0);
                        pose.popPose();
                    }
                }
            }
            case GINGERBREAD -> {
                submit(pose, collector, type, f.bitten ? gingerBitten : gingerWhole, light);
                // waving arm: upper arm turns at the shoulder, forearm at the elbow
                pose.pushPose();
                pose.translate(HolidayModels.GINGER_SHOULDER_X, HolidayModels.GINGER_SHOULDER_Y, 0.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(f.upperArm));
                submit(pose, collector, type, gingerUpper, light);
                pose.translate(HolidayModels.GINGER_UPPER_LEN, 0.0F, 0.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(f.foreArm));
                submit(pose, collector, type, gingerFore, light);
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
            submit(pose, collector, type, mallow, light);
            pose.popPose();
        }

        // crumbs from a bite
        for (HolidayAnimation.Crumb c : f.crumbs) {
            SmoothMesh crumb = c.rgb() == HolidayModels.RED ? crumbRed
                    : c.rgb() == HolidayModels.WHITE ? (caneColors.is("Red & Green") ? crumbGreen : crumbWhite) : crumbCookie;
            pose.pushPose();
            pose.translate(c.pos().x, c.pos().y, c.pos().z);
            pose.scale(scale, scale, scale);
            submit(pose, collector, type, crumb, light);
            pose.popPose();
        }
        pose.popPose();
    }

    private CakeShow.Config cakeConfig() {
        return new CakeShow.Config(cakeText.get(), textSize.get(), textOpacity.get() / 100.0F, textX.get(), textY.get(), -textZ.get(), confettiOpacity.get() / 100.0F, confettiSize.get(), confettiAmount.getInt(),
                confettiFlutter.get(), confettiColors.get(), showImage.get(), imageSize.get(), imageOpacity.get() / 100.0F, imageX.get(), imageY.get(), -imageZ.get(), faceSplat.get(),
                sparklers.get(), sparkAmount.get());
    }

    /** party horn when the confetti pops, fuse hiss when the dynamite lands, boom when it goes off */
    private void sounds(float t) {
        float vol = volume.get() / 100.0F;
        if (vol <= 0) return;
        var sounds = Minecraft.getInstance().getSoundManager();
        if (crossed(t, CakeShow.POP)) sounds.play(SimpleSoundInstance.forUI(PARTY_HORN, 1.0F, vol));
        if (crossed(t, CakeShow.LAND)) sounds.play(SimpleSoundInstance.forUI(SoundEvents.TNT_PRIMED, 1.0F, 0.6F * vol));
        if (crossed(t, CakeShow.BOOM)) sounds.play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, vol));
    }

    private boolean crossed(float t, float at) {
        return lastT < at && t >= at;
    }

    @Override
    public boolean isHudOverlayEnabled() {
        if (!isEnabled() || !inspecting || playing != Skin.BIRTHDAY_CAKE || !faceSplat.get()) return false;
        return CakeShow.splatting((System.nanoTime() - startedAt) / 1.0E9F * speed.get());
    }

    @Override
    public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
        CakeShow.overlay(dl, screenW, screenH, (System.nanoTime() - startedAt) / 1.0E9F * speed.get());
    }

    private static void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, SmoothMesh mesh, int light) {
        collector.submitCustomGeometry(pose, type, (p, out) -> mesh.emit(p, out, light));
    }

    private static void buildMeshes() {
        if (caneRW != null) return;
        caneRW = HolidayModels.cane(false);
        caneRWBitten = HolidayModels.caneBitten(false);
        caneRG = HolidayModels.cane(true);
        caneRGBitten = HolidayModels.caneBitten(true);
        mug = HolidayModels.mug();
        mallow = HolidayModels.marshmallow();
        gingerWhole = HolidayModels.gingerWhole();
        gingerBitten = HolidayModels.gingerBitten();
        gingerUpper = HolidayModels.gingerUpperArm();
        gingerFore = HolidayModels.gingerForearm();
        crumbRed = HolidayModels.crumb(HolidayModels.RED);
        crumbWhite = HolidayModels.crumb(HolidayModels.WHITE);
        crumbGreen = HolidayModels.crumb(HolidayModels.GREEN);
        crumbCookie = HolidayModels.crumb(HolidayModels.CRUMB_COOKIE);
        cake = HolidayModels.cake();
        flame = HolidayModels.flame();
        dynamite = HolidayModels.dynamite();
        puff = HolidayModels.puff();
        // lumps of cake for the explosion (unit size, scaled when drawn)
        int[] lump = {0xF48FB1, 0xFFF8F2, 0xB3E5FC, 0xF0CD82, 0xF06292};
        cakeChunks = new SmoothMesh[lump.length];
        for (int i = 0; i < lump.length; i++) {
            SmoothMesh.Builder b = new SmoothMesh.Builder();
            com.exav.addon.holiday.Shapes.sphere(b, 0, 0, 0, 1.0F, lump[i], 5);
            cakeChunks[i] = b.build();
        }
    }
}
