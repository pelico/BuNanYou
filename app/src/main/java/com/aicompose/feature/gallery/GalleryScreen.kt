package com.aicompose.feature.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aicompose.core.vision.AnalyzerHolder
import com.aicompose.core.vision.CompositionResult
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<CompositionResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = loadBitmap(context, uri) ?: return@launch
            sourceBitmap = bitmap
            result = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch("image/*") }) {
                Text("选择图片")
            }
            OutlinedButton(
                onClick = {
                    val bmp = sourceBitmap ?: return@OutlinedButton
                    scope.launch {
                        isAnalyzing = true
                        result = AnalyzerHolder.get(context).analyze(bmp)
                        isAnalyzing = false
                    }
                },
                enabled = sourceBitmap != null && !isAnalyzing,
            ) {
                Text("AI 重构构图")
            }
        }

        if (isAnalyzing) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                Text("分析中…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        sourceBitmap?.let { bmp ->
            Text("原图", style = MaterialTheme.typography.titleSmall)
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height),
                    contentScale = ContentScale.Fit,
                )
                result?.bestCrop?.let { crop ->
                    CropOverlay(
                        crop = crop,
                        imageWidth = bmp.width,
                        imageHeight = bmp.height,
                    )
                }
            }

            result?.let { r ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "评分: %.4f   耗时: %d ms".format(r.score, r.inferenceMs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("重构结果", style = MaterialTheme.typography.titleSmall)
                    r.bestCrop?.let { crop ->
                        val cropped = cropBitmap(bmp, crop)
                        Image(
                            bitmap = cropped.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(crop: android.graphics.RectF, imageWidth: Int, imageHeight: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight
        val left = crop.left * scaleX
        val top = crop.top * scaleY
        val w = (crop.right - crop.left) * scaleX
        val h = (crop.bottom - crop.top) * scaleY
        drawRect(
            color = Color(0xFF0FDC78),
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
        )
    }
}

private fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    // 为避免 OOM, 先按目标尺寸解码
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val targetShortSide = 2048
    var inSample = 1
    val shortSide = minOf(bounds.outWidth, bounds.outHeight)
    while (shortSide / inSample > targetShortSide) inSample *= 2

    val opts = BitmapFactory.Options().apply {
        inSampleSize = inSample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}

private fun cropBitmap(src: Bitmap, rect: android.graphics.RectF): Bitmap {
    val x = rect.left.coerceIn(0f, src.width - 1f).toInt()
    val y = rect.top.coerceIn(0f, src.height - 1f).toInt()
    val w = (rect.width()).toInt().coerceAtLeast(1)
    val h = (rect.height()).toInt().coerceAtLeast(1)
    val cx = minOf(x, src.width - w)
    val cy = minOf(y, src.height - h)
    val cw = minOf(w, src.width - cx)
    val ch = minOf(h, src.height - cy)
    return Bitmap.createBitmap(src, cx, cy, cw, ch)
}
