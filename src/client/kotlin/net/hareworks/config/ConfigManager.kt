package net.hareworks.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.hareworks.EventType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class StreamMCConfig(
    val videoId: String? = null,
    val pollingIntervalMs: Long = 5000L,
    val chatFormat: String = "§e%author%: §f%message%",
    val showChatInGame: Boolean = true,
    val eventMappings: Map<String, String> = emptyMap()
)

object ConfigManager {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private fun getConfigPath(): Path {
        val configDir = Path.of("config")
        if (!configDir.exists()) {
            Files.createDirectories(configDir)
        }
        return configDir.resolve("stream-mc.json")
    }
    
    fun loadConfig(): StreamMCConfig {
        val path = getConfigPath()
        return if (path.exists()) {
            try {
                json.decodeFromString(path.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                StreamMCConfig()
            }
        } else {
            StreamMCConfig()
        }
    }
    
    fun saveConfig(config: StreamMCConfig) {
        val path = getConfigPath()
        try {
            path.writeText(json.encodeToString(config))
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
