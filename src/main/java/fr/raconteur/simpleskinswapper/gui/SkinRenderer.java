package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SkinRenderer {

    // GUI Y goes down, 3D Y goes up → flip 180° around Z to avoid upside-down rendering.
    private static final Quaternionf BASE_ROTATION = new Quaternionf().rotationZ((float) Math.PI);

    // Player model origin is at the feet; offset by 1.1 to center the model vertically in the preview area.
    private static final Vector3f MODEL_OFFSET = new Vector3f(0.0F, 1.1F, 0.0F);

    public static void renderPlayer(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int size, PlayerSkin skin) {
        AvatarRenderState state = buildRenderState(skin);
        context.enableScissor(x1, y1, x2, y2);
        GuiEntityRenderState element = new GuiEntityRenderState(
                state, MODEL_OFFSET, BASE_ROTATION, null,
                x1, y1, x2, y2, (float) size, context.scissorStack.peek()
        );
        context.guiRenderState.addPicturesInPictureState(element);
        context.disableScissor();
    }

    private static AvatarRenderState buildRenderState(PlayerSkin skin) {
        AvatarRenderState s = new AvatarRenderState();

        // Identity
        s.boundingBoxWidth = 0.6F;
        s.boundingBoxHeight = 1.8F;
        s.eyeHeight = 1.62F;
        s.scale = 1.0F;
        s.ageScale = 1.0F;
        s.ageInTicks = SimpleSkinSwapperClient.TOTAL_TICK_DELTA;
        s.id = 0;

        // Pose
        s.pose = Pose.STANDING;
        s.bodyRot = 180.0F;
        s.yRot = 0.0F;
        s.xRot = 0.0F;

        // Idle arm animation
        float t = SimpleSkinSwapperClient.TOTAL_TICK_DELTA * 0.067F;
        s.walkAnimationPos = Mth.sin(t) * 0.05F;
        s.walkAnimationSpeed = 0.1F;
        s.attackTime = 0.0F;
        // s.handSwinging = false; old
        s.swimAmount = 0.0F;
        s.speedValue = 1.0F;

        // Skin
        s.skin = skin;
        // s.name = ""; old
        s.scoreText = null;
        s.isSpectator = false;
        s.showHat = true;
        s.showJacket = true;
        s.showLeftSleeve = true;
        s.showRightSleeve = true;
        s.showLeftPants = true;
        s.showRightPants = true;
        s.showCape = false;
        s.parrotOnLeftShoulder = null;
        s.parrotOnRightShoulder = null;

        // Inactive
        s.isInvisible = false;
        s.isInvisibleToPlayer = false;
        s.displayFireAnimation = false;
        s.hasRedOverlay = false;
        s.deathTime = 0.0F;
        s.isDiscrete = false;
        s.isCrouching = false;
        s.isBaby = false;
        s.isUpsideDown = false;
        s.isFullyFrozen = false;
        // s.hasOutline = false; old
        // s.customName = null; old
        s.bedOrientation = null;
        s.isInWater = false;
        s.isAutoSpinAttack = false;
        s.isPassenger = false;
        s.isUsingItem = false;
        s.isFallFlying = false;
        s.fallFlyingTimeInTicks = 0.0F;
        s.shouldApplyFlyingYRot = false;
        s.flyingYRot = 0.0F;
        s.isVisuallySwimming = false;
        s.maxCrossbowChargeDuration = 0.0F;
        s.ticksUsingItem = 0;
        // s.itemUseTimeLeft = 0; old
        s.arrowCount = 0;
        s.stingerCount = 0;
        s.elytraRotX = 0.0F;
        s.elytraRotY = 0.0F;
        s.elytraRotZ = 0.0F;

        return s;
    }
}
