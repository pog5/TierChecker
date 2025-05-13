package me.pog5.mctiersio

enum class MCTIORegion {
    NA("North America"),
    EU("Europe"),
    AS("Australia"),
    SA("South America");

    var displayName: String = name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

    constructor(displayName: String) {
        this.displayName = displayName
    }
}