package me.pog5.tierchecker

enum class TierCheckerBackends {
    MCTIERS_IO("MCTiers.io"),
    MCTIERS_COM("MCTiers.com"),
    FFATIERLIST_COM("FFATierList.com");

    val displayName: String
    constructor(name: String) {
        this.displayName = name
    }
}