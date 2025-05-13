package me.pog5.tierchecker.config

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import me.pog5.tierchecker.TierCheckerBackends
import me.pog5.tierchecker.TierCheckerClient
import net.fabricmc.loader.api.FabricLoader
import java.io.FileReader
import java.io.IOException
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class TierCheckerConfig(
    var defaultBackend: TierCheckerBackends = TierCheckerBackends.MCTIERS_IO,
)