package me.pog5.tierchecker.mctiersio

data class MCTIOProfile(
    val uuid: String, // UUID.java, without dashes
    val name: String, // mc username
    val rankings: Map<MCTIOPvpClass, MCTIORanking>,
    val region: MCTIORegion,
    val points: Int, // total points - 300 max
    val overall: Int, // overall rank
    val badges: List<MCTIOBadge>,
)
