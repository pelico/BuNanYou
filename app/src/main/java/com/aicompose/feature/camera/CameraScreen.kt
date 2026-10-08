package com.aicompose.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aicompose.core.vision.AnalyzerHolder
import com.aicompose.core.vision.CompositionResult
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<CompositionResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("需要相机权限来提供实时构图提示")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授权相机")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            // 1.3.x 默认输出格式即 JPEG, 可直接解码为 Bitmap
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture
                        val selector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
            // 三分线 + 中心标记叠加
            RuleOfThirdsOverlay()
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        errorMsg = "相机尚未就绪, 请稍候再试"
                        return@Button
                    }
                    isAnalyzing = true
                    errorMsg = null
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                val decoded = try {
                                    imageProxyToBitmap(image)
                                } catch (t: Throwable) {
                                    null
                                } finally {
                                    image.close()
                                }
                                if (decoded == null) {
                                    isAnalyzing = false
                                    errorMsg = "照片解析失败, 请重试"
                                    return
                                }
                                // 相机原图通常 12MP 级别, 直接上屏/推理会 OOM, 先降采样
                                val bmp = downscale(decoded)
                                capturedBitmap = bmp
                                result = null
                                scope.launch {
                                    try {
                                        result = AnalyzerHolder.analyze(context, bmp)
                                    } catch (t: Throwable) {
                                        errorMsg = "分析失败: ${t.message ?: t.javaClass.simpleName}"
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                isAnalyzing = false
                                errorMsg = "拍照失败: ${exc.message}"
                            }
                        }
                    )
                },
            ) {
                Text("拍照分析")
            }
            if (isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }

        errorMsg?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        capturedBitmap?.let { bmp ->
            result?.bestCrop?.let { _ ->
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("建议裁剪 (评分 %.3f)".format(result!!.score),
                        style = MaterialTheme.typography.bodyMedium)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleOfThirdsOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(alpha = 0.4f)
        // 两条竖线
        drawLine(color, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 2f)
        drawLine(color, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), strokeWidth = 2f)
        // 两条横线
        drawLine(color, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 2f)
        drawLine(color, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), strokeWidth = 2f)
    }
}

private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap? {
    val planes = image.planes
    if (planes.isEmpty()) return null
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    if (bytes.isEmpty()) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/** 把长边限制到 maxSide 以内, 避免全分辨率大图导致 OOM。 */
private fun downscale(src: Bitmap, maxSide: Int = 2048): Bitmap {
    val longSide = maxOf(src.width, src.height)
    if (longSide <= maxSide) return src
    val scale = maxSide.toFloat() / longSide
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
    if (scaled != src) src.recycle()
    return scaled
}
