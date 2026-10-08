package com.aicompose.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aicompose.core.vision.pose.AdviceLevel
import com.aicompose.core.vision.pose.DetectedPose
import com.aicompose.core.vision.pose.PoseAdvice
import com.aicompose.core.vision.pose.PoseCoach
import com.aicompose.core.vision.pose.PoseEstimator
import com.aicompose.core.vision.pose.PoseTemplate
import com.aicompose.core.vision.pose.PoseTemplates
import com.aicompose.feature.pose.AdvicePanel
import com.aicompose.feature.pose.PoseOverlay
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@ExperimentalGetImage
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

    // 实时取景状态
    var livePose by remember { mutableStateOf<DetectedPose?>(null) }
    var liveAdvice by remember { mutableStateOf<List<PoseAdvice>>(emptyList()) }
    var contentWidth by remember { mutableStateOf(0) }
    var contentHeight by remember { mutableStateOf(0) }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }

    // 拍照结果状态
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropResult by remember { mutableStateOf<CompositionResult?>(null) }
    var photoPose by remember { mutableStateOf<DetectedPose?>(null) }
    var photoAdvice by remember { mutableStateOf<List<PoseAdvice>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 实时用流式模式, 拍照用单图模式
    val liveEstimator = remember { PoseEstimator(PoseDetectorOptions.STREAM_MODE) }
    val photoEstimator = remember { PoseEstimator(PoseDetectorOptions.SINGLE_IMAGE_MODE) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            liveEstimator.close()
            photoEstimator.close()
            analysisExecutor.shutdown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("需要相机权限来提供实时构图与姿势提示")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授权相机")
            }
        }
        return
    }

    // 没手动选模板时, 自动推荐最接近当前姿势的一个
    val autoTarget: PoseTemplate? = remember(livePose) {
        livePose?.let { PoseCoach.rankTemplates(it).firstOrNull()?.first }
    }
    val targetTemplate = PoseTemplates.byId(selectedTemplateId) ?: autoTarget
    val matchScore: Float? = remember(livePose, targetTemplate) {
        val p = livePose ?: return@remember null
        val t = targetTemplate ?: return@remember null
        PoseCoach.rankTemplates(p).firstOrNull { it.first.id == t.id }?.second
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                        .build()
                                )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            var lastTs = 0L
                            analysis.setAnalyzer(analysisExecutor) { proxy ->
                                val media = proxy.image
                                val now = System.currentTimeMillis()
                                // 限流到 ~8fps, 够用又不烫手
                                if (media == null || now - lastTs < 120) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                lastTs = now
                                val rot = proxy.imageInfo.rotationDegrees
                                contentWidth = if (rot == 90 || rot == 270) proxy.height else proxy.width
                                contentHeight = if (rot == 90 || rot == 270) proxy.width else proxy.height
                                liveEstimator.detectAsync(
                                    mediaImage = media,
                                    rotationDegrees = rot,
                                    onResult = { p ->
                                        livePose = p
                                        liveAdvice = p?.let { PoseCoach.buildAdvice(it) } ?: emptyList()
                                    },
                                    onComplete = { proxy.close() },
                                )
                            }
                            imageCapture = capture
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                                analysis,
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )

            PoseOverlay(
                pose = livePose,
                target = targetTemplate,
                contentWidth = contentWidth,
                contentHeight = contentHeight,
                fillCenter = true,
                drawThirds = true,
                modifier = Modifier.fillMaxSize(),
            )

            AdvicePanel(
                advice = liveAdvice,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )

            val caption = buildString {
                if (targetTemplate != null) {
                    append("参考姿势：${targetTemplate.name}")
                    if (matchScore != null) append("  匹配 ${(matchScore * 100).toInt()}%")
                }
            }
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    color = Color(0xFFFFC857),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedTemplateId == null,
                    onClick = { selectedTemplateId = null },
                    label = { Text("自动") },
                )
            }
            items(PoseTemplates.ALL, key = { it.id }) { t ->
                FilterChip(
                    selected = selectedTemplateId == t.id,
                    onClick = { selectedTemplateId = t.id },
                    label = { Text(t.name) },
                )
            }
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
                                cropResult = null
                                photoPose = null
                                photoAdvice = emptyList()
                                scope.launch {
                                    try {
                                        cropResult = AnalyzerHolder.analyze(context, bmp)
                                        val p = photoEstimator.detect(bmp)
                                        photoPose = p
                                        photoAdvice = p?.let { PoseCoach.buildAdvice(it) }
                                            ?: listOf(PoseAdvice(AdviceLevel.WARN, "没检测到人物，请让被拍者完整进入画面"))
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cropResult?.let { c ->
                    Text(
                        "建议裁剪 评分 %.3f   耗时 %d ms".format(c.score, c.inferenceMs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height),
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    PoseOverlay(
                        pose = photoPose,
                        target = photoPose?.let { p ->
                            PoseTemplates.byId(selectedTemplateId)
                                ?: PoseCoach.rankTemplates(p).firstOrNull()?.first
                        },
                        contentWidth = bmp.width,
                        contentHeight = bmp.height,
                        fillCenter = false,
                        drawThirds = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (photoAdvice.isNotEmpty()) {
                    AdvicePanel(advice = photoAdvice, modifier = Modifier.fillMaxWidth())
                }
            }
        }
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