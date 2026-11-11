package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.glint.GlintTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashSet;
import java.util.Set;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * A different enchantment glint for the items you pick: flames, swirls, aurora bands or sparkles in your own colours
 * (or rainbow), on the item in your hand, in your inventory and on armour you wear. Replaces the normal glint on those
 * items (or adds to it), and works on unenchanted items too.
 */
public final class BetterGlint extends ClientModule {
    private static volatile BetterGlint active;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("exav-addon", "dynamic/better_glint");

    private final StringListSetting items = addSetting("items", new StringListSetting("Items", "minecraft:netherite_sword", "minecraft:diamond_sword").itemPicker());
    private final BooleanSetting allEnchanted = addSetting("all-enchanted", new BooleanSetting("All Enchanted Items", false));
    private final ModeSetting style = addSetting("style", new ModeSetting("Style", "Galaxy", GlintTexture.STYLES));
    private final BooleanSetting hands = addSetting("hands", new BooleanSetting("Cover My Arms (first person)", true));
    private final ColorSetting colorA = addSetting("color-a", new ColorSetting("Color 1", 0xFFB04DFF));
    private final ColorSetting colorB = addSetting("color-b", new ColorSetting("Color 2", 0xFF3D4DFF));
    private final BooleanSetting rainbow = addSetting("rainbow", new BooleanSetting("Rainbow", false));
    private final NumberSetting intensity = addSetting("intensity", new NumberSetting("Intensity", 1.0F, 0.2F, 2.0F).step(0.05F));
    private final NumberSetting speed = addSetting("speed", new NumberSetting("Speed", 1.0F, 0.1F, 4.0F).step(0.1F));
    private final NumberSetting size = addSetting("size", new NumberSetting("Pattern Size", 1.0F, 0.3F, 3.0F).step(0.05F));
    private final BooleanSetting armor = addSetting("armor", new BooleanSetting("On Worn Armor", true));
    private final BooleanSetting keepVanilla = addSetting("keep-vanilla", new BooleanSetting("Keep Normal Glint Too", false));
    private final NumberSetting cloudSize = addSetting("galaxy-size", new NumberSetting("Galaxy Size", 1.0F, 0.3F, 3.0F).step(0.05F));

    private static RenderType itemType, armorType;
    private DynamicTexture texture;
    private String builtKey = "";
    private int ticks;
    private Set<String> picked = Set.of();

    public BetterGlint() {
        super("Better Glint", "Custom flame / swirl / aurora / sparkle glint in your colours on the items you pick.", ExavAddon.CATEGORY);
        colorA.visibleWhen(() -> !rainbow.get());
        colorB.visibleWhen(() -> !rainbow.get());
        hands.visibleWhen(this::galaxy);
        cloudSize.visibleWhen(this::galaxy);
        size.visibleWhen(() -> !galaxy());
        keepVanilla.visibleWhen(() -> !galaxy());
    }

    private boolean galaxy() {
        return GlintTexture.covers(style.get());
    }

    @Override
    protected void onEnable() {
        active = this;
        builtKey = "";
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        ticks++;
        picked = new HashSet<>(items.get());
        // rebuild the texture when the look changes (rainbow drifts through the hues slowly)
        float hue = rainbow.get() ? (ticks / 400.0F) % 1.0F : 0;
        String key = style.get() + colorA.argb() + "/" + colorB.argb() + rainbow.get() + intensity.get() + (rainbow.get() ? (int) (hue * 50) : 0);
        if (!key.equals(builtKey)) {
            builtKey = key;
            upload(GlintTexture.make(style.get(), colorA.rgb(), colorB.rgb(), rainbow.get(), hue, intensity.get()));
        }
    }

    private void upload(int[] abgr) {
        NativeImage image = new NativeImage(GlintTexture.SIZE, GlintTexture.SIZE, false);
        for (int y = 0; y < GlintTexture.SIZE; y++) {
            for (int x = 0; x < GlintTexture.SIZE; x++) {
                int c = abgr[y * GlintTexture.SIZE + x];
                // stored ABGR -> NativeImage takes ARGB
                int argb = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
                image.setPixel(x, y, argb);
            }
        }
        if (texture == null) {
            texture = new DynamicTexture(() -> "Exav better glint", image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        } else {
            texture.setPixels(image);
            texture.upload();
        }
    }

    // ---------------------------------------------------------------- what gets the glint

    /** should this stack get the custom glint? */
    public static boolean wants(ItemStack stack) {
        BetterGlint m = active;
        if (m == null || stack == null || stack.isEmpty() || m.texture == null) return false;
        if (m.allEnchanted.get() && stack.isEnchanted()) return true;
        return m.picked.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static boolean onArmor() {
        BetterGlint m = active;
        return m != null && m.armor.get();
    }

    public static boolean keepVanilla() {
        BetterGlint m = active;
        return m != null && m.keepVanilla.get() && !m.galaxy();
    }

    // ---------------------------------------------------------------- render types (like the vanilla glint, our texture)

    private static Matrix4f scroll(float scale) {
        BetterGlint m = active;
        float speed = m == null ? 1 : m.speed.get();
        float size = m == null ? 1 : m.size.get();
        double ms = net.minecraft.util.Util.getMillis() * speed;
        float drift = (float) (ms % 90000L) / 90000.0F, rise = (float) (ms % 22000L) / 22000.0F;
        // flames rise: scroll mostly upward with a slow sideways drift
        return new Matrix4f().translation(-drift * 0.3F, rise, 0).rotateZ((float) (Math.PI / 24)).scale(scale / size);
    }

    /** the galaxy drifts slowly both ways, like clouds moving through it */
    private static Matrix4f drift(float scale) {
        BetterGlint m = active;
        float speed = m == null ? 1 : m.speed.get();
        float size = m == null ? 1 : m.cloudSize.get();
        double ms = net.minecraft.util.Util.getMillis() * speed;
        float a = (float) (ms % 160000L) / 160000.0F, b = (float) (ms % 230000L) / 230000.0F;
        return new Matrix4f().translation(a, -b, 0).scale(scale / size);
    }

    private static final TextureTransform ITEM_SCROLL = new TextureTransform("exav_glint", () -> scroll(4.0F));
    private static final TextureTransform ARMOR_SCROLL = new TextureTransform("exav_armor_glint", () -> scroll(0.25F));
    private static final TextureTransform ITEM_DRIFT = new TextureTransform("exav_galaxy", () -> drift(10.0F));
    private static final TextureTransform ARMOR_DRIFT = new TextureTransform("exav_armor_galaxy", () -> drift(1.0F));
    private static final TextureTransform ARM_DRIFT = new TextureTransform("exav_arm_galaxy", () -> drift(2.5F));

    /** the pattern must repeat (the item / skin coordinates go well past one copy of it) */
    private static com.mojang.blaze3d.textures.GpuSampler repeat() {
        return com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(com.mojang.blaze3d.textures.AddressMode.REPEAT,
                com.mojang.blaze3d.textures.AddressMode.REPEAT, com.mojang.blaze3d.textures.FilterMode.LINEAR, com.mojang.blaze3d.textures.FilterMode.LINEAR, false);
    }

    /**
     * Galaxy: painted over the item / arm (normal see-through blending, not added light like a glint), only where the
     * item itself was drawn - the same as the vanilla glint shader, just covering instead of shining.
     */
    private static final com.mojang.blaze3d.pipeline.RenderPipeline COVER = com.mojang.blaze3d.pipeline.RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("exav-addon", "pipeline/galaxy_cover"))
            .withVertexShader("core/glint").withFragmentShader("core/glint").withSampler("Sampler0")
            .withDepthWrite(false).withCull(false)
            .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.EQUAL_DEPTH_TEST)
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
            .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            .build();

    private static RenderType glintItem, glintArmor, coverItem, coverArmor, coverArm;

    private static RenderType make(String name, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, TextureTransform transform, boolean armor) {
        RenderSetup.RenderSetupBuilder b = RenderSetup.builder(pipeline).withTexture("Sampler0", TEXTURE, BetterGlint::repeat).setTextureTransform(transform);
        if (armor) b.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        return RenderType.create(name, b.createRenderSetup());
    }

    /** every render type, so each gets its own buffer drawn after the items (see RenderBuffersGlintMixin) */
    public static RenderType[] allTypes() {
        if (glintItem == null) {
            glintItem = make("exav_glint", RenderPipelines.GLINT, ITEM_SCROLL, false);
            glintArmor = make("exav_armor_glint", RenderPipelines.GLINT, ARMOR_SCROLL, true);
            coverItem = make("exav_galaxy", COVER, ITEM_DRIFT, false);
            coverArmor = make("exav_armor_galaxy", COVER, ARMOR_DRIFT, true);
            coverArm = make("exav_arm_galaxy", COVER, ARM_DRIFT, false);
        }
        return new RenderType[]{glintItem, glintArmor, coverItem, coverArmor, coverArm};
    }

    private static boolean covering() {
        BetterGlint m = active;
        return m != null && m.galaxy();
    }

    public static RenderType itemType() {
        allTypes();
        return covering() ? coverItem : glintItem;
    }

    public static RenderType armorType() {
        allTypes();
        return covering() ? coverArmor : glintArmor;
    }

    /** the galaxy for your first-person arms, or null when that's off */
    public static RenderType armType() {
        BetterGlint m = active;
        if (m == null || !m.galaxy() || !m.hands.get() || m.texture == null) return null;
        allTypes();
        return coverArm;
    }

    // ---------------------------------------------------------------- set while an item's layers are being submitted

    private static boolean current;

    public static void begin(boolean wanted) {
        current = wanted;
    }

    public static void end() {
        current = false;
    }

    public static boolean current() {
        return current;
    }
}
