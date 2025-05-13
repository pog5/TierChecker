package me.pog5.tierchecker.config

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object TierCheckerConfigUtil {
    fun getConfigPath(): Path {
        val configDir = FabricLoader.getInstance().configDir
        return configDir.resolve("tierchecker.json")
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    fun save(cfg: TierCheckerConfig = TierCheckerConfig()) {
        val configPath = getConfigPath()
        if (!configPath.parent.toFile().exists()) {
            configPath.parent.toFile().mkdirs()
        }
        try {
            val cfg = gson.toJson(cfg)
            Files.writeString(configPath, cfg, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(): TierCheckerConfig {
        val configPath = getConfigPath()
        if (!configPath.toFile().exists()) {
            return TierCheckerConfig()
        }
        try {
            val reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)
            val cfg = gson.fromJson(reader, TierCheckerConfig::class.java)
            reader.close()
            return cfg
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return TierCheckerConfig()
    }

    fun getDefaultConfig(): String {
        return GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create()
            .toJson(TierCheckerConfig())
    }
}