package com.exav.addon.holiday;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws your first-person arm so the fist sits at a given point, with the forearm running back toward the elbow
 * (same approach Anubis's CS Knife uses).
 */
public final class HandArm {
    /** direction from the fist back to the elbow, in hand space */
    public static final Vector3f FOREARM = new Vector3f(0.75F, -0.45F, 0.5F).normalize();
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);

    private final AvatarRenderer<AbstractClientPlayer> renderer;
    private final Identifier texture;
    private final boolean rightSleeve;
    private final boolean leftSleeve;
    private final boolean slim;

    private HandArm(AvatarRenderer<AbstractClientPlayer> renderer, Identifier texture, boolean rightSleeve, boolean leftSleeve, boolean slim) {
        this.renderer = renderer;
        this.texture = texture;
        this.rightSleeve = rightSleeve;
        this.leftSleeve = leftSleeve;
        this.slim = slim;
    }

    public static HandArm current() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible()) return null;
        PlayerSkin skin = player.getSkin();
        return new HandArm(mc.getEntityRenderDispatcher().getPlayerRenderer(player), skin.body().texturePath(),
                player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),
                skin.model() == PlayerModelType.SLIM);
    }

    /** Draws the arm with its fist at {@code hand}, coming from {@code elbow}. left = draw the left arm (pose already mirrored). */
    public void draw(PoseStack pose, Vector3f hand, Vector3f elbow, boolean left, SubmitNodeCollector collector, int light) {
        Vector3f along = new Vector3f(hand).sub(elbow).normalize();
        Vector3f front = new Vector3f(UP).fma(-UP.dot(along), along).normalize();
        Vector3f side = new Vector3f(along).cross(front);
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        pose.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(side, along, front)));
        part(pose, left, collector, light);
        // stretch the upper arm back toward the shoulder, off screen
        pose.translate(0.0F, -0.625F, 0.0F);
        pose.scale(-1.0F, -3.0F, 1.0F);
        pose.translate(0.0F, 0.625F, 0.0F);
        part(pose, left, collector, light);
        pose.popPose();
    }

    private void part(PoseStack pose, boolean left, SubmitNodeCollector collector, int light) {
        pose.pushPose();
        pose.translate(slim ? 0.03125F : 0.0625F, -0.5F, 0.0F);
        pose.mulPose(Axis.ZP.rotation(-0.1F));
        pose.translate(0.3125F, -0.125F, 0.0F);
        if (left) {
            pose.scale(-1.0F, 1.0F, 1.0F);
            renderer.renderLeftHand(pose, collector, light, texture, leftSleeve);
        } else {
            renderer.renderRightHand(pose, collector, light, texture, rightSleeve);
        }
        pose.popPose();
    }
}
