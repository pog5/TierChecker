package me.pog5.tierchecker.ffatl

import me.pog5.tierchecker.mctiersio.MCTIOPvpClass
import me.pog5.tierchecker.mctiersio.MCTIORanking

class FFATLRankList(
    var rankings: List<FFATLRankEntry> = listOf(), override val size: Int = 2
) : AbstractList<FFATLRankEntry>() {
    val activeSwordRank: FFATLRankEntry? get() = rankings.getOrNull(0)
    val activeSpeedRank: FFATLRankEntry? get() = rankings.getOrNull(1)
    val retiredSwordRank: FFATLRankEntry? get() = rankings.getOrNull(2)
    val retiredSpeedRank: FFATLRankEntry? get() = rankings.getOrNull(3)

    operator fun get(mode: MCTIOPvpClass): MCTIORanking? {
        return when (mode) {
            MCTIOPvpClass.SWORD -> {
                if (retiredSwordRank != null && retiredSwordRank?.rank != null) {
                    val highOrLow = when (retiredSwordRank!!.rank[0] == 'H') {
                        true -> 0
                        false -> 1
                    }
                    val tier = retiredSwordRank!!.rank[2].digitToInt()
                    MCTIORanking(tier, highOrLow, tier, highOrLow, 0, true)
                } else if (activeSwordRank != null && activeSwordRank?.rank != null) {
                    val highOrLow = when (activeSwordRank?.rank?.get(0) == 'H') {
                        true -> 0
                        false -> 1
                    }
                    val tier = activeSwordRank?.rank?.get(2)?.digitToInt() ?: 0
                    MCTIORanking(tier, highOrLow, tier, highOrLow, 0, false)
                } else {
                    null
                }
            }

            MCTIOPvpClass.SPEED -> {
                if (retiredSpeedRank != null && retiredSpeedRank?.rank != null) {
                    val highOrLow = when (retiredSpeedRank!!.rank[0] == 'H') {
                        true -> 0
                        false -> 1
                    }
                    val tier = retiredSpeedRank!!.rank[2].digitToInt()
                    MCTIORanking(tier, highOrLow, tier, highOrLow, 0, true)
                } else if (activeSpeedRank != null && activeSpeedRank?.rank != null) {
                    val highOrLow = when (activeSpeedRank?.rank?.get(0) == 'H') {
                        true -> 0
                        false -> 1
                    }
                    val tier = activeSpeedRank?.rank?.get(2)?.digitToInt() ?: 0
                    MCTIORanking(tier, highOrLow, tier, highOrLow, 0, false)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    override fun get(index: Int): FFATLRankEntry {
        return rankings[index]
    }
}