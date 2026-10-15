package io.github.mithrandler.labhud.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Pairing
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(repo: LabRepository, initialLink: String?, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var camera by remember { mutableStateOf(granted()) }
    var cameraError by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }   // "don't ask again": only the system settings can give it now
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        camera = ok
        cameraError = ""
        refused = !ok && context.activity()?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true
    }
    // A one-time grant ends when the app goes to the background, and the permission can be taken
    // back in the settings: read it again every time the screen comes back.
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) camera = granted() }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }
    var pasted by remember { mutableStateOf(initialLink ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun tryPair(text: String) {
        if (busy) return
        val link = Pairing.parse(text) ?: run { error = "That is not a LabHUD pairing link."; return }
        busy = true
        error = ""
        scope.launch {
            runCatching { Pairing.pair(link, Build.MODEL ?: "phone", repo.store) }
                .onSuccess { busy = false; onDone() }
                .onFailure {
                    busy = false
                    error = when {
                        it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.SocketTimeoutException ->
                            "Cannot reach ${link.url}. Is this phone on your home network or VPN?"
                        else -> it.message ?: "Pairing failed."
                    }
                }
        }
    }
    LaunchedEffect(initialLink) { if (!initialLink.isNullOrBlank()) tryPair(initialLink) }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (onBack == null) "Welcome to LabHUD" else "Add a server") },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("On your LabHUD display, open ⚙ > Phone app > Pair a phone, or run init.py pair on the server, then scan the code.",
                color = Lab.sub, fontSize = 14.sp)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(CardShape).border(BorderStroke(1.dp, Lab.border), CardShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    busy -> CircularProgressIndicator()
                    camera && cameraError.isEmpty() -> QrScanner(onCode = { if (!busy) tryPair(it) }, onError = { cameraError = it })
                    else -> Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cameraError.ifEmpty { "The camera reads the QR code." }, color = Lab.muted)
                        when {
                            cameraError.isNotEmpty() -> OutlinedButton(onClick = { cameraError = "" }, modifier = Modifier.padding(top = 10.dp)) { Text("Try again") }
                            refused -> Button(onClick = { context.openAppSettings() }, modifier = Modifier.padding(top = 10.dp)) { Text("Open settings") }
                            else -> Button(onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 10.dp)) { Text("Allow the camera") }
                        }
                    }
                }
            }
            if (error.isNotEmpty()) {
                Surface(shape = CardShape, color = Lab.red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Lab.red.copy(alpha = 0.5f))) {
                    Text(error, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }
            Text("Or paste the link printed under the code:", color = Lab.muted, fontSize = 13.sp)
            OutlinedTextField(pasted, { pasted = it.trim() }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("labhud://pair?…") })
            OutlinedButton(onClick = { tryPair(pasted) }, enabled = pasted.isNotBlank() && !busy) { Text("Pair") }
            Text("The phone talks to LabHUD directly: at home on Wi-Fi, or from outside through your VPN (WireGuard, Tailscale).",
                color = Lab.muted, fontSize = 12.sp)
        }
    }
}

/** CameraX preview plus a ZXing reader on every frame, until a QR code is read. A camera that
 * cannot start (no permission any more, the camera switched off in Quick Settings, another app
 * holding it) ends in [onError], never in a crash. */
@Composable
private fun QrScanner(onCode: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { QRCodeReader() }
    val hints = remember { mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                runCatching {
                    val p = future.get().also { provider = it }
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { image ->
                        decode(image, reader, hints)?.let { text -> view.post { onCode(text) } }
                    }
                    p.unbindAll()
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure { onError("The camera did not start. Paste the link instead, or try again.") }
            }, ContextCompat.getMainExecutor(context))
            view
        },
    )
}

private fun decode(image: ImageProxy, reader: QRCodeReader, hints: Map<DecodeHintType, Any>): String? = image.use {
    val plane = it.planes[0]
    val buf = plane.buffer
    val data = ByteArray(buf.remaining()).also { b -> buf.get(b) }
    val source = PlanarYUVLuminanceSource(data, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
    runCatching { reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text }.getOrNull()
        .also { reader.reset() }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
