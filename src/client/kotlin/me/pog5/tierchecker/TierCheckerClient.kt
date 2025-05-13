package me.pog5.tierchecker

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.pog5.tierchecker.config.TierCheckerConfig
import me.pog5.tierchecker.config.TierCheckerConfigUtil
import me.pog5.tierchecker.mctierscom.MCTCOMAPI
import me.pog5.tierchecker.mctiersio.MCTIOBadge
import me.pog5.tierchecker.mctiersio.MCTIOPvpClass
import me.pog5.tierchecker.mctiersio.MCTIORanking
import me.pog5.tierchecker.mctiersio.MCTIOAPI
import me.pog5.tierchecker.playerdb.PlayerAPI
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Date

object TierCheckerClient : ClientModInitializer {
    val LOGGER = LoggerFactory.getLogger("tierchecker")
    var CONFIG = TierCheckerConfig()

    override fun onInitializeClient() {
        CONFIG = TierCheckerConfigUtil.load()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            dispatcher.register(
                ClientCommandManager.literal("mctier")
                    .then(ClientCommandManager.argument("player", StringArgumentType.string())
                    .executes { context ->
                        handleMCTierCmd(CONFIG.defaultBackend, context)
                    })
                    .then(ClientCommandManager.argument("backend", StringArgumentType.string())
                        .then(ClientCommandManager.argument("player", StringArgumentType.string())
                            .executes { context ->
                                val backend= StringArgumentType.getString(context, "backend")
                                when (backend) {
                                    "io" -> handleMCTierCmd(TierCheckerBackends.MCTIERS_IO, context)
                                    "com" -> handleMCTierCmd(TierCheckerBackends.MCTIERS_COM, context)
                                    else -> {
                                        val invalidBackend = Component.literal("Invalid backend! (Either `com` or `io`)").withStyle(ChatFormatting.RED)
                                        context.source.sendError(invalidBackend)
                                        return@executes 0
                                    }
                                }
                            }
            )))
        }
    }

    fun handleMCTierCmd(backend: TierCheckerBackends, context: CommandContext<FabricClientCommandSource>): Int {
        Thread {
            val name = try {
                StringArgumentType.getString(context, "player")
            } catch (e: CommandSyntaxException) {
                val noPlayer = Component.literal("No player name provided!").withStyle(ChatFormatting.RED)
                context.source.sendError(noPlayer)
                return@Thread
            }

            val findingPlayer = Component.literal("Finding player $name on ${backend.displayName}...").withStyle(ChatFormatting.GRAY)
                .withStyle(ChatFormatting.ITALIC)
            context.source.sendFeedback(findingPlayer)
            val uuid = PlayerAPI.playerNameToUUIDCache[name]
            if (uuid == null) {
                val noPlayer = Component.literal("Player not found!").withStyle(ChatFormatting.RED)
                context.source.sendError(noPlayer)
                return@Thread
            }
            val findingProfile =
                Component.literal("Finding ${name}'s MCTiers.io profile...").withStyle(ChatFormatting.GRAY)
                    .withStyle(ChatFormatting.ITALIC)
//                            context.source.sendFeedback(findingProfile)
            val profile = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]
            }
            if (profile == null) {
                val noProfile =
                    Component.literal("MCTiers.io profile not found!").withStyle(ChatFormatting.RED)
                context.source.sendError(noProfile)
                return@Thread
            }

            val found = Component.literal("Found MCTiers.io profile!").withStyle(ChatFormatting.GREEN)
//                            context.source.sendFeedback(found)

            val opener = Component.literal(" ┏  TierChecker: ${backend.displayName}                    ┓").withStyle(ChatFormatting.RED)
            val mctName = Component.literal("    Name: ").append(Component.literal(profile.name).withStyle(ChatFormatting.YELLOW))
            val mctRegion = Component.literal("    Region: ").append(Component.literal(profile.region.displayName).withStyle(ChatFormatting.GREEN))
            val mctRank = Component.literal("    Rank: ").append(Component.literal("#${profile.overall} ").withStyle(ChatFormatting.GOLD)).append(Component.literal("(").append(ptsToRank(profile.points)).append(" - ${profile.points}pts)").withStyle(ChatFormatting.ITALIC))
            val closer = Component.literal(" ┗                                                   ┛").withStyle(ChatFormatting.RED)

            val unknownRank = Component.literal("[N/A]").withStyle(ChatFormatting.GRAY)
            val crystalRank = if (profile.rankings[MCTIOPvpClass.CRYSTAL] != null) {
                styledRank("Crystal", profile.rankings[MCTIOPvpClass.CRYSTAL]!!)
            } else {
                unknownRank
            }
            val mctCrystal = Component.literal("    Crystal: ").append(crystalRank)

            val swordRank = if (profile.rankings[MCTIOPvpClass.SWORD] != null) {
                styledRank("Sword", profile.rankings[MCTIOPvpClass.SWORD]!!)
            } else {
                unknownRank
            }
            val mctSword = Component.literal("    Sword: ").append(swordRank)

            val uhcRank = if (profile.rankings[MCTIOPvpClass.UHC] != null) {
                styledRank("UHC", profile.rankings[MCTIOPvpClass.UHC]!!)
            } else {
                unknownRank
            }
            val mctUHC = Component.literal("    UHC: ").append(uhcRank)

            val potRank = if (profile.rankings[MCTIOPvpClass.POT] != null) {
                styledRank("Pot", profile.rankings[MCTIOPvpClass.POT]!!)
            } else {
                unknownRank
            }
            val mctPot = Component.literal("    Pot: ").append(potRank)

            val nethPotRank = if (profile.rankings[MCTIOPvpClass.NETH_POT] != null) {
                styledRank("NethPot", profile.rankings[MCTIOPvpClass.NETH_POT]!!)
            } else {
                unknownRank
            }
            val mctNethPot = Component.literal("    NethPot: ").append(nethPotRank)

            val smpRank = if (profile.rankings[MCTIOPvpClass.SMP] != null) {
                styledRank("SMP", profile.rankings[MCTIOPvpClass.SMP]!!)
            } else {
                unknownRank
            }
            val mctSMP = Component.literal("    SMP: ").append(smpRank)

            val axeRank = if (profile.rankings[MCTIOPvpClass.AXE] != null) {
                styledRank("Axe", profile.rankings[MCTIOPvpClass.AXE]!!)
            } else {
                unknownRank
            }
            val mctAxe = Component.literal("    Axe: ").append(axeRank)

            val elytraRank = if (profile.rankings[MCTIOPvpClass.ELYTRA] != null) {
                styledRank("Elytra", profile.rankings[MCTIOPvpClass.ELYTRA]!!)
            } else {
                unknownRank
            }
            val mctElytra = Component.literal("    Elytra: ").append(elytraRank)

            val vanillaRank = if (profile.rankings[MCTIOPvpClass.VANILLA] != null) {
                styledRank("Vanilla", profile.rankings[MCTIOPvpClass.VANILLA]!!)
            } else {
                unknownRank
            }
            val mctVanilla = Component.literal("    Vanilla: ").append(vanillaRank)

            val maceRank = if (profile.rankings[MCTIOPvpClass.MACE] != null) {
                styledRank("Mace", profile.rankings[MCTIOPvpClass.MACE]!!)
            } else {
                unknownRank
            }
            val mctMace = Component.literal("    Mace: ").append(maceRank)

            val nethOPRank = if (profile.rankings[MCTIOPvpClass.NETH_OP] != null) {
                styledRank("NethOP", profile.rankings[MCTIOPvpClass.NETH_OP]!!)
            } else {
                unknownRank
            }
            val mctNethOP = Component.literal("    NethOP: ").append(nethOPRank)

            val badges = styledBadges(profile.badges)

            context.source.sendFeedback(opener)
            context.source.sendFeedback(mctName)
            context.source.sendFeedback(mctRegion)
            context.source.sendFeedback(mctRank)
            context.source.sendError(Component.empty())

            context.source.sendFeedback(mctCrystal.append("     ").append(mctSword))
            context.source.sendFeedback(mctUHC.append("          ").append(mctPot))
            context.source.sendFeedback(mctNethPot.append("     ").append(mctSMP))
            context.source.sendFeedback(mctAxe.append("          ").append(mctElytra))
            context.source.sendFeedback(mctNethOP.append("      ").append(mctVanilla))
            context.source.sendFeedback(mctMace.append("             ").append(badges))

            context.source.sendFeedback(closer)
            1
        }.start()
        return 1
    }

    fun styledRank(type: String?, rank: MCTIORanking): Component {
        val tierColors = listOf(
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.AQUA,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.RED
        )

        val tier = rank.tier
        val pos = if (rank.pos == 0) {
            "H"
        } else {
            "L"
        }

        val peakTier = rank.peak_tier
        val peakPos = if (rank.peak_pos == 0) {
            "H"
        } else {
            "L"
        }

        val attained = rank.attained
        val attainedString = if (attained == 0L) {
            "N/A"
        } else {
            val date = Date(attained * 1000)
            val formatter = SimpleDateFormat("yyyy-MM-dd")
            formatter.format(date)
        }

        val hover = Component.literal("${type ?: "Tier"}: ")
            .append(Component.literal("${pos}T$tier").withStyle(tierColors[tier-1]))
            .append(Component.literal("\n"))
            .append(Component.literal(" | Peak Tier: "))
            .append(Component.literal("${pos}T$peakTier").withStyle(tierColors[tier-1]))
            .append(Component.literal("\n"))
            .append(Component.literal(" | Attained: "))
            .append(Component.literal(attainedString).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("\n"))
            .append(Component.literal(" | Retired: "))
            .append(if (rank.retired)
                Component.literal("Yes").withStyle(ChatFormatting.RED)
            else Component.literal("No").withStyle(ChatFormatting.GREEN))

        val hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)

        val retired = if (rank.retired) {
            Style.EMPTY.withColor(ChatFormatting.STRIKETHROUGH)
        } else {
            Style.EMPTY
        }
        val sent = Component.literal("[${pos}T${tier}]").withStyle(retired)
            .withStyle(Style.EMPTY.withColor(tierColors[tier-1])
                .withHoverEvent(hoverEvent))
        return sent
    }

    fun styledBadges(badges: List<MCTIOBadge>): Component {
        if (badges.isEmpty()) {
            return Component.literal("[Badges (0)]").withStyle(ChatFormatting.GRAY)
        }

        val sortedBadges = badges.toSortedSet { a, b -> a.title.compareTo(b.title) }

        val hover = Component.literal("Badges: ")
        sortedBadges.forEach { badge ->
            hover.append(Component.literal("\n"))
                .append(Component.literal(badge.title).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - "))
                .append(Component.literal(badge.desc).withStyle(ChatFormatting.GRAY))
        }

        val hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)

        val badgeMsg = Component.literal("[Badges (${sortedBadges.size})]").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withHoverEvent(hoverEvent))
        return badgeMsg
    }

    fun ptsToRank(points: Int): Component {
        return when (points) {
            in 0..9 -> Component.literal("Rookie").withStyle(ChatFormatting.GRAY)
            in 10..29 -> Component.literal("Combat Novice").withStyle(ChatFormatting.LIGHT_PURPLE)
            in 30..49 -> Component.literal("Combat Cadet").withStyle(ChatFormatting.AQUA)
            in 50..99 -> Component.literal("Combat Specialist").withStyle(ChatFormatting.YELLOW)
            in 100..249 -> Component.literal("Combat Ace").withStyle(ChatFormatting.RED)
            in 250..500 -> Component.literal("Combat Master").withStyle(ChatFormatting.GOLD)
            else -> Component.literal("Unknown").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC)
        }
    }
}
