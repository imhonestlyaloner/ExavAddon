package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anubis' CS Knife plays one inspect animation; with this on, each inspect plays the next of several (Toss &amp; Catch,
 * Spinner, Blade Check, Flick &amp; Stab, plus Anubis' own Classic and Twirl if you like). They're keyframed the same
 * way as Anubis' own (handed to its animator), so they work with every knife skin and the "hold to keep looking" loop.
 */
public final class InspectCycle extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static volatile InspectCycle active;

    private final ModeSetting order = addSetting("order", new ModeSetting("Order", "Cycle", "Cycle", "Random"));
    private final BooleanSetting toss = addSetting("toss", new BooleanSetting("Toss & Catch", true));
    private final BooleanSetting spinner = addSetting("spinner", new BooleanSetting("Spinner", true));
    private final BooleanSetting bladeCheck = addSetting("blade-check", new BooleanSetting("Blade Check", true));
    private final BooleanSetting flick = addSetting("flick", new BooleanSetting("Flick & Stab", true));
    private final BooleanSetting anubisOwn = addSetting("anubis-own", new BooleanSetting("Include Anubis' Classic & Twirl", false));
    private final NumberSetting speed = addSetting("speed", new NumberSetting("Speed", 1.0F, 0.5F, 2.0F).step(0.05F));

    private int next;
    private int last = -1;

    public InspectCycle() {
        super("Inspect Cycle", "CS Knife inspects cycle through 4 different animations instead of always the same one.", ExavAddon.CATEGORY);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    // ---------------------------------------------------------------- the animations
    // each row: time (0..1 of the clip), x, y, z (blocks; +z is toward you), yaw, pitch, roll, flip, spin (degrees)

    /** thrown up spinning end over end, caught, then held up to look at */
    private static final float[][] TOSS = {
            {0.00F, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.10F, -0.05F, -0.06F, 0.02F, 10, 8, 10, 0, 0},
            {0.22F, -0.12F, 0.55F, 0.00F, 20, -5, 20, 300, 0},
            {0.32F, -0.14F, 0.70F, 0.00F, 25, -5, 25, 540, 0},
            {0.44F, -0.18F, 0.30F, 0.06F, 55, -8, 40, 700, 0},
            {0.50F, -0.22F, 0.18F, 0.10F, 62, -8, 46, 720, 0},
            {0.56F, -0.24F, 0.21F, 0.10F, 64, -6, 48, 720, 0},
            {0.78F, -0.23F, 0.19F, 0.10F, 60, -8, 48, 720, 0},
            {1.00F, 0, 0, 0, 0, 0, 0, 720, 0},
    };
    /** held up and spun round like a fidget spinner, then settles */
    private static final float[][] SPINNER = {
            {0.00F, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.12F, -0.18F, 0.14F, 0.06F, 70, -6, 24, 0, -30},
            {0.30F, -0.20F, 0.22F, 0.08F, 76, -6, 22, 0, 360},
            {0.48F, -0.20F, 0.22F, 0.08F, 76, -6, 22, 0, 720},
            {0.62F, -0.21F, 0.20F, 0.08F, 74, -6, 26, 0, 1080},
            {0.70F, -0.23F, 0.18F, 0.10F, 66, -8, 40, 0, 1100},
            {0.84F, -0.23F, 0.18F, 0.10F, 64, -8, 44, 0, 1080},
            {1.00F, 0, 0, 0, 0, 0, 0, 0, 1080},
    };
    /** brought close, the edge tilted past your eye, flipped to check both sides */
    private static final float[][] BLADE_CHECK = {
            {0.00F, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.14F, -0.30F, 0.20F, 0.16F, 85, -18, 20, 0, 0},
            {0.30F, -0.30F, 0.22F, 0.17F, 88, -22, 70, 0, 0},
            {0.42F, -0.28F, 0.21F, 0.15F, 70, -10, 50, 0, 0},
            {0.52F, -0.27F, 0.20F, 0.14F, 66, -8, 48, 180, 0},
            {0.70F, -0.27F, 0.21F, 0.14F, 68, -6, 46, 180, 0},
            {0.82F, -0.25F, 0.19F, 0.12F, 60, -10, 50, 360, 0},
            {1.00F, 0, 0, 0, 0, 0, 0, 360, 0},
    };
    /** a quick stab forward, a wrist roll and a flip */
    private static final float[][] FLICK = {
            {0.00F, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.08F, 0.02F, 0.04F, 0.10F, 0, -15, 0, 0, 0},
            {0.16F, -0.05F, 0.06F, -0.30F, 0, 10, 0, 0, 0},
            {0.24F, -0.04F, 0.05F, -0.22F, 0, 5, -20, 0, 0},
            {0.38F, -0.20F, 0.18F, 0.06F, 60, -8, 50, 0, 0},
            {0.48F, -0.21F, 0.20F, 0.08F, 62, -8, 140, 0, 0},
            {0.58F, -0.22F, 0.19F, 0.09F, 64, -6, 48, 360, 0},
            {0.80F, -0.23F, 0.19F, 0.10F, 60, -8, 48, 360, 0},
            {1.00F, 0, 0, 0, 0, 0, 0, 360, 0},
    };

    /** name, keys, seconds, and the part that repeats while you hold the inspect key */
    private record Anim(String name, float[][] keys, float seconds, float loopEnter, float loopExit) {
    }

    private static final Anim[] ANIMS = {
            new Anim("Toss & Catch", TOSS, 2.8F, 0.56F, 0.78F),
            new Anim("Spinner", SPINNER, 2.6F, 0.30F, 0.48F),
            new Anim("Blade Check", BLADE_CHECK, 3.6F, 0.52F, 0.70F),
            new Anim("Flick & Stab", FLICK, 2.4F, 0.58F, 0.80F),
    };

    /**
     * Called (through a mixin) when CS Knife picks the clip for a new inspect. Returns the next animation as an
     * Anubis clip, or null to let Anubis use its own (module off / Anubis' own picked this time).
     */
    public static Object pick(Object anubisDefault, String style) {
        InspectCycle m = active;
        if (m == null) return null;
        List<Integer> on = new ArrayList<>();
        boolean[] enabled = {m.toss.get(), m.spinner.get(), m.bladeCheck.get(), m.flick.get()};
        for (int i = 0; i < ANIMS.length; i++) if (enabled[i]) on.add(i);
        if (m.anubisOwn.get()) {
            on.add(100); // Classic
            on.add(101); // Twirl
        }
        if (on.isEmpty()) return null;
        int choice;
        if ("Random".equals(m.order.get()) && on.size() > 1) {
            do {
                choice = on.get(ThreadLocalRandom.current().nextInt(on.size()));
            } while (choice == m.last);
        } else {
            choice = on.get(Math.floorMod(m.next++, on.size()));
        }
        m.last = choice;
        if (choice == 100) return anubisClip("Classic");
        if (choice == 101) return anubisClip("Twirl");
        Anim a = ANIMS[choice];
        return makeClip(a.keys, a.seconds / m.speed.get(), a.loopEnter, a.loopExit);
    }

    // ---------------------------------------------------------------- building Anubis clips (its classes aren't public)

    private static final String CLIP = "net.anubis.client.module.impl.render.InspectAnimation$Clip";
    private static Constructor<?> clipConstructor;
    private static java.lang.reflect.Method anubisClip;

    private static Object makeClip(float[][] keys, float seconds, float loopEnter, float loopExit) {
        try {
            if (clipConstructor == null) {
                clipConstructor = Class.forName(CLIP).getDeclaredConstructor(float[][].class, float.class, float.class, float.class);
                clipConstructor.setAccessible(true);
            }
            return clipConstructor.newInstance(keys, seconds, loopEnter, loopExit);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Inspect Cycle couldn't build an animation", e);
            return null;
        }
    }

    private static boolean building;

    private static Object anubisClip(String name) {
        try {
            if (anubisClip == null) {
                anubisClip = Class.forName("net.anubis.client.module.impl.render.InspectAnimation").getDeclaredMethod("clip", String.class);
                anubisClip.setAccessible(true);
            }
            building = true; // so our own hook lets this call through
            return anubisClip.invoke(null, name);
        } catch (ReflectiveOperationException e) {
            return null;
        } finally {
            building = false;
        }
    }

    public static boolean passThrough() {
        return building;
    }
}
