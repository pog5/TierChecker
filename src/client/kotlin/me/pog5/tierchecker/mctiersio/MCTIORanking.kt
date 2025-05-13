package me.pog5.tierchecker.mctiersio

data class MCTIORanking(
    val tier: Int, // 1-5
    val pos: Int, // 0-1 (0 - HIGH, 1 - LOW)
    val peak_tier: Int, // same as @tier
    val peak_pos: Int, // same as @pos
    val attained: Long, // unix timestamp
    val retired: Boolean,
)
