package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.glint.GlintTexture;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

/**
 * Animated looks for the items you pick: Galaxy, Flame, Swirl, Aurora or Sparkle painted over the item (and your
 * first-person arms and worn armour), plus an effect reaching out around the item's outline - fire licking off it,
 * nebula wisps, ribbons of colour, light curtains or twinkling stars. Each style has its own colours, or use yours.
 */
public final class BetterGlint extends ClientModule {
    private static volatile BetterGlint active;
    private static final Identifier COVER_TEXTURE = Identifier.fromNamespaceAndPath("exav-addon", "dynamic/better_glint");
    private static final Identifier AURA_TEXTURE = Identifier.fromNamespaceAndPath("exav-addon", "dynamic/better_glint_aura");
    private static final int FRAMES = 40;

    private final StringListSetting items = addSetting("items", new StringListSetting("Items", "minecraft:netherite_sword", "minecraft:diamond_sword").itemPicker());
    private final BooleanSetting allEnchanted = addSetting("all-enchanted", new BooleanSetting("All Enchanted Items", false));
    private final ModeSetting style = addSetting("style", new ModeSetting("Style", "Galaxy", GlintTexture.STYLES));
    private final BooleanSetting around = addSetting("around", new BooleanSetting("Effect Around The Item", true));
    private final NumberSetting aroundSize = addSetting("around-size", new NumberSetting("Effect Size", 1.0F, 0.3F, 2.5F).step(0.05F));
    private final BooleanSetting hands = addSetting("hands", new BooleanSetting("Cover My Arms (first person)", true));
    private final BooleanSetting styleColors = addSetting("style-colors", new BooleanSetting("Use The Style's Colors", true));
    private final ColorSetting colorA = addSetting("color-a", new ColorSetting("Color 1", 0xFFB04DFF));
    private final ColorSetting colorB = addSetting("color-b", new ColorSetting("Color 2", 0xFF3D4DFF));
    private final BooleanSetting rainbow = addSetting("rainbow", new BooleanSetting("Rainbow", false));
    private final NumberSetting intensity = addSetting("intensity", new NumberSetting("Intensity", 1.0F, 0.2F, 2.0F).step(0.05F));
    private final NumberSetting speed = addSetting("speed", new NumberSetting("Speed", 1.0F, 0.1F, 4.0F).step(0.1F));
    private final NumberSetting patternSize = addSetting("galaxy-size", new NumberSetting("Pattern Size", 1.0F, 0.3F, 3.0F).step(0.05F));
    private final BooleanSetting armor = addSetting("armor", new BooleanSetting("On Worn Armor", true));

    private DynamicTexture coverTexture, auraTexture;
    /** the animation frames (ABGR), made off the game thread whenever the look changes */
    private volatile int[][] coverFrames, auraFrames;
    private CompletableFuture<?> building;
    private String builtKey = "";
    private int shownFrame = -1;
    private Set<String> picked = Set.of();

    public BetterGlint() {
        super("Better Glint", "Animated galaxy / flame / swirl / aurora / sparkle look on the items you pick, with an effect around them.", ExavAddon.CATEGORY);
        colorA.visibleWhen(() -> !rainbow.get() && !styleColors.get());
        colorB.visibleWhen(() -> !rainbow.get() && !styleColors.get());
        aroundSize.visibleWhen(around::get);
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
        picked = new HashSet<>(items.get());
        int[] pal = styleColors.get() ? GlintTexture.palette(style.get()) : new int[]{colorA.rgb(), colorB.rgb()};
        String key = style.get() + pal[0] + "/" + pal[1] + rainbow.get() + intensity.get();
        if (!key.equals(builtKey) && (building == null || building.isDone())) {
            builtKey = key;
            String st = style.get();
            boolean rb = rainbow.get();
            float in = intensity.get();
            building = CompletableFuture.runAsync(() -> {
                int[][] cover = GlintTexture.cover(st, pal[0], pal[1], rb, in, FRAMES);
                int[][] aura = GlintTexture.aura(st, pal[0], pal[1], rb, in, FRAMES);
                coverFrames = cover;
                auraFrames = aura;
                shownFrame = -1;
            });
        }
    }

    /** shows the right animation frame, once per rendered frame */
    @EventHandler
    public void onRender3D(Render3DEvent event) {
        int[][] cover = coverFrames, aura = auraFrames;
        if (cover == null || aura == null) return;
        double loopMs = 2000.0 / Math.max(0.05, speed.get());
        int frame = (int) ((net.minecraft.util.Util.getMillis() % (long) loopMs) / loopMs * FRAMES) % FRAMES;
        if (frame == shownFrame && coverTexture != null) return;
        shownFrame = frame;
        coverTexture = upload(coverTexture, COVER_TEXTURE, cover[frame], GlintTexture.SIZE, "Exav better glint");
        auraTexture = upload(auraTexture, AURA_TEXTURE, aura[frame], GlintTexture.AURA, "Exav better glint aura");
    }

    private static DynamicTexture upload(DynamicTexture texture, Identifier id, int[] abgr, int size, String name) {
        if (texture == null) {
            texture = new DynamicTexture(() -> name, new NativeImage(size, size, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return texture;
        // NativeImage keeps RGBA bytes = ABGR ints on little-endian machines
        MemoryUtil.memIntBuffer(pixels.getPointer(), size * size).put(abgr);
        texture.upload();
        return texture;
    }

    // ---------------------------------------------------------------- what gets it

    public static boolean wants(ItemStack stack) {
        BetterGlint m = active;
        if (m == null || stack == null || stack.isEmpty() || m.coverTexture == null) return false;
        if (m.allEnchanted.get() && stack.isEnchanted()) return true;
        return m.picked.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static boolean onArmor() {
        BetterGlint m = active;
        return m != null && m.armor.get();
    }

    public static boolean keepVanilla() {
        return false;
    }

    // ---------------------------------------------------------------- render types

    /** slow drift of the cover (the animation frames do the rest) */
    private static Matrix4f drift(float scale) {
        BetterGlint m = active;
        float speed = m == null ? 1 : m.speed.get();
        float size = m == null ? 1 : m.patternSize.get();
        double ms = net.minecraft.util.Util.getMillis() * speed;
        float a = (float) (ms % 160000L) / 160000.0F, b = (float) (ms % 230000L) / 230000.0F;
        return new Matrix4f().translation(a, -b, 0).scale(scale / size);
    }

    private static final TextureTransform ITEM_DRIFT = new TextureTransform("exav_galaxy", () -> drift(10.0F));
    private static final TextureTransform ARMOR_DRIFT = new TextureTransform("exav_armor_galaxy", () -> drift(1.0F));
    private static final TextureTransform ARM_DRIFT = new TextureTransform("exav_arm_galaxy", () -> drift(2.5F));

    private static GpuSampler repeat() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    /** the cover: painted over the item / arm, only where it was drawn (same test as the vanilla glint) */
    private static final RenderPipeline COVER = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("exav-addon", "pipeline/galaxy_cover"))
            .withVertexShader("core/glint").withFragmentShader("core/glint").withSampler("Sampler0")
            .withDepthWrite(false).withCull(false)
            .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .build();

    /** the effect around the item: glowing (added light), behind nothing in front of it */
    private static final RenderPipeline AURA = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("exav-addon", "pipeline/glint_aura"))
            .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color").withSampler("Sampler0")
            .withDepthWrite(false).withCull(false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withBlend(BlendFunction.LIGHTNING)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private static RenderType coverItem, coverArmor, coverArm, aura;

    private static RenderType make(String name, RenderPipeline pipeline, Identifier texture, TextureTransform transform, boolean armor) {
        RenderSetup.RenderSetupBuilder b = RenderSetup.builder(pipeline).withTexture("Sampler0", texture, BetterGlint::repeat);
        if (transform != null) b.setTextureTransform(transform);
        if (armor) b.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        return RenderType.create(name, b.createRenderSetup());
    }

    /** every render type, so each gets its own buffer drawn after the items (see RenderBuffersGlintMixin) */
    public static RenderType[] allTypes() {
        if (coverItem == null) {
            coverItem = make("exav_galaxy", COVER, COVER_TEXTURE, ITEM_DRIFT, false);
            coverArmor = make("exav_armor_galaxy", COVER, COVER_TEXTURE, ARMOR_DRIFT, true);
            coverArm = make("exav_arm_galaxy", COVER, COVER_TEXTURE, ARM_DRIFT, false);
            aura = make("exav_glint_aura", AURA, AURA_TEXTURE, null, false);
        }
        return new RenderType[]{coverItem, coverArmor, coverArm, aura};
    }

    public static RenderType itemType() {
        allTypes();
        return coverItem;
    }

    public static RenderType armorType() {
        allTypes();
        return coverArmor;
    }

    /** the cover for your first-person arms, or null when that's off */
    public static RenderType armType() {
        BetterGlint m = active;
        if (m == null || !m.hands.get() || m.coverTexture == null) return null;
        allTypes();
        return coverArm;
    }

    // ---------------------------------------------------------------- the effect around the item's outline

    /**
     * For flat items (swords, tools...): from every outer edge of the item's outline, a strip reaching outward with the
     * aura texture on it - long off the top edges, shorter off the sides and bottom, so fire rises off the item.
     */
    public static void submitAura(SubmitNodeCollector collector, PoseStack pose, ItemDisplayContext context, List<BakedQuad> quads) {
        BetterGlint m = active;
        if (m == null || !m.around.get() || m.auraTexture == null || quads.isEmpty()) return;
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.NONE) return; // no room in inventory slots
        float zMin = Float.MAX_VALUE, zMax = -Float.MAX_VALUE;
        for (BakedQuad q : quads) {
            for (int i = 0; i < 4; i++) {
                float z = q.position(i).z();
                zMin = Math.min(zMin, z);
                zMax = Math.max(zMax, z);
            }
        }
        if (zMax - zMin > 0.1F) return; // not a flat item (blocks etc.)
        float mid = (zMin + zMax) / 2, size = m.aroundSize.get();
        boolean flame = "Flame".equals(m.style.get());
        RenderType type = aura;
        collector.submitCustomGeometry(pose, type, (p, out) -> {
            for (BakedQuad q : quads) {
                Direction d = q.direction();
                if (d.getAxis() == Direction.Axis.Z) continue; // front / back faces
                // the edge: the two distinct corners of this side face, at mid-thickness
                Vector3fc a = q.position(0), b = null;
                for (int i = 1; i < 4; i++) {
                    Vector3fc c = q.position(i);
                    if (Math.abs(c.x() - a.x()) + Math.abs(c.y() - a.y()) > 1.0E-4F) {
                        b = c;
                        break;
                    }
                }
                if (b == null) continue;
                float nx = d.getStepX(), ny = d.getStepY();
                // long enough that neighbouring pieces overlap into one effect; fire is longest off the top and its
                // tips curl upward, so it rises off the item
                float length = size * (flame ? (ny > 0 ? 0.30F : ny < 0 ? 0.10F : 0.20F) : (ny > 0 ? 0.17F : ny < 0 ? 0.12F : 0.15F));
                float rise = flame ? length * 0.9F : length * 0.2F;
                float u0 = (a.x() + a.y()) * 3, u1 = (b.x() + b.y()) * 3; // along the edge, continuous between pieces
                int base = flame ? 0x99FFFFFF : 0xCCFFFFFF, tip = 0x00FFFFFF; // overlapping pieces add up, so keep each soft
                out.addVertex(p, a.x(), a.y(), mid).setUv(u0, 0).setColor(base);
                out.addVertex(p, b.x(), b.y(), mid).setUv(u1, 0).setColor(base);
                out.addVertex(p, b.x() + nx * length, b.y() + ny * length + rise, mid).setUv(u1, 0.98F).setColor(tip);
                out.addVertex(p, a.x() + nx * length, a.y() + ny * length + rise, mid).setUv(u0, 0.98F).setColor(tip);
            }
        });
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
