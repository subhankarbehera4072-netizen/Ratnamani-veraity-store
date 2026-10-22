package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.GroceryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SmartGroceryApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { GroceryRepository(database) }
    val firestoreSyncRepository by lazy { com.example.data.repository.FirestoreSyncRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SmartGroceryApp
            private set
    }
}
