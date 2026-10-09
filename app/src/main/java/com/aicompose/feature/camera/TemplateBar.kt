package com.aicompose.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicompose.core.guide.PoseTemplate
import com.aicompose.core.guide.ShotType

/** 底部横向姿势模板栏 (景别匹配的排前面) */
@Composable
fun TemplateCarousel(
    templates: List<PoseTemplate>,
    selectedId: String?,
    onSelect: (PoseTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(templates, key = { it.id }) { t ->
            TemplateCard(
                template = t,
                selected = t.id == selectedId,
                onClick = { onSelect(t) },
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: PoseTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = GuideColors.MATCHED
    val border = if (selected) accent else Color.White.copy(alpha = 0.25f)
    val skeletonColor = if (selected) accent else Color.White.copy(alpha = 0.85f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(92.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.5.dp, border, RoundedCornerShape(12.dp)),
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val side = minOf(size.width, size.height)
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                drawSkeleton(
                    joints = template.joints,
                    transform = { x, y -> androidx.compose.ui.geometry.Offset(left + x * side, top + y * side) },
                    color = skeletonColor,
                    strokeWidth = 3f,
                    dashed = false,
                    headRadius = 6f,
                )
            }
            Text(
                text = template.shotType.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
        Text(
            text = template.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 姿态库抽屉: 全部模板按景别分组 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateLibrarySheet(
    templates: List<PoseTemplate>,
    selectedId: String?,
    onSelect: (PoseTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF15181D)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            for (shot in ShotType.entries) {
                val group = templates.filter { it.shotType == shot }
                if (group.isEmpty()) continue
                item(key = "h_${shot.name}") {
                    Text(
                        text = "${shot.displayName}姿势",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                items(group, key = { it.id }) { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(t)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = t.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (t.id == selectedId) GuideColors.MATCHED else Color.White,
                            )
                            Text(
                                text = t.guideText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (t.id == selectedId) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = GuideColors.MATCHED,
                            )
                        }
                    }
                }
            }
        }
    }
}
