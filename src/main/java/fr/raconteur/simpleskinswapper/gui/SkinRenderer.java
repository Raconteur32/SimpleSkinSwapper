package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SkinRenderer {

    // GUI Y goes down, 3D Y goes up → flip 180° around Z to avoid upside-down rendering.
    private static final Quaternionf BASE_ROTATION = new Quaternionf().rotationZ((float) Math.PI);

    // Raw model height feet-to-head-top in this pipeline's local units, derived from HumanoidModel's
    // part geometry (legs 12px + body 12px + head 8px = 32px = 2.0 blocks), not the 1.8 entity hitbox
    // (which is unrelated to the rendered mesh size). Plus ~0.03 for the hat/hair overlay layer, which
    // is drawn 0.5px larger than the head cube on every side.
    private static final float MODEL_HEIGHT = 2.03F;

    // Symmetric vertical centering offset: half the model height (plus a hair's-width safety margin)
    // so feet and head-top land equally far from the box's vertical center.
    private static final Vector3f MODEL_OFFSET = new Vector3f(0.0F, MODEL_HEIGHT / 2.0F + 0.01F, 0.0F);

    // Approximate torso height, used only as the drag-rotate pivot so tilting swings around the chest
    // instead of the feet. Independent of MODEL_OFFSET/framing.
    private static final float PIVOT_HEIGHT = 0.9F;

    // Every call site sizes the render as previewHeight/2, which by itself maps a fixed ±1.0-unit world
    // window onto the box — too small to fit the full ~2.03-unit-tall model. Widen the window to match
    // MODEL_OFFSET (plus a small margin) so the model fills the preview without clipping the head.
    private static final float PREVIEW_HALF_HEIGHT = MODEL_HEIGHT / 2.0F + 0.02F;

    public static void renderPlayer(DrawContext context, int x1, int y1, int x2, int y2, int size, SkinTextures skin) {
        renderState(context, x1, y1, x2, y2, size, buildRenderState(skin), BASE_ROTATION, MODEL_OFFSET);
    }

    /**
     * Same as {@link #renderPlayer}, but turns the head/body toward the mouse cursor,
     * mirroring vanilla's inventory-screen player preview.
     */
    public static void renderPlayerFollowingMouse(DrawContext context, int x1, int y1, int x2, int y2, int size, SkinTextures skin, int mouseX, int mouseY) {
        PlayerEntityRenderState state = buildRenderState(skin);

        float centerX = (x1 + x2) / 2.0F;
        float centerY = (y1 + y2) / 2.0F;
        float xAngle = (float) Math.atan((centerX - mouseX) / 40.0);
        float yAngle = (float) Math.atan((centerY - mouseY) / 40.0);
        state.bodyYaw = 180.0F + xAngle * 20.0F;
        state.relativeHeadYaw = xAngle * 20.0F;
        state.pitch = -yAngle * 20.0F;

        renderState(context, x1, y1, x2, y2, size, state, BASE_ROTATION, MODEL_OFFSET);
    }

    /**
     * Same as {@link #renderPlayer}, but orbits the whole model by the given yaw/pitch,
     * for drag-to-rotate previews. Unlike {@link #renderPlayerFollowingMouse}, this rotates
     * the entire model (not just the head/body pose), allowing the camera to reach the top
     * of the head or the underside of the feet. The rotation pivots around the model's
     * mid-body height rather than its feet.
     */
    public static void renderPlayerRotatable(DrawContext context, int x1, int y1, int x2, int y2, int size, SkinTextures skin, float yawDegrees, float pitchDegrees) {
        Quaternionf yawPitch = new Quaternionf()
                .rotateY((float) Math.toRadians(yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees));
        Quaternionf rotation = new Quaternionf(yawPitch).rotateZ((float) Math.PI);

        // The renderer applies p' = rotation * p + translation, where rotation is (yawPitch * baseFlip)
        // applied to model-local points: the base flip runs first, then yaw/pitch. So the pivot must be
        // expressed in post-flip space (Y negated) and only rotated by yawPitch (not the flip) to find
        // how far it moved, before compensating the translation to keep it fixed.
        Vector3f flippedPivot = new Vector3f(0.0F, -PIVOT_HEIGHT, 0.0F);
        Vector3f rotatedPivot = yawPitch.transform(new Vector3f(flippedPivot));
        Vector3f translation = new Vector3f(flippedPivot).sub(rotatedPivot).add(MODEL_OFFSET);

        renderState(context, x1, y1, x2, y2, size, buildRenderState(skin), rotation, translation);
    }

    private static void renderState(DrawContext context, int x1, int y1, int x2, int y2, int size, PlayerEntityRenderState state, Quaternionf rotation, Vector3f translation) {
        context.enableScissor(x1, y1, x2, y2);
        float effectiveSize = size / PREVIEW_HALF_HEIGHT;
        EntityGuiElementRenderState element = new EntityGuiElementRenderState(
                state, translation, rotation, null,
                x1, y1, x2, y2, effectiveSize, context.scissorStack.peekLast()
        );
        context.state.addSpecialElement(element);
        context.disableScissor();
    }

    private static PlayerEntityRenderState buildRenderState(SkinTextures skin) {
        PlayerEntityRenderState s = new PlayerEntityRenderState();

        // Identity
        s.width = 0.6F;
        s.height = 1.8F;
        s.standingEyeHeight = 1.62F;
        s.baseScale = 1.0F;
        s.ageScale = 1.0F;
        s.age = SimpleSkinSwapperClient.TOTAL_TICK_DELTA;
        s.id = 0;

        // Pose
        s.pose = EntityPose.STANDING;
        s.bodyYaw = 180.0F;
        s.relativeHeadYaw = 0.0F;
        s.pitch = 0.0F;

        // Idle arm animation
        float t = SimpleSkinSwapperClient.TOTAL_TICK_DELTA * 0.067F;
        s.limbSwingAnimationProgress = MathHelper.sin(t) * 0.05F;
        s.limbSwingAmplitude = 0.1F;
        s.handSwingProgress = 0.0F;
        s.leaningPitch = 0.0F;
        s.limbAmplitudeInverse = 1.0F;

        // Skin
        s.skinTextures = skin;
        s.playerName = null;
        s.spectator = false;
        s.hatVisible = true;
        s.jacketVisible = true;
        s.leftSleeveVisible = true;
        s.rightSleeveVisible = true;
        s.leftPantsLegVisible = true;
        s.rightPantsLegVisible = true;
        s.capeVisible = false;
        s.leftShoulderParrotVariant = null;
        s.rightShoulderParrotVariant = null;

        // Inactive
        s.invisible = false;
        s.invisibleToPlayer = false;
        s.onFire = false;
        s.hurt = false;
        s.deathTime = 0.0F;
        s.sneaking = false;
        s.isInSneakingPose = false;
        s.baby = false;
        s.flipUpsideDown = false;
        s.shaking = false;
        s.sleepingDirection = null;
        s.touchingWater = false;
        s.usingRiptide = false;
        s.hasVehicle = false;
        s.isUsingItem = false;
        s.isGliding = false;
        s.glidingTicks = 0.0F;
        s.applyFlyingRotation = false;
        s.flyingRotation = 0.0F;
        s.isSwimming = false;
        s.crossbowPullTime = 0.0F;
        s.itemUseTime = 0;
        s.stuckArrowCount = 0;
        s.stingerCount = 0;
        s.leftWingPitch = 0.0F;
        s.leftWingYaw = 0.0F;
        s.leftWingRoll = 0.0F;

        return s;
    }
}
