package com.example

import com.example.data.entity.SubUnit
import com.example.domain.calculator.WeightPriceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightPriceCalculatorTest {

    @Test
    fun testSugarCalculations() {
        val sugarRate = 48.0 // ₹48 per kg

        assertEquals(4.80, WeightPriceCalculator.calculatePrice(sugarRate, 100.0, SubUnit.GRAM), 0.001)
        assertEquals(12.00, WeightPriceCalculator.calculatePrice(sugarRate, 250.0, SubUnit.GRAM), 0.001)
        assertEquals(24.00, WeightPriceCalculator.calculatePrice(sugarRate, 500.0, SubUnit.GRAM), 0.001)
        assertEquals(36.00, WeightPriceCalculator.calculatePrice(sugarRate, 750.0, SubUnit.GRAM), 0.001)
        assertEquals(48.00, WeightPriceCalculator.calculatePrice(sugarRate, 1.0, SubUnit.KG), 0.001)
        assertEquals(120.00, WeightPriceCalculator.calculatePrice(sugarRate, 2.5, SubUnit.KG), 0.001)
    }

    @Test
    fun testRiceCalculations() {
        val riceRate = 60.0 // ₹60 per kg
        assertEquals(150.00, WeightPriceCalculator.calculatePrice(riceRate, 2.5, SubUnit.KG), 0.001)
    }

    @Test
    fun testOilCalculations() {
        val oilRate = 150.0 // ₹150 per litre
        assertEquals(75.00, WeightPriceCalculator.calculatePrice(oilRate, 500.0, SubUnit.ML), 0.001)
    }

    @Test
    fun testReverseBudgetCalculations() {
        val sugarRate = 48.0
        val budget = 20.0 // ₹20 worth of sugar
        val qtyInGrams = WeightPriceCalculator.calculateQuantityForBudget(budget, sugarRate, SubUnit.GRAM)
        assertEquals(416.667, qtyInGrams, 0.01)
    }

    @Test
    fun testInvalidInputsSafeHandling() {
        assertEquals(0.0, WeightPriceCalculator.calculatePrice(-10.0, 500.0, SubUnit.GRAM), 0.0)
        assertEquals(0.0, WeightPriceCalculator.calculatePrice(48.0, -50.0, SubUnit.GRAM), 0.0)
    }
}
