package com.shade.app.ui.webpairing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shade.app.ui.theme.AccentPurple
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanner(
    modifier: Modifier = Modifier,
    onQrText: (String) -> Unit,
    onCancelScan: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onQrTextLatest by rememberUpdatedState(onQrText)
    val scheme = MaterialTheme.colorScheme

    val permissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val previewViewState = remember { mutableStateOf<PreviewView?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> permissionGranted.value = granted }
    )

    LaunchedEffect(Unit) {
        if (!permissionGranted.value) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!permissionGranted.value) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "QR okutmak için kamera gerekli",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Görüntü kaydedilmez; yalnızca kod okunur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Text("İzin ver")
                    }
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "İsteğe bağlı; Ayarlar’dan da yönetebilirsin.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewState.value = this
                }
            },
            update = { previewViewState.value = it }
        )
        QrScannerOverlay(onCancel = onCancelScan)
    }

    val previewView = previewViewState.value
    DisposableEffect(permissionGranted.value, lifecycleOwner, previewView) {
        if (permissionGranted.value && previewView != null) {
            bindCamera(
                context = context,
                previewView = previewView,
                onQrText = { onQrTextLatest(it) },
                lifecycleOwner = lifecycleOwner,
                permissionGranted = permissionGranted,
                analysisExecutor = analysisExecutor
            )
        }
        onDispose {
            tryUnbindAll(context)
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun bindCamera(
    context: Context,
    previewView: PreviewView,
    onQrText: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    permissionGranted: MutableState<Boolean>,
    analysisExecutor: Executor
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val delivered = AtomicBoolean(false)
            analysis.setAnalyzer(
                analysisExecutor,
                object : ImageAnalysis.Analyzer {
                    override fun analyze(imageProxy: ImageProxy) {
                        if (delivered.get()) {
                            imageProxy.close()
                            return
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return
                        }
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val qr = barcodes.firstOrNull()?.rawValue
                                    ?: barcodes.firstOrNull()?.displayValue
                                if (!qr.isNullOrBlank() && delivered.compareAndSet(false, true)) {
                                    onQrText(qr)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                }
            )

            try {
                cameraProvider.unbindAll()
                if (!permissionGranted.value) return@addListener
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {
                // UI tarafında ayrıca hata gösterilmiyor; analyzer hiç çalışmaz.
            }
        },
        mainExecutor
    )
}

private fun tryUnbindAll(context: Context) {
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
        { runCatching { future.get().unbindAll() } },
        mainExecutor
    )
}

@Composable
private fun QrScannerOverlay(onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(268.dp)
                .border(width = 3.dp, color = Color.White.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = scheme.surface.copy(alpha = 0.94f),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "QR kodu beyaz çerçevenin içine gelecek şekilde tut.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("İptal", color = AccentPurple)
                }
            }
        }
    }
}
