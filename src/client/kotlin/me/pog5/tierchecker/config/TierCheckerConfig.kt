package me.pog5.tierchecker.config

import me.pog5.tierchecker.TierCheckerBackends

data class TierCheckerConfig(
    var defaultBackend: TierCheckerBackends = TierCheckerBackends.MCTIERS_COM,
)