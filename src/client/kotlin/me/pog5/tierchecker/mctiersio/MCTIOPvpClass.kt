package me.pog5.tierchecker.mctiersio

import com.google.gson.annotations.SerializedName

enum class MCTIOPvpClass {

    @SerializedName("crystal")
    CRYSTAL("Crystal"),

    @SerializedName("crystal")
    VANILLA("Crystal"),

    @SerializedName("nethop")
    NETH_OP("NethOP"),

    @SerializedName("sword")
    SWORD("Sword"),

    @SerializedName("uhc")
    UHC("UHC"),

    @SerializedName("pot")
    POT("Pot"),

    @SerializedName("neth_pot")
    NETH_POT("Netherite Pot"),

    @SerializedName("smp")
    SMP("SMP"),

    @SerializedName("axe")
    AXE("Axe"),

    @SerializedName("elytra")
    ELYTRA("Elytra"),

    @SerializedName("mace")
    MACE("Mace");

    var displayName: String = name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

    constructor(displayName: String) {
        this.displayName = displayName
    }
}