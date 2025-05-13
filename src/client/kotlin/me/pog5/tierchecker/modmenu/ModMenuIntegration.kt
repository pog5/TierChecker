package me.pog5.tierchecker.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import me.pog5.tierchecker.TierChecker
import me.pog5.tierchecker.TierCheckerBackends
import me.pog5.tierchecker.TierCheckerClient
import me.pog5.tierchecker.config.TierCheckerConfig
import me.pog5.tierchecker.config.TierCheckerConfigUtil
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parentScreen: Screen ->
            YetAnotherConfigLib.createBuilder()
                .title(Component.literal("TierChecker Configuration"))
                .category { ConfigCategory.createBuilder()
                    .name(Component.literal("TierChecker"))
                    .tooltip(Component.literal("Settings for TierChecker"))
                    .group { OptionGroup.createBuilder()
                        .name(Component.literal("General"))
                        .description(OptionDescription.of(Component.literal("General TierChecker settings")))
                            .option { Option.createBuilder<TierCheckerBackends>()
                            .name(Component.literal("Default Backend"))
                            .description(OptionDescription.of(Component.literal("The default backend to use when /mctier <name> is invoked instead of /mctier <backend> <name>")))
                            .binding(TierCheckerConfig().defaultBackend,
                                {
                                    TierCheckerClient.CONFIG.defaultBackend
                                },
                                {
                                    TierCheckerClient.CONFIG.defaultBackend = it
                                }
                            )
                            .controller { EnumControllerBuilder.create(it)
                                .enumClass(TierCheckerBackends::class.java)
                            }
                            .build()
                        }
                        .build()
                    }
                    .build()
                }
                .save { TierCheckerConfigUtil.save(TierCheckerClient.CONFIG) }
                .build()
                .generateScreen(parentScreen)
        }
    }
}
