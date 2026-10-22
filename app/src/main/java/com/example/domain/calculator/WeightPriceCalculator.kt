package com.example.domain.calculator

import com.example.data.entity.SubUnit
import com.example.data.entity.UnitType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

object WeightPriceCalculator {

    private val currencyFormat = DecimalFormat("##,##,##0.00")
    private val quantityFormat = DecimalFormat("0.###")

    fun formatCurrency(amount: Double, symbol: String = "₹"): String {
        return "$symbol ${currencyFormat.format(amount)}"
    }

    fun formatQuantity(quantity: Double, unit: String): String {
        return "${quantityFormat.format(quantity)} $unit"
    }

    /**
     * Calculates the monetary price for a given quantity and rate.
     * E.g.
     * Rate = ₹48 / kg
     * Quantity = 250 g -> factor = 0.001 -> quantityInBase = 0.25 kg -> Total = ₹12.00
     */
    fun calculatePrice(
        ratePerBaseUnit: Double,
        quantityEntered: Double,
        subUnit: SubUnit
    ): Double {
        if (ratePerBaseUnit < 0 || quantityEntered <= 0) return 0.0
        val rateBd = BigDecimal.valueOf(ratePerBaseUnit)
        val qtyBd = BigDecimal.valueOf(quantityEntered)
        val factorBd = BigDecimal.valueOf(subUnit.factorToBase)

        val qtyInBaseUnit = qtyBd.multiply(factorBd)
        val total = qtyInBaseUnit.multiply(rateBd)

        return total.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Reverse Calculation: Given a budget amount (e.g. ₹20), find how much quantity the customer gets.
     * E.g.
     * Rate = ₹48 / kg
     * Budget = ₹20
     * Target SubUnit = Grams -> Base qty = 20 / 48 = 0.416666 kg -> In grams = 416.67 g!
     */
    fun calculateQuantityForBudget(
        budgetAmount: Double,
        ratePerBaseUnit: Double,
        targetSubUnit: SubUnit
    ): Double {
        if (ratePerBaseUnit <= 0 || budgetAmount <= 0) return 0.0
        val budgetBd = BigDecimal.valueOf(budgetAmount)
        val rateBd = BigDecimal.valueOf(ratePerBaseUnit)
        val factorBd = BigDecimal.valueOf(targetSubUnit.factorToBase)

        // baseQty = budget / rate
        val baseQty = budgetBd.divide(rateBd, 6, RoundingMode.HALF_UP)
        // targetQty = baseQty / factor
        val targetQty = baseQty.divide(factorBd, 3, RoundingMode.HALF_UP)

        return targetQty.toDouble()
    }

    /**
     * Converts entered quantity to base unit (kg, L, pc).
     */
    fun convertToBaseQuantity(quantityEntered: Double, subUnit: SubUnit): Double {
        if (quantityEntered <= 0) return 0.0
        val qtyBd = BigDecimal.valueOf(quantityEntered)
        val factorBd = BigDecimal.valueOf(subUnit.factorToBase)
        return qtyBd.multiply(factorBd).setScale(4, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Quick presets for Indian grocery shopping
     */
    fun getCommonQuantityPresets(unitType: UnitType): List<Pair<String, Pair<Double, SubUnit>>> {
        return when (unitType) {
            UnitType.KG -> listOf(
                "100 g" to (100.0 to SubUnit.GRAM),
                "250 g (पाव)" to (250.0 to SubUnit.GRAM),
                "500 g (आधा किलो)" to (500.0 to SubUnit.GRAM),
                "750 g" to (750.0 to SubUnit.GRAM),
                "1 kg (एक किलो)" to (1.0 to SubUnit.KG),
                "2 kg" to (2.0 to SubUnit.KG),
                "2.5 kg" to (2.5 to SubUnit.KG),
                "5 kg" to (5.0 to SubUnit.KG)
            )
            UnitType.LITRE -> listOf(
                "100 ml" to (100.0 to SubUnit.ML),
                "250 ml (पाव)" to (250.0 to SubUnit.ML),
                "500 ml (आधा लीटर)" to (500.0 to SubUnit.ML),
                "1 L (एक लीटर)" to (1.0 to SubUnit.LITRE),
                "2 L" to (2.0 to SubUnit.LITRE),
                "5 L" to (5.0 to SubUnit.LITRE)
            )
            UnitType.PIECE -> listOf(
                "1 pc" to (1.0 to SubUnit.PIECE),
                "2 pcs" to (2.0 to SubUnit.PIECE),
                "5 pcs" to (5.0 to SubUnit.PIECE),
                "6 pcs (आधा दर्जन)" to (6.0 to SubUnit.PIECE),
                "10 pcs" to (10.0 to SubUnit.PIECE),
                "12 pcs (1 दर्जन)" to (12.0 to SubUnit.PIECE)
            )
        }
    }
}
