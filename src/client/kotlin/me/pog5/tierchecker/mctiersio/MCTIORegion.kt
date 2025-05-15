package me.pog5.tierchecker.mctiersio

import com.google.gson.annotations.SerializedName

enum class MCTIORegion {
    @SerializedName("Unknown")
    UNKNOWN("Unknown"),

    @SerializedName("NA")
    NA("North America"),

    @SerializedName("EU")
    EU("Europe"),

    @SerializedName("AS")
    AS("Australia"),

    @SerializedName("SA")
    SA("South America"),

    @SerializedName("OC")
    OC("Oceania");

    var displayName: String = name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

    constructor(displayName: String) {
        this.displayName = displayName
    }
}