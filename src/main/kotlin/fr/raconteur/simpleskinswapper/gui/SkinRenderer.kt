package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Quaternionf
import org.joml.Vector3f

object SkinRenderer {

    // GUI Y goes down, 3D Y goes up → flip 180° around Z to avoid upside-down rendering.
    internal val BASE_ROTATION = Quaternionf().rotationZ(Math.PI.toFloat())

    // Raw model height feet-to-head-top in this pipeline's local units, derived from HumanoidModel's
    // part geometry (legs 12px + body 12px + head 8px = 32px = 2.0 blocks), not the 1.8 entity hitbox
    // (which is unrelated to the rendered mesh size). Plus ~0.03 for the hat/hair overlay layer, which
    // is drawn 0.5px larger than the head cube on every side.
    private const val MODEL_HEIGHT = 2.03F

    // Symmetric vertical centering offset: half the model height (plus a hair's-width safety margin)
    // so feet and head-top land equally far from the box's vertical center.
    internal val MODEL_OFFSET = Vector3f(0.0F, MODEL_HEIGHT / 2.0F + 0.01F, 0.0F)

    // Approximate torso height, used only as the drag-rotate pivot so tilting swings around the chest
    // instead of the feet. Independent of MODEL_OFFSET/framing.
    private const val PIVOT_HEIGHT = 0.9F

    // Every call site sizes the render as previewHeight/2, which by itself maps a fixed ±1.0-unit world
    // window onto the box — too small to fit the full ~2.03-unit-tall model. Widen the window to match
    // MODEL_OFFSET (plus a small margin) so the model fills the preview without clipping the head.
    internal const val PREVIEW_HALF_HEIGHT = MODEL_HEIGHT / 2.0F + 0.02F

    @JvmStatic
    fun renderPlayer(context: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, size: Int, skin: PlayerSkin) {
        renderState(context, x1, y1, x2, y2, size, buildRenderState(skin), BASE_ROTATION, MODEL_OFFSET)
    }

    /**
     * Same as [renderPlayer], but turns the head/body toward the mouse cursor,
     * mirroring vanilla's inventory-screen player preview.
     */
    @JvmStatic
    fun renderPlayerFollowingMouse(
        context: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, size: Int,
        skin: PlayerSkin, mouseX: Int, mouseY: Int
    ) {
        val state = buildRenderState(skin)

        val centerX = (x1 + x2) / 2.0F
        val centerY = (y1 + y2) / 2.0F
        val xAngle = Math.atan((centerX - mouseX) / 40.0).toFloat()
        val yAngle = Math.atan((centerY - mouseY) / 40.0).toFloat()
        state.bodyRot = 180.0F + xAngle * 20.0F
        state.yRot = xAngle * 20.0F
        state.xRot = -yAngle * 20.0F

        renderState(context, x1, y1, x2, y2, size, state, BASE_ROTATION, MODEL_OFFSET)
    }

    /**
     * Same as [renderPlayer], but orbits the whole model by the given yaw/pitch,
     * for drag-to-rotate previews. Unlike [renderPlayerFollowingMouse], this rotates
     * the entire model (not just the head/body pose), allowing the camera to reach the top
     * of the head or the underside of the feet. The rotation pivots around the model's
     * mid-body height rather than its feet.
     */
    @JvmStatic
    fun renderPlayerRotatable(
        context: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, size: Int,
        skin: PlayerSkin, yawDegrees: Float, pitchDegrees: Float
    ) {
        val yawPitch = Quaternionf()
            .rotateY(Math.toRadians(yawDegrees.toDouble()).toFloat())
            .rotateX(Math.toRadians(pitchDegrees.toDouble()).toFloat())
        val rotation = Quaternionf(yawPitch).rotateZ(Math.PI.toFloat())

        // The renderer applies p' = rotation * p + translation, where rotation is (yawPitch * baseFlip)
        // applied to model-local points: the base flip runs first, then yaw/pitch. So the pivot must be
        // expressed in post-flip space (Y negated) and only rotated by yawPitch (not the flip) to find
        // how far it moved, before compensating the translation to keep it fixed.
        val flippedPivot = Vector3f(0.0F, -PIVOT_HEIGHT, 0.0F)
        val rotatedPivot = yawPitch.transform(Vector3f(flippedPivot))
        val translation = Vector3f(flippedPivot).sub(rotatedPivot).add(MODEL_OFFSET)

        renderState(context, x1, y1, x2, y2, size, buildRenderState(skin), rotation, translation)
    }

    private fun renderState(
        context: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, size: Int,
        state: AvatarRenderState, rotation: Quaternionf, translation: Vector3f
    ) {
        context.enableScissor(x1, y1, x2, y2)
        val effectiveSize = size / PREVIEW_HALF_HEIGHT
        val element = GuiEntityRenderState(
            state, translation, rotation, null,
            x1, y1, x2, y2, effectiveSize, context.scissorStack.peek()
        )
        context.guiRenderState.addPicturesInPictureState(element)
        context.disableScissor()
    }

    internal fun buildRenderState(
        skin: PlayerSkin,
        enableMovingLegs: Boolean = fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig.get().enableMovingLegs,
        totalTickDelta: Float = SimpleSkinSwapperClient.TOTAL_TICK_DELTA): AvatarRenderState {
        val s = AvatarRenderState()

        s.boundingBoxWidth = 0.6F
        s.boundingBoxHeight = 1.8F
        s.eyeHeight = 1.62F
        s.scale = 1.0F
        s.ageScale = 1.0F
        s.ageInTicks = totalTickDelta
        s.id = 0

        s.pose = Pose.STANDING
        s.bodyRot = 180.0F
        s.yRot = 0.0F
        s.xRot = 0.0F

        // How fast should the legs swing?
        val progressFactor = 0.12F
        // How far should the legs swing?
        val animationSpeed = 0.20F
        s.walkAnimationPos = if (enableMovingLegs) totalTickDelta * progressFactor else 0.0F
        s.walkAnimationSpeed = if (enableMovingLegs) animationSpeed else 0.0F

        //? if <26.3
        s.attackTime = 0.0F
        s.swimAmount = 0.0F
        s.speedValue = 1.0F

        s.skin = skin
        s.scoreText = null
        s.isSpectator = false
        s.showHat = true
        s.showJacket = true
        s.showLeftSleeve = true
        s.showRightSleeve = true
        s.showLeftPants = true
        s.showRightPants = true
        s.showCape = false
        s.parrotOnLeftShoulder = null
        s.parrotOnRightShoulder = null

        s.isInvisible = false
        s.isInvisibleToPlayer = false
        s.displayFireAnimation = false
        s.hasRedOverlay = false
        s.deathTime = 0.0F
        s.isDiscrete = false
        s.isCrouching = false
        s.isBaby = false
        s.isUpsideDown = false
        s.isFullyFrozen = false
        s.bedOrientation = null
        s.isInWater = false
        s.isAutoSpinAttack = false
        s.isPassenger = false
        s.isUsingItem = false
        s.isFallFlying = false
        s.fallFlyingTimeInTicks = 0.0F
        s.shouldApplyFlyingYRot = false
        s.flyingYRot = 0.0F
        s.isVisuallySwimming = false
        s.maxCrossbowChargeDuration = 0.0F
        s.ticksUsingItem = 0.0F
        s.arrowCount = 0
        s.stingerCount = 0
        s.elytraRotX = 0.0F
        s.elytraRotY = 0.0F
        s.elytraRotZ = 0.0F

        return s
    }
}
