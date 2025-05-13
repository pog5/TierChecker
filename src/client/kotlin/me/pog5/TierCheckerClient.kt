package me.pog5

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.pog5.mctiersio.MCTIOBadge
import me.pog5.mctiersio.MCTIOPvpClass
import me.pog5.mctiersio.MCTIORanking
import me.pog5.mctiersio.MCTIOAPI
import me.pog5.playerdb.PlayerAPI
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import java.util.Date


object TierCheckerClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            dispatcher.register(
                ClientCommandManager.literal("mctier")
                    .then(ClientCommandManager.argument("player", StringArgumentType.string())
                    .executes { context ->
                        Thread {
                            val name = try {
                                StringArgumentType.getString(context, "player")
                            } catch (e: CommandSyntaxException) {
                                val noPlayer = Component.literal("No player name provided!").withStyle(ChatFormatting.RED)
                                context.source.sendError(noPlayer)
                                return@Thread
                            }

                            val findingPlayer = Component.literal("Finding player $name...").withStyle(ChatFormatting.GRAY)
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
                            val profile = MCTIOAPI.tierCache[uuid]
                            if (profile == null) {
                                val noProfile =
                                    Component.literal("MCTiers.io profile not found!").withStyle(ChatFormatting.RED)
                                context.source.sendError(noProfile)
                                return@Thread
                            }

                            val found = Component.literal("Found MCTiers.io profile!").withStyle(ChatFormatting.GREEN)
//                            context.source.sendFeedback(found)

                            val opener = Component.literal(" ┏  TierChecker: MCTiers.io                    ┓").withStyle(ChatFormatting.RED)
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

                            context.source.sendFeedback(closer)
                            1
                        }.start()
                        1
                    })
            )
        }
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
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
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
            .append(if (rank.retired) Component.literal("Yes").withStyle(ChatFormatting.RED) else Component.literal("No").withStyle(ChatFormatting.GREEN))

        val hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)

        val sent = Component.literal("[${pos}T${tier}]").setStyle(Style.EMPTY.withColor(tierColors[tier-1]).withHoverEvent(hoverEvent))
        return sent
    }

    fun styledBadges(badges: List<MCTIOBadge>): Component {
        if (badges.isEmpty()) {
            return Component.literal("[Badges (0)]").withStyle(ChatFormatting.GRAY)
        }

        val hover = Component.literal("Badges: ")
        badges.forEach { badge ->
            hover.append(Component.literal("\n"))
                .append(Component.literal(badge.title).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - "))
                .append(Component.literal(badge.desc).withStyle(ChatFormatting.GRAY))
        }

        val hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)

        val badgeMsg = Component.literal("[Badges (${badges.size})]").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withHoverEvent(hoverEvent))
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