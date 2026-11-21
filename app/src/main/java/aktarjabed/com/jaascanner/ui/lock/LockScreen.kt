package aktarjabed.com.jaascanner.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.findActivity
import aktarjabed.com.jaascanner.security.AppLock
import aktarjabed.com.jaascanner.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun LockScreen(app: AppViewModel) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val available = AppLock.isAvailable(context)

    val prompt: () -> Unit = {
        val activity = context.findActivity() as? FragmentActivity
        if (activity != null && available) {
            message = null
            AppLock.prompt(
                activity = activity,
                title = "Unlock JAScanner",
                subtitle = "Confirm it is you",
                onSuccess = { app.unlock() },
                onFailure = { message = it },
            )
        }
    }

    LaunchedEffect(Unit) { prompt() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("JAScanner is locked", style = MaterialTheme.typography.titleLarge)
        if (available) {
            Button(onClick = prompt) { Text("Unlock") }
        } else {
            Text(
                "This phone no longer has a screen lock or fingerprint set up, so the app lock cannot ask for one.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                scope.launch {
                    container.settings.setAppLock(false)
                    app.unlock()
                }
            }) { Text("Turn off app lock") }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}
