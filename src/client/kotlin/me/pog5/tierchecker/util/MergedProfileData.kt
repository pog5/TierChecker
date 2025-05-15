package me.pog5.tierchecker.util

import me.pog5.tierchecker.TierCheckerBackends
import me.pog5.tierchecker.mctiersio.MCTIOBadge
import me.pog5.tierchecker.mctiersio.MCTIOPvpClass
import me.pog5.tierchecker.mctiersio.MCTIORanking
import me.pog5.tierchecker.mctiersio.MCTIORegion


data class MergedProfileData(
    val name: String = "Unknown",
    var region: MCTIORegion = MCTIORegion.UNKNOWN,
    var overall: Int = -1,
    var points: Int = -1,
    var badges: List<MCTIOBadge> = emptyList(),
    var subhuman: Boolean = false,
    val rankings: MutableMap<MCTIOPvpClass, MCTIORanking?> = mutableMapOf(),
    // Keep track of which backend provided which data
    val dataSource: MutableMap<String, TierCheckerBackends> = mutableMapOf()
)