package aktarjabed.com.jaascanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.container

/**
 * Creates (or returns) the ViewModel of type [VM] that belongs to the screen being shown.
 * The owner is supplied by JAScannerRoot, so the ViewModel is cleared when the screen is popped.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel(factory = viewModelFactory { initializer<VM> { create(container) } })
}
