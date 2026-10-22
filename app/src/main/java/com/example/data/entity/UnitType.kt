package com.example.data.entity

enum class UnitType(val displayName: String, val hindiName: String, val odiaName: String, val baseUnit: String) {
    KG("Kilogram (kg)", "किलोग्राम (kg)", "କିଲୋଗ୍ରାମ (kg)", "kg"),
    LITRE("Litre (L)", "लीटर (L)", "ଲିଟର (L)", "L"),
    PIECE("Piece (pc)", "पीस (pc)", "ପିସ୍ (pc)", "pc");

    companion object {
        fun fromString(value: String): UnitType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KG
        }
    }
}

enum class SubUnit(val symbol: String, val displayName: String, val hindiName: String, val odiaName: String, val parentType: UnitType, val factorToBase: Double) {
    GRAM("g", "Grams (g)", "ग्राम", "ଗ୍ରାମ", UnitType.KG, 0.001),
    KG("kg", "Kilograms (kg)", "किलो", "କିଲୋ", UnitType.KG, 1.0),
    ML("ml", "Millilitres (ml)", "मिली", "ମିଲି", UnitType.LITRE, 0.001),
    LITRE("L", "Litres (L)", "लीटर", "ଲିଟର", UnitType.LITRE, 1.0),
    PIECE("pc", "Pieces (pc)", "पीस", "ପିସ୍", UnitType.PIECE, 1.0);

    companion object {
        fun forUnitType(unitType: UnitType): List<SubUnit> = when (unitType) {
            UnitType.KG -> listOf(GRAM, KG)
            UnitType.LITRE -> listOf(ML, LITRE)
            UnitType.PIECE -> listOf(PIECE)
        }
    }
}
