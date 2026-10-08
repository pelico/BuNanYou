package com.aicompose.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aicompose.core.vision.scene.PoseCard
import com.aicompose.core.vision.scene.Scene

private val SELECTED_BORDER = Color(0xFF2BE08C)

/** 场景切换 chips */
@Composable
fun SceneChipRow(
    selected: Scene?,
    auto: Scene?,
    onSelect: (Scene?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = {
                    val autoText = auto?.let { "自动·${it.displayName}" } ?: "自动识别"
                    Text(autoText)
                },
            )
        }
        items(Scene.entries.toList(), key = { it.key }) { scene ->
            FilterChip(
                selected = selected == scene,
                onClick = { onSelect(scene) },
                label = { Text("${scene.emoji} ${scene.displayName}") },
            )
        }
    }
}

/** 某个场景下的姿势卡片列表 */
@Composable
fun PosePickerRow(
    poses: List<PoseCard>,
    selectedId: String?,
    recommendedId: String? = null,
    onSelect: (PoseCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(poses, key = { it.id }) { pose ->
            PosePickerItem(
                pose = pose,
                selected = pose.id == selectedId,
                recommended = pose.id == recommendedId,
                onClick = { onSelect(pose) },
            )
        }
    }
}

@Composable
private fun PosePickerItem(
    pose: PoseCard,
    selected: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) SELECTED_BORDER else Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            AsyncImage(
                model = "file:///android_asset/poses/${pose.id}.jpg",
                contentDescription = pose.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (recommended) {
                Text(
                    text = "AI 推荐",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopStart)
                        .padding(4.dp)
                        .background(SELECTED_BORDER, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = pose.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) SELECTED_BORDER else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}