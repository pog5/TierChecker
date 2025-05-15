package me.pog5.tierchecker.ffatl

import com.google.gson.annotations.SerializedName
import me.pog5.tierchecker.mctiersio.MCTIORegion

data class FFATLProfile(
    @SerializedName("ign")
    val name: String,
    @SerializedName("country")
    val region: MCTIORegion = MCTIORegion.UNKNOWN,
    @SerializedName("rank")
    val realRanks: List<FFATLRankEntry> = emptyList(),
    @Transient
    private val _rankings: FFATLRankList? = null,
    val subhuman: Boolean = false,
) {
    val rankings: FFATLRankList
        get() = _rankings ?: FFATLRankList(realRanks)
}

