package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.FirestoreSyncRepository
import com.example.data.repository.GroceryRepository

class GroceryViewModelFactory(
    private val application: Application,
    private val repository: GroceryRepository,
    private val firestoreSyncRepository: FirestoreSyncRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroceryViewModel::class.java)) {
            return GroceryViewModel(application, repository, firestoreSyncRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
