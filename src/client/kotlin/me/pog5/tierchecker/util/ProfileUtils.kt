package me.pog5.tierchecker.util

import me.pog5.tierchecker.TierCheckerBackends
import me.pog5.tierchecker.ffatl.FFATLAPI
import me.pog5.tierchecker.ffatl.FFATLProfile
import me.pog5.tierchecker.mctierscom.MCTCOMAPI
import me.pog5.tierchecker.mctiersio.MCTIOAPI
import me.pog5.tierchecker.mctiersio.MCTIOProfile
import me.pog5.tierchecker.mctiersio.MCTIOPvpClass
import me.pog5.tierchecker.mctiersio.MCTIORegion
import java.util.*

object ProfileUtils {
    fun createMergedProfile(name: String, uuid: UUID, backendOrder: List<TierCheckerBackends>): MergedProfileData? {
        val mergedProfile = MergedProfileData(name)
        var foundAnyData = false

        // Try each backend in order
        for (backend in backendOrder) {
            val profile = when (backend) {
                TierCheckerBackends.MCTIERS_IO -> MCTIOAPI.tierCache[uuid]
                TierCheckerBackends.MCTIERS_COM -> MCTCOMAPI.tierCache[uuid]
                TierCheckerBackends.FFATIERLIST_COM -> FFATLAPI.tierCache[name]
            }

            if (profile != null) {
                foundAnyData = true

                if (mergedProfile.overall == -1) {
                    when (backend) {
                        TierCheckerBackends.MCTIERS_IO, TierCheckerBackends.MCTIERS_COM -> {
                            (profile as? MCTIOProfile)?.overall?.let {
                                mergedProfile.overall = it
                                mergedProfile.dataSource["overall"] = backend
                            }
                        }

                        else -> {}
                    }
                }

                if (mergedProfile.region == MCTIORegion.UNKNOWN) {
                    (profile as? MCTIOProfile)?.region?.let {
                        mergedProfile.region = it
                        mergedProfile.dataSource["region"] = backend
                    }
                }


                if (mergedProfile.points == -1) {
                    when (backend) {
                        TierCheckerBackends.MCTIERS_IO, TierCheckerBackends.MCTIERS_COM -> {
                            (profile as? MCTIOProfile)?.points?.let {
                                mergedProfile.points = it
                                mergedProfile.dataSource["points"] = backend
                            }
                        }

                        else -> {}
                    }
                }

                // Merge badges
                when (backend) {
                    TierCheckerBackends.MCTIERS_IO, TierCheckerBackends.MCTIERS_COM -> {
                        (profile as? MCTIOProfile)?.badges?.let {
                            if (mergedProfile.badges.isEmpty()) {
                                mergedProfile.badges = it
                                mergedProfile.dataSource["badges"] = backend
                            }
                        }
                    }

                    else -> {}
                }

                // Check for blacklisted/subhuman
                when (backend) {
                    TierCheckerBackends.FFATIERLIST_COM -> {
                        (profile as? FFATLProfile)?.subhuman?.let {
                            mergedProfile.subhuman = it
                            mergedProfile.dataSource["subhuman"] = backend
                        }
                    }

                    else -> {}
                }

                // Merge rankings for all PVP classes
                for (pvpClass in MCTIOPvpClass.entries) {
                    // Get current and new ranking
                    val currentRanking = mergedProfile.rankings[pvpClass]
                    val newRanking = profile
                        .let { it as? MCTIOProfile }
                        ?.rankings?.get(pvpClass)
                        ?: (profile as? FFATLProfile)?.rankings?.get(pvpClass)

                    // Update if new ranking exists and either:
                    // - current ranking doesn't exist, or
                    // - new ranking has a newer attained date
                    if (newRanking != null && (currentRanking == null ||
                                (newRanking.attained > currentRanking.attained))
                    ) {
                        mergedProfile.rankings[pvpClass] = newRanking
                        mergedProfile.dataSource["rank_${pvpClass.name}"] = backend
                    }
                }
            }
        }

        return if (foundAnyData) mergedProfile else null
    }
}