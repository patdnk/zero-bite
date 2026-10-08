package com.mazesys.zerobite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppFlowController
import com.mazesys.zerobite.coordination.SheetViewModelStores

/** Keeps app-level flow and navigation state alive across configuration changes. */
class AppViewModel(dependencies: AppDependencies) : ViewModel() {
    val flow = AppFlowController(dependencies.settings)
    val coordinator = AppCoordinator(today = dependencies.clock::today)
    val sheetStores = SheetViewModelStores()

    override fun onCleared() = sheetStores.clearAll()

    companion object {
        fun factory(dependencies: AppDependencies): ViewModelProvider.Factory =
            viewModelFactory { initializer { AppViewModel(dependencies) } }
    }
}
