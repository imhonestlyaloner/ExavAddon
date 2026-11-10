package com.exav.addon.holiday;

import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where everything is at a given moment of an inspect, in hand space (the space Minecraft draws the held item in:
 * x right, y up, z toward the camera; the camera sits at about (-0.56, 0.52, 0.72)).
 */
public final class HolidayAnimation {
    /** the gingerbread man's waving arm hanging down and out when he isn't waving */
    static final float REST_UPPER = -40.0F, REST_FORE = -10.0F;

    /** where the fist rests */
    public static final Vector3f FIST = new Vector3f(-0.06F, 0.06F, 0.02F);
    /** just below the middle of the screen, a little in front of the camera */
    public static final Vector3f MOUTH = new Vector3f(-0.52F, 0.41F, 0.30F);

    public enum Skin {
        CANDY_CANE("Candy Cane", 3.4F, 0.021F, HolidayModels.CANE_GRIP_X, HolidayModels.CANE_GRIP_Y, 0.0F),
        HOT_COCOA("Hot Cocoa", 5.0F, 0.024F, HolidayModels.MUG_GRIP_X, HolidayModels.MUG_GRIP_Y, 0.0F),
        GINGERBREAD("Gingerbread Man", 4.6F, 0.022F, HolidayModels.GINGER_GRIP_X, HolidayModels.GINGER_GRIP_Y, 0.0F),
        BIRTHDAY_CAKE("Birthday Cake", CakeShow.END, 0.022F, HolidayModels.CAKE_GRIP_X, HolidayModels.CAKE_GRIP_Y, 0.0F);

        public final String label;
        public final float seconds;
        public final float voxel;
        public final float gripX, gripY, gripZ;

        Skin(String label, float seconds, float voxel, float gripX, float gripY, float gripZ) {
            this.label = label;
            this.seconds = seconds;
            this.voxel = voxel;
            this.gripX = gripX;
            this.gripY = gripY;
            this.gripZ = gripZ;
        }

        public static Skin of(String label) {
            for (Skin s : values()) {
                if (s.label.equals(label)) return s;
            }
            return CANDY_CANE;
        }

        /** how it's held when not inspecting */
        public Quaternionf restRotation() {
            return switch (this) {
                // straight part up, leaning toward the middle of the screen and away from you
                case CANDY_CANE -> new Quaternionf().rotationTo(0, 1, 0, -0.25F, 0.9F, -0.45F);
                // upright, turned a little so you see its front and the handle sits in your hand
                case HOT_COCOA -> new Quaternionf().rotationY((float) Math.toRadians(-20));
                // upright, facing you, slightly tilted
                case GINGERBREAD -> new Quaternionf().rotationZ((float) Math.toRadians(8)).rotateY((float) Math.toRadians(-15));
                // flat on your palm, tipped toward you a little
                case BIRTHDAY_CAKE -> new Quaternionf().rotationX((float) Math.toRadians(10));
            };
        }
    }

    /** A falling marshmallow (hand space position, tumble angle). */
    public record Falling(Vector3f pos, float spin) {
    }

    /** A crumb flying off a bite (hand space position, colour). */
    public record Crumb(Vector3f pos, int rgb) {
    }

    /** Everything that's drawn this frame. */
    public static final class Frame {
        public final Vector3f hand = new Vector3f();
        /** where the item's grip point is (equal to hand while it's held) */
        public final Vector3f item = new Vector3f();
        public final Quaternionf rotation = new Quaternionf();
        public boolean bitten;
        /** 0 = item not shown (the cake after it blew up) */
        public float itemScale = 1.0F;
        /** gingerbread waving arm: shoulder and elbow angles, degrees (0 = straight out sideways) */
        public float upperArm = REST_UPPER, foreArm = REST_FORE;
        /** marshmallows already in the mug */
        public int caught;
        public final List<Falling> falling = new ArrayList<>();
        public final List<Crumb> crumbs = new ArrayList<>();
    }

    private HolidayAnimation() {
    }

    /** The pose when not inspecting. */
    public static Frame rest(Skin skin) {
        Frame f = new Frame();
        f.hand.set(FIST);
        f.item.set(FIST);
        f.rotation.set(skin.restRotation());
        return f;
    }

    /** The pose {@code t} seconds into the inspect (t >= skin.seconds is the end). size = knife size setting. */
    public static Frame at(Skin skin, float t, float size) {
        return switch (skin) {
            case CANDY_CANE -> candyCane(t, size);
            case HOT_COCOA -> hotCocoa(t, size);
            case GINGERBREAD -> gingerbread(t, size);
            case BIRTHDAY_CAKE -> CakeShow.frame(t, size);
        };
    }

    // ------------------------------------------------------------------ candy cane
    // toss it up spinning end over end, catch it by the hook, bring it to your face, bite the top off, put it back

    private static Frame candyCane(float t, float size) {
        Skin skin = Skin.CANDY_CANE;
        Frame f = new Frame();
        Quaternionf rest = skin.restRotation();
        float scale = skin.voxel * size;
        Vector3f dip = new Vector3f(FIST).add(0.0F, -0.07F, 0.01F);
        Vector3f ready = new Vector3f(FIST).add(0.0F, 0.03F, 0.0F);
        // "biting" pose: the straight part points at your mouth
        Quaternionf biteRot = new Quaternionf().rotationTo(0, 1, 0, -0.6F, 0.6F, 0.52F);
        Vector3f biteGrip = gripFor(MOUTH, biteRot, scale, skin, HolidayModels.CANE_BITE_X, HolidayModels.CANE_BITE_Y);

        if (t < 0.25F) { // wind up
            float u = smooth(t / 0.25F);
            f.hand.set(FIST).lerp(dip, u);
            f.item.set(f.hand);
            f.rotation.set(rest);
        } else if (t < 1.15F) { // in the air: two flips end over end
            float u = (t - 0.25F) / 0.9F;
            f.hand.set(dip).lerp(ready, smooth(Math.min(1.0F, u / 0.8F)));
            // the middle of the cane flies in an arc and it spins around its middle
            float midX = 2.0F, midY = 11.0F;
            Vector3f from = pointOf(dip, rest, scale, skin, midX, midY, skin.gripZ);
            Vector3f to = pointOf(ready, rest, scale, skin, midX, midY, skin.gripZ);
            Vector3f mid = new Vector3f(from).lerp(to, u).add(0.0F, 0.36F * 4.0F * u * (1.0F - u), 0.0F);
            f.rotation.set(new Quaternionf().rotationX((float) (Math.PI * 4.0 * u)).mul(rest));
            f.item.set(gripFor(mid, f.rotation, scale, skin, midX, midY, skin.gripZ));
        } else if (t < 1.35F) { // caught by the hook, small give
            float u = (t - 1.15F) / 0.2F;
            f.hand.set(ready).add(0.0F, -0.045F * (float) Math.sin(Math.PI * u), 0.0F);
            f.item.set(f.hand);
            f.rotation.set(rest);
        } else if (t < 1.95F) { // up to your face
            float u = smooth((t - 1.35F) / 0.6F);
            f.hand.set(ready).lerp(biteGrip, u);
            f.item.set(f.hand);
            f.rotation.set(rest).slerp(biteRot, u);
        } else if (t < 2.8F) { // bite (chomp at ~2.1), then chew
            float u = t - 1.95F;
            float chomp = u < 0.3F ? (float) Math.sin(Math.PI * u / 0.3F) : 0.0F;
            float chew = u > 0.35F ? 0.012F * (float) Math.sin((u - 0.35F) * 22.0F) : 0.0F;
            f.hand.set(biteGrip).add(-0.01F * chomp, 0.02F * chomp + chew, 0.05F * chomp);
            f.item.set(f.hand);
            f.rotation.set(biteRot);
            f.bitten = t >= 2.1F;
        } else { // back down
            float u = smooth(Math.min(1.0F, (t - 2.8F) / 0.6F));
            f.hand.set(biteGrip).lerp(FIST, u);
            f.item.set(f.hand);
            f.rotation.set(biteRot).slerp(rest, u);
            f.bitten = true;
        }
        if (t >= 2.1F) crumbs(f, t - 2.1F, MOUTH, new int[]{HolidayModels.RED, HolidayModels.WHITE}, 7);
        return f;
    }

    // ------------------------------------------------------------------ hot cocoa
    // hold the mug up, marshmallows drop from the sky, move the mug under each one, then take a sip

    private static final float[][] MALLOWS = { // {drop time, x offset, z offset}
            {0.55F, -0.10F, 0.00F},
            {1.20F, 0.08F, -0.05F},
            {1.85F, -0.03F, 0.04F},
            {2.50F, 0.11F, -0.02F},
    };
    private static final float FALL = 0.65F;
    /** where caught marshmallows float, in mug model coords */
    public static final float[][] MALLOW_SLOTS = {{-2.0F, -1.0F}, {2.0F, 1.5F}, {-0.5F, 2.5F}, {1.5F, -2.5F}};

    private static Frame hotCocoa(float t, float size) {
        Skin skin = Skin.HOT_COCOA;
        Frame f = new Frame();
        Quaternionf rest = skin.restRotation();
        float scale = skin.voxel * size;
        Vector3f up = new Vector3f(FIST).add(-0.12F, 0.12F, -0.08F);
        // drinking pose: tipped toward you, near rim at your mouth
        Quaternionf sipRot = new Quaternionf().rotationX((float) Math.toRadians(50)).mul(rest);
        Vector3f sipGrip = gripFor(MOUTH, sipRot, scale, skin, 0.0F, HolidayModels.MUG_RIM_Y, HolidayModels.MUG_RIM_R);

        f.rotation.set(rest);
        if (t < 0.5F) { // lift it up
            f.hand.set(FIST).lerp(up, smooth(t / 0.5F));
        } else if (t < 3.35F) { // catching: move under whichever marshmallow lands next
            f.hand.set(catchPosition(up, t));
            // little dip each time one lands
            for (float[] m : MALLOWS) {
                float since = t - (m[0] + FALL);
                if (since >= 0 && since < 0.18F) f.hand.y -= 0.025F * (float) Math.sin(Math.PI * since / 0.18F);
            }
        } else if (t < 4.0F) { // to your mouth, tipping
            float u = smooth((t - 3.35F) / 0.65F);
            f.hand.set(catchPosition(up, 3.35F)).lerp(sipGrip, u);
            f.rotation.set(rest).slerp(sipRot, u);
        } else if (t < 4.45F) { // sip: tip a bit more and back
            float u = (t - 4.0F) / 0.45F;
            float extra = (float) Math.toRadians(14) * (float) Math.sin(Math.PI * u);
            f.rotation.set(new Quaternionf().rotationX(extra).mul(sipRot));
            f.hand.set(gripFor(MOUTH, f.rotation, scale, skin, 0.0F, HolidayModels.MUG_RIM_Y, HolidayModels.MUG_RIM_R));
        } else { // back down
            float u = smooth(Math.min(1.0F, (t - 4.45F) / 0.55F));
            f.hand.set(sipGrip).lerp(FIST, u);
            f.rotation.set(sipRot).slerp(rest, u);
        }
        f.item.set(f.hand);

        for (int i = 0; i < MALLOWS.length; i++) {
            float[] m = MALLOWS[i];
            float land = m[0] + FALL;
            if (t >= land) {
                f.caught++;
            } else if (t >= m[0]) {
                // falls onto the cocoa where the mug will be when it lands
                Vector3f mugAt = catchPosition(up, land);
                Vector3f surface = pointOf(mugAt, rest, scale, skin, MALLOW_SLOTS[i][0], HolidayModels.MUG_SURFACE_Y + 0.9F, MALLOW_SLOTS[i][1]);
                float u = (t - m[0]) / FALL;
                Vector3f pos = new Vector3f(surface).add(0.02F * (1 - u), 1.1F * (1.0F - u * u), 0.0F);
                f.falling.add(new Falling(pos, u * 540.0F + i * 70.0F));
            }
        }
        return f;
    }

    /** where the mug's hand is while catching: heading for the next marshmallow's spot */
    private static Vector3f catchPosition(Vector3f up, float t) {
        Vector3f from = new Vector3f(up);
        for (float[] m : MALLOWS) {
            Vector3f target = new Vector3f(up).add(m[1], 0.0F, m[2]);
            float land = m[0] + FALL;
            float start = land - 0.6F;
            if (t <= start) return from;
            if (t < land - 0.12F) return new Vector3f(from).lerp(target, smooth((t - start) / 0.48F));
            from = target;
        }
        return from;
    }

    // ------------------------------------------------------------------ gingerbread man
    // hold him up facing you, he waves, then you bite his head

    private static Frame gingerbread(float t, float size) {
        Skin skin = Skin.GINGERBREAD;
        Frame f = new Frame();
        Quaternionf rest = skin.restRotation();
        float scale = skin.voxel * size;
        Vector3f show = new Vector3f(-0.46F, 0.18F, -0.12F);
        Quaternionf showRot = new Quaternionf().rotationZ((float) Math.toRadians(-3));
        Quaternionf biteRot = new Quaternionf().rotationX((float) Math.toRadians(28)).rotateY((float) Math.toRadians(-25));
        Vector3f biteGrip = gripFor(MOUTH, biteRot, scale, skin, HolidayModels.GINGER_BITE_X, HolidayModels.GINGER_BITE_Y, 0.0F);

        if (t < 0.5F) { // lift him up in front of you
            float u = smooth(t / 0.5F);
            f.hand.set(FIST).lerp(show, u);
            f.rotation.set(rest).slerp(showRot, u);
        } else if (t < 2.25F) { // he waves like a person: arm up, elbow bent, hand swaying from the elbow
            float u = t - 0.5F;
            float raise = smooth(Math.min(1.0F, u / 0.3F)) * smooth(Math.min(1.0F, (1.75F - u) / 0.35F));
            float sway = (float) Math.sin(u * Math.PI * 2.0 * 1.7) * smooth(Math.min(1.0F, (u - 0.15F) / 0.25F))
                    * smooth(Math.min(1.0F, (1.75F - u) / 0.4F));
            float lag = (float) Math.sin(u * Math.PI * 2.0 * 1.7 - 0.9);
            // shoulder: from hanging down to raised up and out, following the hand a little
            f.upperArm = REST_UPPER + (55.0F - REST_UPPER) * raise + 7.0F * lag * raise;
            // elbow: bent up, forearm swinging side to side
            f.foreArm = REST_FORE + (55.0F - REST_FORE) * raise + 32.0F * sway;
            // he leans into it a bit
            f.hand.set(show).add(0.0F, 0.01F * Math.abs((float) Math.sin(u * Math.PI * 1.7)), 0.0F);
            f.rotation.set(new Quaternionf(showRot).rotateZ((float) Math.toRadians(-3.0F * sway * raise)));
        } else if (t < 2.9F) { // to your mouth
            float u = smooth((t - 2.25F) / 0.65F);
            f.hand.set(show).lerp(biteGrip, u);
            f.rotation.set(showRot).slerp(biteRot, u);
        } else if (t < 3.75F) { // bite (chomp at ~3.0), chew
            float u = t - 2.9F;
            float chomp = u < 0.3F ? (float) Math.sin(Math.PI * u / 0.3F) : 0.0F;
            float chew = u > 0.35F ? 0.012F * (float) Math.sin((u - 0.35F) * 22.0F) : 0.0F;
            f.hand.set(biteGrip).add(-0.01F * chomp, 0.02F * chomp + chew, 0.05F * chomp);
            f.rotation.set(biteRot);
            f.bitten = t >= 3.05F;
        } else { // back down
            float u = smooth(Math.min(1.0F, (t - 3.75F) / 0.85F));
            f.hand.set(biteGrip).lerp(FIST, u);
            f.rotation.set(biteRot).slerp(rest, u);
            f.bitten = true;
        }
        f.item.set(f.hand);
        if (t >= 3.05F) crumbs(f, t - 3.05F, MOUTH, new int[]{HolidayModels.CRUMB_COOKIE}, 8);
        return f;
    }

    // ------------------------------------------------------------------ helpers

    /** grip position so that model point (px, py, pz) lands on {@code target} */
    static Vector3f gripFor(Vector3f target, Quaternionf rot, float scale, Skin skin, float px, float py) {
        return gripFor(target, rot, scale, skin, px, py, skin.gripZ);
    }

    static Vector3f gripFor(Vector3f target, Quaternionf rot, float scale, Skin skin, float px, float py, float pz) {
        Vector3f offset = new Vector3f(px - skin.gripX, py - skin.gripY, pz - skin.gripZ).mul(scale);
        rot.transform(offset);
        return new Vector3f(target).sub(offset);
    }

    /** hand space position of model point (px, py, pz) when the grip is at {@code grip} */
    static Vector3f pointOf(Vector3f grip, Quaternionf rot, float scale, Skin skin, float px, float py, float pz) {
        Vector3f offset = new Vector3f(px - skin.gripX, py - skin.gripY, pz - skin.gripZ).mul(scale);
        rot.transform(offset);
        return offset.add(grip);
    }

    /** crumbs burst from the bite and fall for 0.8s */
    private static void crumbs(Frame f, float dt, Vector3f origin, int[] colors, int count) {
        if (dt > 0.8F) return;
        for (int i = 0; i < count; i++) {
            float a = i * 2.39996F; // golden angle spread
            float speed = 0.35F + 0.15F * ((i * 37) % 5) / 5.0F;
            float vx = (float) Math.cos(a) * speed * 0.6F, vz = (float) Math.sin(a) * speed * 0.4F - 0.15F, vy = 0.25F + 0.1F * (i % 3);
            Vector3f p = new Vector3f(origin).add(vx * dt, vy * dt - 1.6F * dt * dt, vz * dt).add(0.0F, -0.03F, -0.04F);
            f.crumbs.add(new Crumb(p, colors[i % colors.length]));
        }
    }

    static float smooth(float u) {
        u = Math.max(0.0F, Math.min(1.0F, u));
        return u * u * (3.0F - 2.0F * u);
    }
}
