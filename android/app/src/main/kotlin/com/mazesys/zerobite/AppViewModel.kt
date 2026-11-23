package com.mazesys.zerobite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.reminders.PermissionRelay

/** Keeps the coordinator tree and its navigation state alive across configuration changes. */
class AppViewModel(dependencies: AppDependencies) : ViewModel() {
    val coordinator = AppCoordinator(dependencies, viewModelScope)
    val sheetStores = SheetViewModelStores()
    val permissions = PermissionRelay()

    override fun onCleared() = sheetStores.clearAll()

    companion object {
        fun factory(dependencies: AppDependencies): ViewModelProvider.Factory =
            viewModelFactory { initializer { AppViewModel(dependencies) } }
    }
}
