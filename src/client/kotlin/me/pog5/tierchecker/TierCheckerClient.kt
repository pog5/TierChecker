package me.pog5.tierchecker

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import me.pog5.tierchecker.config.TierCheckerConfig
import me.pog5.tierchecker.config.TierCheckerConfigUtil
import me.pog5.tierchecker.ffatl.FFATLAPI
import me.pog5.tierchecker.mctierscom.MCTCOMAPI
import me.pog5.tierchecker.mctiersio.MCTIOAPI
import me.pog5.tierchecker.mctiersio.MCTIOBadge
import me.pog5.tierchecker.mctiersio.MCTIOPvpClass
import me.pog5.tierchecker.mctiersio.MCTIORanking
import me.pog5.tierchecker.playerdb.PlayerAPI
import me.pog5.tierchecker.util.MergedProfileData
import me.pog5.tierchecker.util.ProfileUtils
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

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
                        handleMCTierCmd(context)
                    })
                    .then(ClientCommandManager.argument("backend", StringArgumentType.string())
                        .then(ClientCommandManager.argument("player", StringArgumentType.string())
                            .executes { context ->
                                val backend= StringArgumentType.getString(context, "backend")
                                when (backend) {
                                    "io" -> handleMCTierCmd(TierCheckerBackends.MCTIERS_IO, context)
                                    "com" -> handleMCTierCmd(TierCheckerBackends.MCTIERS_COM, context)
                                    "ffa" -> handleMCTierCmd(TierCheckerBackends.FFATIERLIST_COM, context)
                                    else -> {
                                        val invalidBackend =
                                            Component.literal("Invalid backend! (Either `com`, `io` or `ffa`)")
                                                .withStyle(ChatFormatting.RED)
                                        context.source.sendError(invalidBackend)
                                        return@executes 0
                                    }
                                }
                            }
            )))
        }
    }

    fun handleMCTierCmd(context: CommandContext<FabricClientCommandSource>): Int {
        Thread {
            val name = try {
                StringArgumentType.getString(context, "player")
            } catch (e: CommandSyntaxException) {
                val noPlayer = Component.literal("No player name provided!").withStyle(ChatFormatting.RED)
                context.source.sendError(noPlayer)
                return@Thread
            }

            val findingPlayer =
                Component.literal("Finding player $name (using all backends)...").withStyle(ChatFormatting.GRAY)
                    .withStyle(ChatFormatting.ITALIC)
            context.source.sendFeedback(findingPlayer)

            val uuid = PlayerAPI.playerNameToUUIDCache[name]
            if (uuid == null) {
                val noPlayer = Component.literal("Player not found!").withStyle(ChatFormatting.RED)
                context.source.sendError(noPlayer)
                return@Thread
            }

            // Define backend order - start with default, then try others
            val backendOrder = listOf(
                CONFIG.defaultBackend,
                *TierCheckerBackends.values().filter { it != CONFIG.defaultBackend }.toTypedArray()
            )

            // Create merged profile data
            val mergedProfile = ProfileUtils.createMergedProfile(name, uuid, backendOrder)

            if (mergedProfile == null) {
                val noProfile = Component.literal("No profile found on any backend!").withStyle(ChatFormatting.RED)
                context.source.sendError(noProfile)
                return@Thread
            }

            // Display the merged profile data
            displayProfileData(context, mergedProfile, backendOrder)
        }.start()
        return 1
    }

    fun displayProfileData(
        context: CommandContext<FabricClientCommandSource>,
        profile: MergedProfileData,
        backendOrder: List<TierCheckerBackends>
    ) {
        // Create data sources tooltip
        val sourcesInfo = Component.literal("Data Sources:\n")
            .withStyle(ChatFormatting.GRAY)

        profile.dataSource.forEach { (field, backend) ->
            sourcesInfo.append(
                Component.literal(" - $field: ${backend.displayName}\n")
                    .withStyle(ChatFormatting.GRAY)
            )
        }

        // Opener with hover event showing data sources
        val opener = Component.literal(" ┏  TierChecker: Combined Data               ┓")
            .withStyle(ChatFormatting.RED)
            .withStyle(Style.EMPTY.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, sourcesInfo)))

        // Name and blacklisted status
        val blacklisted = if (profile.subhuman) {
            val sourceComponent =
                Component.literal(" | Source: ").append("\n").append(profile.dataSource["subhuman"]!!.displayName)
            val hoverEffect = HoverEvent(HoverEvent.Action.SHOW_TEXT, sourceComponent)
            Component.literal("[⚠ BLACKLISTED]").withStyle(ChatFormatting.DARK_RED)
                .withStyle(Style.EMPTY.withHoverEvent(hoverEffect))
        } else {
            Component.empty()
        }
        val mctName =
            Component.literal("    Name: ").append(Component.literal(profile.name).withStyle(ChatFormatting.YELLOW))

        // Region and overall rank
        val regionSourceComponent = Component.literal("Source: ")
            .append(Component.literal(profile.dataSource["region"]!!.displayName).withStyle(ChatFormatting.GRAY))
        val regionHoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, regionSourceComponent)
        val mctRegion = Component.literal("    Region: ")
            .append(Component.literal(profile.region.displayName).withStyle(ChatFormatting.GREEN))
            .withStyle(Style.EMPTY.withHoverEvent(regionHoverEvent))
        val mctRank = Component.literal("    Rank: ")
            .append(Component.literal("#${profile.overall} ").withStyle(ChatFormatting.GOLD))
            .append(
                Component.literal("(")
                    .append(ptsToRank(profile.points))
                    .append(" - ${if (profile.points == -1) "?" else profile.points}pts)")
                    .withStyle(ChatFormatting.ITALIC)
            )
            .withStyle(Style.EMPTY.withHoverEvent(regionHoverEvent))

        // Badges
        val badges = styledBadges(profile.badges)

        fun createRankComponent(profile: MergedProfileData, pvpClass: MCTIOPvpClass, label: String): MutableComponent {
            val trueKey = "rank_" + pvpClass.name
            val unknownRank = Component.literal("[N/A]").withStyle(ChatFormatting.GRAY)

            val sourceComponent = profile.dataSource[trueKey]?.let {
                Component.literal(" | Source: ")
                    .append(Component.literal(it.displayName).withStyle(ChatFormatting.GRAY))
            }
            val extraInfo = sourceComponent?.let { listOf(it) } ?: emptyList()
            val rank = profile.rankings[pvpClass]?.let { styledRank(label, it, extraInfo) } ?: unknownRank
            return Component.literal("    $label: ").append(rank)
        }

        val mctCrystal = createRankComponent(profile, MCTIOPvpClass.CRYSTAL, "Crystal")
        val mctSword = createRankComponent(profile, MCTIOPvpClass.SWORD, "Sword")
        val mctUHC = createRankComponent(profile, MCTIOPvpClass.UHC, "UHC")
        val mctPot = createRankComponent(profile, MCTIOPvpClass.POT, "Pot")
        val mctNethPot = createRankComponent(profile, MCTIOPvpClass.NETH_POT, "NethPot")
        val mctSMP = createRankComponent(profile, MCTIOPvpClass.SMP, "SMP")
        val mctAxe = createRankComponent(profile, MCTIOPvpClass.AXE, "Axe")
        val mctElytra = createRankComponent(profile, MCTIOPvpClass.ELYTRA, "Elytra")
        val mctNethOP = createRankComponent(profile, MCTIOPvpClass.NETH_OP, "NethOP")
        val mctVanilla = createRankComponent(profile, MCTIOPvpClass.VANILLA, "Vanilla")
        val mctSpeed = createRankComponent(profile, MCTIOPvpClass.SPEED, "Speed")
        val mctMace = createRankComponent(profile, MCTIOPvpClass.MACE, "Mace")

        // Closer
        val closer =
            Component.literal(" ┗                                                   ┛").withStyle(ChatFormatting.RED)

        // Send all components to chat
        context.source.sendFeedback(opener)
        context.source.sendFeedback(mctName.append("  ").append(blacklisted))
        context.source.sendFeedback(mctRegion)
        context.source.sendFeedback(mctRank)
        context.source.sendFeedback(Component.literal("                   ").append(badges))

        context.source.sendFeedback(mctCrystal.append("     ").append(mctSword))
        context.source.sendFeedback(mctUHC.append("          ").append(mctPot))
        context.source.sendFeedback(mctNethPot.append("     ").append(mctSMP))
        context.source.sendFeedback(mctAxe.append("          ").append(mctElytra))
        context.source.sendFeedback(mctNethOP.append("      ").append(mctVanilla))
        context.source.sendFeedback(mctSpeed.append("       ").append(mctMace))

        context.source.sendFeedback(closer)
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
            Component.literal("Finding ${name}'s ${backend.displayName} profile...").withStyle(ChatFormatting.GRAY)
                .withStyle(ChatFormatting.ITALIC)
//                            context.source.sendFeedback(findingProfile)
            val profile = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]
                TierCheckerBackends.FFATIERLIST_COM -> {
                    println(FFATLAPI.toJson(FFATLAPI.tierCache[name]))
                    FFATLAPI.tierCache[name]
                }
            }
            if (profile == null) {
                val noProfile =
                    Component.literal("${backend.displayName} profile not found!").withStyle(ChatFormatting.RED)
                context.source.sendError(noProfile)
                return@Thread
            }

            Component.literal("Found ${backend.displayName} profile!").withStyle(ChatFormatting.GREEN)
//                            context.source.sendFeedback(found)

            val apiName = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.name
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.name
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.name
            } ?: name
            val apiRegion = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.region?.displayName
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.region?.displayName
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.region?.displayName
            } ?: "Unknown"
            val apiRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.overall
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.overall
                TierCheckerBackends.FFATIERLIST_COM -> "N/A"
            } ?: "Unknown"
            val apiPoints = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.points
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.points
                TierCheckerBackends.FFATIERLIST_COM -> -1
            } ?: -1

            val openerSpaced = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> " ┏  TierChecker: ${backend.displayName}                    ┓"
                TierCheckerBackends.MCTIERS_COM -> " ┏  TierChecker: ${backend.displayName}                  ┓"
                TierCheckerBackends.FFATIERLIST_COM -> " ┏  TierChecker: ${backend.displayName}              ┓"
            }
            val opener = Component.literal(openerSpaced).withStyle(ChatFormatting.RED)
            val mctName =
                Component.literal("    Name: ").append(Component.literal(apiName).withStyle(ChatFormatting.YELLOW))
            val mctRegion =
                Component.literal("    Region: ").append(Component.literal(apiRegion).withStyle(ChatFormatting.GREEN))
            val mctRank =
                Component.literal("    Rank: ").append(Component.literal("#${apiRank} ").withStyle(ChatFormatting.GOLD))
                    .append(
                        Component.literal("(").append(ptsToRank(apiPoints))
                            .append(" - ${if (apiPoints == -1) "?" else apiPoints}pts)")
                            .withStyle(ChatFormatting.ITALIC)
                    )
            val closer = Component.literal(" ┗                                                   ┛").withStyle(ChatFormatting.RED)

            val unknownRank = Component.literal("[N/A]").withStyle(ChatFormatting.GRAY)

            val apiCrystalRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.CRYSTAL]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.CRYSTAL]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.CRYSTAL]
            }
            val crystalRank = if (apiCrystalRank != null) {
                styledRank("Crystal", apiCrystalRank)
            } else {
                unknownRank
            }
            val mctCrystal = Component.literal("    Crystal: ").append(crystalRank)

            val apiSwordRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.SWORD]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.SWORD]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.SWORD]
            }
            val swordRank = if (apiSwordRank != null) {
                styledRank("Sword", apiSwordRank)
            } else {
                unknownRank
            }
            val mctSword = Component.literal("    Sword: ").append(swordRank)

            val apiUHCRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.UHC]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.UHC]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.UHC]
            }
            val uhcRank = if (apiUHCRank != null) {
                styledRank("UHC", apiUHCRank)
            } else {
                unknownRank
            }
            val mctUHC = Component.literal("    UHC: ").append(uhcRank)

            val apiPotRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.POT]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.POT]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.POT]
            }
            val potRank = if (apiPotRank != null) {
                styledRank("Pot", apiPotRank)
            } else {
                unknownRank
            }
            val mctPot = Component.literal("    Pot: ").append(potRank)

            val apiNethPotRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.NETH_POT]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.NETH_POT]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.NETH_POT]
            }
            val nethPotRank = if (apiNethPotRank != null) {
                styledRank("NethPot", apiNethPotRank)
            } else {
                unknownRank
            }
            val mctNethPot = Component.literal("    NethPot: ").append(nethPotRank)

            val apiSMPRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.SMP]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.SMP]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.SMP]
            }
            val smpRank = if (apiSMPRank != null) {
                styledRank("SMP", apiSMPRank)
            } else {
                unknownRank
            }
            val mctSMP = Component.literal("    SMP: ").append(smpRank)

            val apiAxeRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.AXE]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.AXE]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.AXE]
            }
            val axeRank = if (apiAxeRank != null) {
                styledRank("Axe", apiAxeRank)
            } else {
                unknownRank
            }
            val mctAxe = Component.literal("    Axe: ").append(axeRank)

            val apiElytraRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.ELYTRA]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.ELYTRA]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.ELYTRA]
            }
            val elytraRank = if (apiElytraRank != null) {
                styledRank("Elytra", apiElytraRank)
            } else {
                unknownRank
            }
            val mctElytra = Component.literal("    Elytra: ").append(elytraRank)

            val apiVanillaRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.VANILLA]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.VANILLA]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.VANILLA]
            }
            val vanillaRank = if (apiVanillaRank != null) {
                styledRank("Vanilla", apiVanillaRank)
            } else {
                unknownRank
            }
            val mctVanilla = Component.literal("    Vanilla: ").append(vanillaRank)

            val apiMaceRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.MACE]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.MACE]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.MACE]
            }
            val maceRank = if (apiMaceRank != null) {
                styledRank("Mace", apiMaceRank)
            } else {
                unknownRank
            }
            val mctMace = Component.literal("    Mace: ").append(maceRank)

            val apiNethOPRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.NETH_OP]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.NETH_OP]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.NETH_OP]
            }
            val nethOPRank = if (apiNethOPRank != null) {
                styledRank("NethOP", apiNethOPRank)
            } else {
                unknownRank
            }
            val mctNethOP = Component.literal("    NethOP: ").append(nethOPRank)

            val apiSpeedRank = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.SPEED]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.rankings[MCTIOPvpClass.SPEED]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.rankings[MCTIOPvpClass.SPEED]
            }
            val speedRank = if (apiSpeedRank != null) {
                styledRank("Speed", apiSpeedRank)
            } else {
                unknownRank
            }
            val mctSpeed = Component.literal("    Speed: ").append(speedRank)

            val apiBadges = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]?.badges
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]?.badges
                TierCheckerBackends.FFATIERLIST_COM -> emptyList()
            } ?: emptyList()

            val badges = styledBadges(apiBadges)

            val apiBlacklisted = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> null
                TierCheckerBackends.MCTIERS_COM -> null
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]?.subhuman
            } ?: false
            val blacklisted = if (apiBlacklisted) {
                Component.literal("[⚠ BLACKLISTED]").withStyle(ChatFormatting.DARK_RED)
            } else {
                Component.empty()
            }

            context.source.sendFeedback(opener)
            context.source.sendFeedback(mctName.append("  ").append(blacklisted))
            context.source.sendFeedback(mctRegion)
            context.source.sendFeedback(mctRank)
            context.source.sendFeedback(Component.literal("                   ").append(badges))

            context.source.sendFeedback(mctCrystal.append("     ").append(mctSword))
            context.source.sendFeedback(mctUHC.append("          ").append(mctPot))
            context.source.sendFeedback(mctNethPot.append("     ").append(mctSMP))
            context.source.sendFeedback(mctAxe.append("          ").append(mctElytra))
            context.source.sendFeedback(mctNethOP.append("      ").append(mctVanilla))
            context.source.sendFeedback(mctSpeed.append("       ").append(mctMace))

            context.source.sendFeedback(closer)
            1
        }.start()
        return 1
    }

    fun styledRank(type: String?, rank: MCTIORanking, extraComponents: List<Component> = emptyList()): Component {
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
        if (rank.peak_pos == 0) {
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

        for (extra in extraComponents) {
            hover.append(Component.literal("\n"))
                .append(extra)
        }

        val hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)

        val sent = Component.literal("[${pos}T${tier}]")
            .withStyle(Style.EMPTY.withColor(tierColors[tier-1])
                .withHoverEvent(hoverEvent).withStrikethrough(rank.retired)
            )
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
