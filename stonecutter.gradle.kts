plugins {
	id("dev.kikugie.stonecutter")
	kotlin("jvm") version "2.4.10" apply false
}

stonecutter active "26.2"

// Pure symbol renames between the 1.21.11 and 26.x APIs, applied textually when
// generating the per-version sources. Written as replace(<1.21.11 form>, <26.x form>);
// the direction flag makes them apply old→new on 26.x (no-op: tokens absent from the
// VCS source) and new→old on 1.21.11. Structural deltas (method arity, override names
// that differ per class) use //? comment conditionals directly in the sources.
stonecutter parameters {
	replacements {
		// GuiGraphics was split into GuiGraphics/GuiGraphicsExtractor in 26.x.
		// Regex with explicit token boundaries: the plain string form would also
		// rewrite spruceui's SpruceGuiGraphics on 26.x versions sharing the VCS form.
		// Direct direction (26.x targets) is a deliberate no-op.
		regex(current.parsed >= "26.1") {
			replace("(?!)x", "unused", "\\bGuiGraphicsExtractor\\b", "GuiGraphics")
		}

		string(current.parsed >= "26.1") {
			replace(".drawCenteredString(", ".centeredText(")
			replace(".drawString(client.font, Component", ".text(client.font, Component")

			// Fabric API renames
			replace("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper")
			replace("KeyBindingHelper.registerKeyBinding(", "KeyMappingHelper.registerKeyMapping(")
			replace("PayloadTypeRegistry.playC2S()", "PayloadTypeRegistry.serverboundPlay()")
			replace("PayloadTypeRegistry.playS2C()", "PayloadTypeRegistry.clientboundPlay()")

			// GUI render-state classes moved packages (the broad rule also covers the .pip subpackage)
			replace("net.minecraft.client.gui.render.state", "net.minecraft.client.renderer.state.gui")
			// Picture-in-picture GUI rendering renamed the submit methods
			replace("submitPicturesInPictureState", "addPicturesInPictureState")
			replace("submitBlitToCurrentLayer", "addBlitToCurrentLayer")
			replace("submitGuiElement", "addGuiElement")

			// Widget rendering pipeline renames (identical on Screen and spruceui widgets)
			replace("renderDefaultSprite", "extractDefaultSprite")
			replace("renderDefaultLabel", "extractDefaultLabel")
			replace("renderContents", "extractContents")
			replace("renderBackground", "extractBackground")
		}
	}
}
