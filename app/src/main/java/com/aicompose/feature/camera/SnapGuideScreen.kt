package com.aicompose.feature.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aicompose.core.guide.AlignmentState
import com.aicompose.core.guide.CompositionEngine
import com.aicompose.core.guide.DetectedJoint
import com.aicompose.core.guide.PoseLandmarkerManager
import com.aicompose.core.guide.PoseTemplate
import com.aicompose.core.guide.PoseTemplateCatalog
import com.aicompose.core.guide.Tilt
import com.aicompose.core.guide.TiltSensor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

/** 骨骼检测限流 ~15fps */
private const val FRAME_INTERVAL_MS = 66L

/**
 * SnapGuide 主取景页:
 * CameraX 预览 → MediaPipe 骨骼 + 物理倾角 → 启发式构图引擎 → Compose 叠加闭环引导。
 */
@Composable
fun SnapGuideScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    // ---------- 权限 ----------
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }
    LaunchedEffect(Unit) {
        if (!hasCamera) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // ---------- 状态 ----------
    var tilt by remember { mutableStateOf(Tilt()) }
    var pose by remember { mutableStateOf<Map<Int, DetectedJoint>?>(null) }
    var frame by remember { mutableStateOf(IntSize(480, 640)) }
    var selected by remember { mutableStateOf<PoseTemplate?>(null) }
    var mirrored by remember { mutableStateOf(false) }
    var scoreEma by remember { mutableFloatStateOf(Float.NaN) }
    var showLibrary by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastHapticAt by remember { mutableLongStateOf(0L) }

    val templates = remember { PoseTemplateCatalog.load(context) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val landmarker = remember {
        PoseLandmarkerManager(context) { result ->
            val lm = result?.landmarks()?.firstOrNull()
            pose = if (lm.isNullOrEmpty()) {
                null
            } else {
                lm.mapIndexed { i, l ->
                    i to DetectedJoint(l.x(), l.y(), l.visibility().orElse(0f))
                }.toMap()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            landmarker.close()
            analyzerExecutor.shutdown()
        }
    }

    // ---------- 姿态相似度 (EMA 平滑防抖) ----------
    val aspect = if (frame.height > 0) frame.width.toFloat() / frame.height else 0.75f
    val sim = if (pose != null && selected != null) {
        CompositionEngine.similarity(pose!!, selected!!, aspect)
    } else {
        null
    }
    LaunchedEffect(sim?.score) {
        val s = sim?.score ?: Float.NaN
        scoreEma = when {
            s.isNaN() || s < 0f -> Float.NaN
            scoreEma.isNaN() -> s
            else -> scoreEma + (s - scoreEma) * 0.35f
        }
    }
    val state = when {
        sim == null || scoreEma.isNaN() || scoreEma < 0f -> AlignmentState.IDLE
        scoreEma >= 0.82f -> AlignmentState.MATCHED
        scoreEma >= 0.60f -> AlignmentState.NEAR
        else -> AlignmentState.IDLE
    }

    // 对齐成功: 触觉反馈一次 (2s 冷却)
    LaunchedEffect(state) {
        if (state == AlignmentState.MATCHED) {
            val now = System.currentTimeMillis()
            if (now - lastHapticAt > 2000) {
                lastHapticAt = now
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // ---------- 引导文案 ----------
    val shot = pose?.let { CompositionEngine.detectShotType(it) }
    val personRatio = pose?.let { CompositionEngine.personHeight(it) }
    val smoothedSim = sim?.copy(score = if (scoreEma.isNaN()) sim.score else scoreEma)
    val guidance = CompositionEngine.guidance(
        personVisible = pose != null,
        shotType = shot,
        personRatio = personRatio,
        template = selected,
        sim = smoothedSim,
        pitchDeg = tilt.pitchDeg,
    )

    // ---------- 物理倾角传感器 ----------
    DisposableEffect(lifecycleOwner) {
        val sensor = TiltSensor(context) { tilt = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor.start()
                Lifecycle.Event.ON_PAUSE -> sensor.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensor.stop()
        }
    }

    // ---------- 相机绑定 ----------
    LaunchedEffect(hasCamera) {
        if (!hasCamera) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                var reuseRaw: Bitmap? = null
                var reuseRot: Bitmap? = null
                var lastTs = 0L
                analysis.setAnalyzer(analyzerExecutor) { proxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastTs < FRAME_INTERVAL_MS) return@setAnalyzer
                        lastTs = now
                        val image = proxy.image ?: return@setAnalyzer
                        val w = proxy.width
                        val h = proxy.height
                        val plane = image.planes[0]
                        val pixelStride = plane.pixelStride

                        val raw = reuseRaw?.takeIf { it.width == w && it.height == h }
                            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                .also { reuseRaw = it }
                        if (pixelStride * w == plane.rowStride) {
                            val buf = plane.buffer
                            buf.rewind()
                            raw.copyPixelsFromBuffer(buf)
                        } else {
                            // rowStride 有 padding 时逐行拷贝
                            val rowBytes = pixelStride * w
                            val out = ByteBuffer.allocate(rowBytes * h)
                            val buf = plane.buffer
                            for (row in 0 until h) {
                                buf.position(row * plane.rowStride)
                                buf.limit(row * plane.rowStride + rowBytes)
                                out.put(buf.slice())
                            }
                            out.rewind()
                            raw.copyPixelsFromBuffer(out)
                        }

                        val rotation = proxy.imageInfo.rotationDegrees
                        val rw = if (rotation == 90 || rotation == 270) h else w
                        val rh = if (rotation == 90 || rotation == 270) w else h
                        val rotated = if (rotation == 0) raw else {
                            val m = Matrix().apply { postRotate(rotation.toFloat()) }
                            val dst = reuseRot?.takeIf { it.width == rw && it.height == rh }
                                ?: Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                                    .also { reuseRot = it }
                            dst.eraseColor(android.graphics.Color.TRANSPARENT)
                            Canvas(dst).drawBitmap(raw, m, null)
                            dst
                        }

                        if (frame.width != rotated.width || frame.height != rotated.height) {
                            frame = IntSize(rotated.width, rotated.height)
                        }
                        landmarker.detectAsync(BitmapImageBuilder(rotated).build(), now)
                    } catch (_: Throwable) {
                        // 单帧失败直接丢弃, 不影响预览
                    } finally {
                        proxy.close()
                    }
                }

                val imgCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis, imgCapture,
                )
                capture = imgCapture
            } catch (t: Throwable) {
                toast = "相机启动失败: ${t.message ?: t.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ---------- 拍照 ----------
    fun takePhoto() {
        val imgCapture = capture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "SnapGuide_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SnapGuide")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            toast = "创建相册文件失败"
            return
        }
        val opts = ImageCapture.OutputFileOptions.Builder(resolver, uri, values).build()
        imgCapture.takePicture(
            opts,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    toast = "已保存到相册"
                }

                override fun onError(exc: ImageCaptureException) {
                    resolver.delete(uri, null, null)
                    toast = "保存失败: ${exc.message ?: exc.javaClass.simpleName}"
                }
            },
        )
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            toast = null
        }
    }

    // ---------- UI ----------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // 三分线 + 水平仪 + 骨架叠加
        GridThirds(Modifier.fillMaxSize())
        PoseSkeletonOverlay(
            userJoints = pose,
            frameWidth = frame.width.toFloat(),
            frameHeight = frame.height.toFloat(),
            template = selected,
            mirrored = mirrored,
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
        LevelLineOverlay(tilt, Modifier.fillMaxSize())

        // 顶部: 水平角 + 镜像 + 姿态库
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "水平 %.1f°".format(tilt.rollDeg),
                    color = if (tilt.level) GuideColors.MATCHED else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { mirrored = !mirrored }) {
                Icon(Icons.Filled.Flip, contentDescription = "镜像", tint = Color.White)
            }
            IconButton(onClick = { showLibrary = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "姿态库", tint = Color.White)
            }
        }

        // 无权限提示
        if (!hasCamera) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text("需要相机权限才能开始构图指导", color = Color.White)
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授权相机")
                }
            }
        }

        // toast
        toast?.let { msg ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // 底部: 引导文案 + 模板栏 + 快门
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            if (guidance.isNotBlank()) {
                Surface(
                    color = when (state) {
                        AlignmentState.MATCHED -> GuideColors.MATCHED.copy(alpha = 0.92f)
                        AlignmentState.NEAR -> GuideColors.NEAR.copy(alpha = 0.88f)
                        AlignmentState.IDLE -> Color.Black.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = guidance,
                        color = if (state == AlignmentState.IDLE) Color.White else Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // 模板栏: 检测到的景别排前面
            val ordered = remember(shot, selected) {
                if (shot == null) templates
                else templates.sortedByDescending { it.shotType == shot }
            }
            TemplateCarousel(
                templates = ordered,
                selectedId = selected?.id,
                onSelect = { t ->
                    selected = if (selected?.id == t.id) null else t
                    scoreEma = Float.NaN
                },
            )

            // 快门: 对齐成功时描边变绿
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        color = if (state == AlignmentState.MATCHED) GuideColors.MATCHED else Color.White,
                        shape = CircleShape,
                    )
                    .padding(8.dp)
                    .clickable { takePhoto() },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (state == AlignmentState.MATCHED) GuideColors.MATCHED else Color.White),
                )
            }
        }

        // 姿态库抽屉
        if (showLibrary) {
            TemplateLibrarySheet(
                templates = templates,
                selectedId = selected?.id,
                onSelect = { selected = it },
                onDismiss = { showLibrary = false },
            )
        }
    }
}
