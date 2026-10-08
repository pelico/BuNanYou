package com.aicompose.feature.camera

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aicompose.core.vision.AnalyzerHolder
import com.aicompose.core.vision.CompositionResult
import com.aicompose.core.vision.scene.PoseCatalog
import com.aicompose.core.vision.scene.Scene
import com.aicompose.core.vision.scene.SceneDetector
import com.aicompose.core.vision.scene.SceneHit
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val SCENE_INTERVAL_MS = 700L

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

    // 场景识别状态: autoHit 是实时识别结果 (含命中标签与置信度), manualScene 是用户手选 (优先级更高)
    var autoHit by remember { mutableStateOf<SceneHit?>(null) }
    var manualScene by remember { mutableStateOf<Scene?>(null) }
    var selectedPoseId by remember { mutableStateOf<String?>(null) }
    // 记住上次生效的场景, 用于「识别到新场景就自动推荐姿势」
    var lastAppliedSceneKey by remember { mutableStateOf<String?>(null) }

    // 拍照结果状态
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropResult by remember { mutableStateOf<CompositionResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val sceneDetector = remember { SceneDetector() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            sceneDetector.close()
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

    val effectiveScene = manualScene ?: autoHit?.scene
    val poses = effectiveScene?.let { PoseCatalog.of(it) } ?: emptyList()
    val selectedPose = PoseCatalog.byId(selectedPoseId)?.takeIf { it.scene == effectiveScene }
    val silhouette = remember(selectedPose?.id) {
        selectedPose?.let { loadSilhouette(context, it.id) }
    }

    // 场景一变就自动推荐该场景的第一个姿势 (AI 推荐), 用户手动换姿势后不再打扰
    LaunchedEffect(effectiveScene?.key) {
        val scene = effectiveScene ?: return@LaunchedEffect
        if (lastAppliedSceneKey != scene.key) {
            lastAppliedSceneKey = scene.key
            selectedPoseId = PoseCatalog.of(scene).firstOrNull()?.id
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                                // 场景识别不需要很频繁, 限流到 ~1.4fps, 够用又不烫手
                                if (media == null || now - lastTs < SCENE_INTERVAL_MS) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                lastTs = now
                                val rot = proxy.imageInfo.rotationDegrees
                                sceneDetector.classifyAsync(
                                    mediaImage = media,
                                    rotationDegrees = rot,
                                    onResult = { hit -> if (hit != null) autoHit = hit },
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

            SilhouetteOverlay(
                silhouette = silhouette,
                drawThirds = true,
                modifier = Modifier.fillMaxSize(),
            )

            // 左上角: 识别到的场景 + 命中标签与置信度
            val sceneLabel = buildString {
                when {
                    effectiveScene != null -> append("${effectiveScene.emoji} ${effectiveScene.displayName}")
                    autoHit != null -> append("识别中…")
                    else -> append("识别中…")
                }
                autoHit?.let { h ->
                    if (h.label != null && h.scene == effectiveScene) {
                        append(" · ${h.label} ${(h.confidence * 100).toInt()}%")
                    }
                }
            }
            Text(
                text = sceneLabel,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            // 底部: 当前姿势说明 (AI 自动推荐或用户自选)
            val caption = when {
                selectedPose != null -> "✨ AI 推荐 · ${selectedPose.name}：${selectedPose.summary}"
                effectiveScene != null -> effectiveScene.hint
                else -> "把镜头对准场景，正在识别…"
            }
            Text(
                text = caption,
                color = Color(0xFFFFC857),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        SceneChipRow(
            selected = manualScene,
            auto = autoHit?.scene,
            onSelect = { scene ->
                manualScene = scene
                // 换场景时清掉不属于新场景的选中姿势
                val pose = PoseCatalog.byId(selectedPoseId)
                if (pose != null && pose.scene != (scene ?: autoHit?.scene)) selectedPoseId = null
            },
            modifier = Modifier.padding(top = 10.dp),
        )

        if (poses.isNotEmpty()) {
            PosePickerRow(
                poses = poses,
                selectedId = selectedPose?.id,
                recommendedId = poses.firstOrNull()?.id,
                onSelect = { pose ->
                    selectedPoseId = if (selectedPoseId == pose.id) null else pose.id
                },
                modifier = Modifier.padding(top = 10.dp),
            )
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
                                scope.launch {
                                    try {
                                        cropResult = AnalyzerHolder.analyze(context, bmp)
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
                }
            }
        }
    }
}

/** 剪影素材很小 (十几 KB), 直接同步解码, 用 map 缓存避免重复 IO。 */
private val silhouetteCache = HashMap<String, ImageBitmap?>()

private fun loadSilhouette(context: Context, id: String): ImageBitmap? {
    if (silhouetteCache.containsKey(id)) return silhouetteCache[id]
    val bitmap = runCatching {
        context.assets.open("poses/$id.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }.getOrNull()
    silhouetteCache[id] = bitmap
    return bitmap
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