package net.hareworks.screen

import net.hareworks.StreamMCClient
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StreamConfigScreen : Screen(Component.literal("StreamMC Config")) {
    private lateinit var apiKeyField: EditBox
    private lateinit var videoIdField: EditBox

    override fun init() {
        super.init()

        val centerX = width / 2
        
        // API Key Field
        apiKeyField = EditBox(font, centerX - 100, 50, 200, 20, Component.literal("API Key"))
        apiKeyField.setMaxLength(100)
        apiKeyField.value = StreamMCClient.apiKey ?: ""
        apiKeyField.setHint(Component.literal("Enter API Key"))
        addRenderableWidget(apiKeyField)

        // Video ID Field
        videoIdField = EditBox(font, centerX - 100, 90, 200, 20, Component.literal("Video ID"))
        videoIdField.setMaxLength(20)
        videoIdField.value = StreamMCClient.videoId ?: ""
        videoIdField.setHint(Component.literal("Enter Video ID"))
        addRenderableWidget(videoIdField)

        // Start Button
        addRenderableWidget(Button.builder(Component.literal("Start Polling")) {
            startPolling()
        }.bounds(centerX - 105, 130, 100, 20).build())

        // Stop Button
        addRenderableWidget(Button.builder(Component.literal("Stop Polling")) {
            stopPolling()
        }.bounds(centerX + 5, 130, 100, 20).build())
    }

    private fun startPolling() {
        val apiKey = apiKeyField.value
        val videoId = videoIdField.value

        if (apiKey.isBlank() || videoId.isBlank()) {
            return
        }

        StreamMCClient.startPolling(apiKey, videoId)
        
        onClose()
    }

    private fun stopPolling() {
        StreamMCClient.stopPolling()
        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Render a semi-transparent black background manually to avoid "Can only blur once per frame" crash
        guiGraphics.fill(0, 0, width, height, -1072689136)
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
        
        // Labels
        guiGraphics.drawString(font, Component.literal("API Key"), width / 2 - 100, 40, 0xA0A0A0)
        guiGraphics.drawString(font, Component.literal("Video ID"), width / 2 - 100, 80, 0xA0A0A0)
    }
}
