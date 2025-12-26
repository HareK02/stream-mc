package net.hareworks.config

import net.hareworks.EventType
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class StreamMCConfig(
    val videoId: String? = null,
    val pollingIntervalMs: Long = 5000L,
    val showChatInGame: Boolean = true,
    val formats: Map<String, String> = mapOf(
        "CHAT" to "§e%author%: §f%message%",
        "SUPERCHAT" to "§c[SC] §6%author% §a(%displayAmount%): §f%message%",
        "SUPERSTICKER" to "§d[Sticker] §6%author% §a(%displayAmount%)",
        "NEW_SPONSOR" to "§a[New Member] §e%author% §b(%memberLevel%)",
        "MEMBER_MILESTONE" to "§b[Milestone] §e%author% §d(%memberMonth% mo): §f%message%"
    ),
    val eventMappings: Map<String, String> = emptyMap()
)

object ConfigManager {
    private val yaml = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
    })
    
    private fun getConfigPath(): Path {
        val configDir = Path.of("config")
        if (!configDir.exists()) {
            Files.createDirectories(configDir)
        }
        return configDir.resolve("stream-mc.yaml")
    }
    
    fun loadConfig(): StreamMCConfig {
        val path = getConfigPath()
        if (!path.exists()) return StreamMCConfig()
        
        return try {
            val map = yaml.load<Map<String, Any>>(path.readText()) ?: return StreamMCConfig()
            
            StreamMCConfig(
                videoId = map["videoId"] as? String,
                pollingIntervalMs = (map["pollingIntervalMs"] as? Number)?.toLong() ?: 5000L,
                showChatInGame = map["showChatInGame"] as? Boolean ?: true,
                formats = (map["formats"] as? Map<String, String>) ?: StreamMCConfig().formats,
                eventMappings = (map["eventMappings"] as? Map<String, String>) ?: emptyMap()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            StreamMCConfig()
        }
    }
    
    fun saveConfig(config: StreamMCConfig) {
        val path = getConfigPath()
        val comment = """
            # StreamMC Configuration
            # 
            # videoId: YouTube Video ID (e.g. jNQXAC9IVRw)
            # pollingIntervalMs: Interval in milliseconds to poll chat
            # showChatInGame: Toggle chat display in Minecraft chat
            # 
            # formats: Chat display formats per event type (supports Minecraft color codes with §)
            #   Available payloads per event:
            #   - CHAT: %author%, %message%
            #   - SUPERCHAT: %author%, %message%, %amount%, %currency%, %displayAmount%, %tier%
            #   - SUPERSTICKER: %author%, %amount%, %currency%, %displayAmount%, %tier%, %stickerId%, %altText%
            #   - NEW_SPONSOR: %author%, %isUpgrade%, %memberLevel%
            #   - MEMBER_MILESTONE: %author%, %message%, %memberMonth%, %memberLevel%
            # 
            # eventMappings: Map event types to data pack functions
            
        """.trimIndent()
        
        try {
            val map = mutableMapOf<String, Any>()
            config.videoId?.let { map["videoId"] = it }
            map["pollingIntervalMs"] = config.pollingIntervalMs
            map["showChatInGame"] = config.showChatInGame
            map["formats"] = config.formats
            map["eventMappings"] = config.eventMappings
            
            val yamlOutput = yaml.dump(map)
            path.writeText(comment + "\n" + yamlOutput)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun eventMapToStringMap(eventMap: Map<EventType, String>): Map<String, String> {
        return eventMap.mapKeys { it.key.name }
    }
    
    fun stringMapToEventMap(stringMap: Map<String, String>): Map<EventType, String> {
        return stringMap.mapNotNull { (key, value) ->
            try {
                EventType.valueOf(key) to value
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toMap()
    }
}
