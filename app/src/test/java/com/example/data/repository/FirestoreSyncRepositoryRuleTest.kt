package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FirestoreSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun authenticatedUser_canSaveAndRestoreCloudProducts() = runBlocking {
        val testEmail = "test_owner@example.com"
        val uid = signInTestUser(testEmail)

        val repository = FirestoreSyncRepository(firestore)

        val product = CloudProduct(
            id = "prod_123",
            userId = uid,
            name = "Mustard Oil 1L",
            category = "Grocery",
            price = 160.0,
            costPrice = 140.0,
            stockQuantity = 25.0,
            unit = "ltr"
        )

        repository.saveProduct(uid, product)

        val restoreResult = repository.restoreAllFromCloud(uid)
        assertTrue(restoreResult.isSuccess)

        val data = restoreResult.getOrNull()
        assertNotNull(data)
        assertTrue(data!!.products.any { it.name == "Mustard Oil 1L" && it.price == 160.0 })
    }

    @Test
    fun authenticatedUser_canSaveCustomerAndTransaction() = runBlocking {
        val testEmail = "test_customer_owner@example.com"
        val uid = signInTestUser(testEmail)

        val repository = FirestoreSyncRepository(firestore)

        val customer = CloudCustomer(
            id = "cust_999",
            userId = uid,
            name = "Babulal Sahu",
            phone = "9861000000",
            totalUdhar = 320.0
        )
        repository.saveCustomer(uid, customer)

        val tx = CloudTransaction(
            id = "tx_999",
            userId = uid,
            customerId = "cust_999",
            amount = 320.0,
            type = "UDHAR",
            note = "Rice 5kg, Oil 1L",
            itemsSummary = "Rice & Oil"
        )
        repository.saveTransaction(uid, tx)

        val restoreResult = repository.restoreAllFromCloud(uid)
        assertTrue(restoreResult.isSuccess)
        val data = restoreResult.getOrNull()
        assertNotNull(data)
        assertTrue(data!!.customers.any { it.name == "Babulal Sahu" })
        assertTrue(data.transactions.any { it.amount == 320.0 && it.type == "UDHAR" })
    }

    @Test
    fun unauthenticatedUser_failsOperation() = runBlocking {
        auth.signOut()
        val repository = FirestoreSyncRepository(firestore)

        var failed = false
        try {
            repository.saveProduct("random_uid", CloudProduct(name = "Unauth Item"))
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)
    }
}
