package aktarjabed.com.jaascanner.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.findActivity
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import aktarjabed.com.jaascanner.ui.nav.ToolKind
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's own camera, used when Google's document scanner cannot start (for example on a
 * phone without Play services, or with an out-of-date scanner module). Camera access is asked for
 * at runtime with the Activity Result API, only on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(app: AppViewModel, screenId: Long) {
    val context = LocalContext.current
    val container = context.container
    val vm = containerViewModel { CameraScanViewModel(it) }
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            permanentlyDenied = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        }
    }

    // Re-check after a trip to the system settings page.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (hasPermission) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Pictures from a session that died with the process would otherwise sit in the cache forever.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            captureDir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    val close: () -> Unit = {
        vm.discard()
        app.pop()
    }
    BackHandler(enabled = !vm.saving) { close() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    if (hasPermission) {
        CameraBinding(
            previewView = previewView,
            imageCapture = imageCapture,
            onError = { cameraError = it },
        )
    }

    val takePicture: () -> Unit = {
        if (!hasPermission || capturing || vm.saving || vm.pageCount >= CameraScanViewModel.MAX_PAGES) {
            if (vm.pageCount >= CameraScanViewModel.MAX_PAGES) {
                container.messenger.post("That is the most pages one scan can hold. Tap Done to save them.")
            }
        } else {
            capturing = true
            imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val file = File(captureDir(context), "page-${System.currentTimeMillis()}.jpg")
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!vm.add(file)) {
                            container.messenger.post("That is the most pages one scan can hold.")
                        }
                        capturing = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        capturing = false
                        container.messenger.post("The picture could not be taken. Try again.")
                    }
                },
            )
        }
    }

    val finish: () -> Unit = {
        vm.save(context) { id ->
            // The save finishes after the user may have left this screen; only replace the top
            // screen when this camera screen is still the one showing.
            app.replaceTopIfCurrent(screenId, Screen.Viewer(id))
        }
    }
    val canFinish = vm.pageCount > 0 && !vm.saving && !capturing

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = close, enabled = !vm.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                title = { Text("Scan with camera") },
                actions = {
                    TextButton(onClick = finish, enabled = canFinish) {
                        Text(
                            if (vm.pageCount > 0) "Done (${vm.pageCount})" else "Done",
                            color = if (canFinish) Color.White else Color.White.copy(alpha = 0.45f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasPermission -> PermissionContent(
                    permanentlyDenied = permanentlyDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                    onUsePictures = {
                        vm.discard()
                        app.replaceTopIfCurrent(screenId, Screen.Tool(ToolKind.ImagesToPdf))
                    },
                )

                else -> {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    cameraError?.let { message ->
                        Text(
                            message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(16.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            when {
                                vm.pageCount == 0 -> "Line up the page and take a picture."
                                vm.pageCount == 1 -> "1 page captured"
                                else -> "${vm.pageCount} pages captured"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { vm.removeLast() },
                                enabled = vm.pageCount > 0 && !vm.saving,
                            ) { Text("Undo last", color = Color.White) }
                            Button(onClick = takePicture, enabled = !capturing && !vm.saving) {
                                Text(if (capturing) "Taking..." else "Take picture")
                            }
                        }
                    }
                }
            }

            if (vm.saving) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            "Making the PDF... ${vm.progress} of ${vm.total}",
                            color = Color.White,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionContent(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onUsePictures: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Camera access is needed to scan with this phone's camera.",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "JAScanner asks only when you open this screen. You can also add pictures through " +
                "Photos to PDF instead.",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 20.dp)) {
                Text("Open app settings")
            }
            Text(
                "Choose Permissions, then Camera, and allow it.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Button(onClick = onRequest, modifier = Modifier.padding(top = 20.dp)) {
                Text("Allow camera")
            }
        }
        TextButton(onClick = onUsePictures, modifier = Modifier.padding(top = 8.dp)) {
            Text("Use Photos to PDF instead", color = Color.White)
        }
    }
}

/** Binds CameraX to the screen's lifecycle; unbound again when the screen goes away. */
@Composable
private fun CameraBinding(
    previewView: PreviewView,
    imageCapture: ImageCapture,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, previewView, imageCapture) {
        var provider: ProcessCameraProvider? = null
        val executor = ContextCompat.getMainExecutor(context)
        val future = try {
            ProcessCameraProvider.getInstance(context)
        } catch (error: Throwable) {
            onError("The camera could not start on this phone.")
            null
        }
        if (future != null) {
            future.addListener(
                {
                    try {
                        val bound = future.get()
                        provider = bound
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val selector = if (bound.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                        bound.unbindAll()
                        bound.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                    } catch (error: Throwable) {
                        onError("The camera could not start on this phone.")
                    }
                },
                executor,
            )
        }
        onDispose { provider?.unbindAll() }
    }
}

private fun captureDir(context: android.content.Context): File =
    File(context.cacheDir, "camera-scan").apply { mkdirs() }
