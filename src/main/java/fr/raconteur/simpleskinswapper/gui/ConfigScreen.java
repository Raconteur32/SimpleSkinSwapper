package fr.raconteur.simpleskinswapper.gui;

import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ConfigScreen extends Screen {

    private final Screen parent;
    /** Null when not connected to a multiplayer server. */
    private final String currentServerAddress;
    private EditBox serverCommandField;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("simpleskinswapper.config.title"));
        this.parent = parent;
        ServerData serverInfo = net.minecraft.client.Minecraft.getInstance().getCurrentServer();
        this.currentServerAddress = serverInfo != null ? serverInfo.ip : null;
    }

    @Override
    protected void init() {
        SimpleSkinSwapperConfig config = SimpleSkinSwapperConfig.get();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Title
        this.addRenderableWidget(new MultiLineTextWidget(centerX - 150, 14, this.title, this.font)
                .setMaxWidth(300)
                .setCentered(true));

        if (currentServerAddress != null) {
            // Label: "Command for: example.com"
            this.addRenderableWidget(new MultiLineTextWidget(
                    centerX - 150, startY,
                    Component.translatable("simpleskinswapper.config.server_command.for", currentServerAddress),
                    this.font)
                    .setMaxWidth(300)
                    .setCentered(true));

            // Command input field
            this.serverCommandField = new EditBox(
                    this.font, centerX - 150, startY + 20, 300, 20,
                    Component.translatable("simpleskinswapper.config.server_command"));
            String existing = config.getCommandForServer(currentServerAddress);
            this.serverCommandField.setValue(existing != null ? existing : "");
            this.serverCommandField.setMaxLength(256);
            this.addRenderableWidget(this.serverCommandField);
        } else {
            // Not connected — explain the feature
            MutableComponent notConnectedText = Component.translatable("simpleskinswapper.config.server_command.not_connected.line1")
                    .append("\n")
                    .append(Component.translatable("simpleskinswapper.config.server_command.not_connected.line2"))
                    .append("\n")
                    .append(Component.translatable("simpleskinswapper.config.server_command.not_connected.line3"))
                    .append("\n")
                    .append(Component.translatable("simpleskinswapper.config.server_command.not_connected.line4"));
            this.addRenderableWidget(new MultiLineTextWidget(centerX - 150, startY, notConnectedText, this.font)
                    .setMaxWidth(300)
                    .setCentered(true));
        }

        // Save button
        this.addRenderableWidget(Button.builder(
                Component.translatable("simpleskinswapper.config.save"),
                button -> {
                    if (currentServerAddress != null) save();
                    this.minecraft.gui.setScreen(parent);
                })
                .bounds(centerX - 100, this.height - 30, 95, 20)
                .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_CANCEL,
                button -> this.minecraft.gui.setScreen(parent))
                .bounds(centerX + 5, this.height - 30, 95, 20)
                .build());
    }

    private void save() {
        SimpleSkinSwapperConfig config = SimpleSkinSwapperConfig.get();
        config.serverCommands.put(currentServerAddress, this.serverCommandField.getValue());
        SimpleSkinSwapperConfig.save();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
