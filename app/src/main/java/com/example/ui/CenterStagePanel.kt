package com.example.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.canvas.StickmanRenderer
import com.example.export.ExportResult
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldSoft
import com.example.ui.theme.StudioSurfaceMuted
import com.example.ui.theme.StudioWhite
import com.example.viewmodel.InkReelStudioState
import com.example.viewmodel.StageViewMode

@Composable
fun CenterStagePanel(
    state: InkReelStudioState,
    lipAmplitude: Float,
    onTogglePlayPause: () -> Unit,
    onRestart: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCaptions: () -> Unit,
    onToggleBubbles: () -> Unit,
    onSetStageMode: (StageViewMode) -> Unit,
    onSavePngThumbnail: () -> Unit,
    onExportMp4: () -> Unit,
    onShareExport: (ExportResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = state.project.totalDurationMs.coerceAtLeast(1000L)
    val activeShotState = StickmanRenderer.resolveShotAt(state.project, state.playheadMs)

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = StudioWhite),
        border = BorderStroke(1.dp, HairlineBorder),
        shape = MaterialTheme.shapes.large
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactViewport = maxWidth < 460.dp
            val stageMaxWidth = when {
                isCompactViewport && maxHeight < 580.dp -> 156.dp
                isCompactViewport -> 182.dp
                else -> 268.dp
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (isCompactViewport) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isCompactViewport) 8.dp else 10.dp)
            ) {
            // Stage Header + Reference Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.project.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkBlack,
                        maxLines = 1
                    )
                    Text(
                        text = if (activeShotState.isOutro) {
                            "Outro CTA • ${state.project.outroText}"
                        } else {
                            "Shot ${activeShotState.shotIndex + 1}/${state.project.shots.size} • ${activeShotState.shot.framing.label}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateMuted,
                        maxLines = 1
                    )
                }

                if (state.referenceVideoUri != null) {
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = state.stageViewMode == StageViewMode.YOUR_REEL,
                            onClick = { onSetStageMode(StageViewMode.YOUR_REEL) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text("Your Reel", style = MaterialTheme.typography.bodySmall)
                        }
                        SegmentedButton(
                            selected = state.stageViewMode == StageViewMode.REFERENCE_CLIP,
                            onClick = { onSetStageMode(StageViewMode.REFERENCE_CLIP) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text("Reference", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 9:16 Vertical Stage Viewport
            Card(
                modifier = Modifier
                    .widthIn(max = stageMaxWidth)
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f),
                border = BorderStroke(2.dp, InkBlack),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = InkBlack)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.stageViewMode == StageViewMode.REFERENCE_CLIP && state.referenceVideoUri != null) {
                        val refUri = state.referenceVideoUri
                        key(refUri) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setMediaController(MediaController(ctx))
                                        setVideoURI(refUri)
                                        start()
                                    }
                                },
                                update = { _ -> },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .testTag("stage_canvas")
                        ) {
                            drawIntoCanvas { c ->
                                StickmanRenderer.renderFrame(
                                    canvas = c.nativeCanvas,
                                    width = size.width,
                                    height = size.height,
                                    project = state.project,
                                    playheadMs = state.playheadMs,
                                    lipAmplitude = lipAmplitude,
                                    showCaptions = state.showCaptions,
                                    showSpeechBubbles = state.showSpeechBubbles
                                )
                            }
                        }
                    }
                }
            }

            // Download Button directly below the Video Preview
            if (state.isGeneratingReel || state.isSynthesizingAllVoices) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceMuted),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (state.isGeneratingReel) {
                                "Generating stickman video & storyboard..."
                            } else {
                                "Preparing character voices..."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = InkBlack
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = InkBlack,
                            trackColor = HairlineBorder
                        )
                    }
                }
            } else {
                val mp4 = state.lastMp4Export
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_download_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mp4 != null) StudioEmeraldSoft else StudioSurfaceMuted
                    ),
                    border = BorderStroke(
                        width = if (mp4 != null || state.hasGeneratedReel) 1.5.dp else 1.dp,
                        color = if (mp4 != null) StudioEmerald else InkBlack
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isExportingMp4) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = InkBlack,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Rendering MP4 Video... ${(state.exportProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkBlack,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { state.exportProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = InkBlack,
                                trackColor = HairlineBorder
                            )
                        } else if (mp4 != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = StudioEmerald
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Video Downloaded (${formatFileSize(mp4.fileSizeBytes)})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InkBlack
                                    )
                                    Text(
                                        text = mp4.displayPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onExportMp4,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("download_again_mp4_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Download Again")
                                }
                                Button(
                                    onClick = { onShareExport(mp4) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InkBlack,
                                        contentColor = StudioWhite
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_downloaded_mp4_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Share MP4")
                                }
                            }
                        } else {
                            Button(
                                onClick = onExportMp4,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkBlack,
                                    contentColor = StudioWhite
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("stage_download_mp4_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Download Video (${state.project.exportResolution.label} MP4)")
                            }
                        }
                    }
                }
            }

            // Scrubber + Timecode
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = (state.playheadMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { frac -> onSeekTo((frac * totalMs).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = InkBlack,
                        activeTrackColor = InkBlack,
                        inactiveTrackColor = StudioSurfaceMuted
                    ),
                    modifier = Modifier.testTag("timeline_scrubber")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimecode(state.playheadMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkBlack
                    )
                    Text(
                        text = formatTimecode(totalMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateMuted
                    )
                }
            }

            // Transport Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurfaceMuted, MaterialTheme.shapes.medium)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier.size(48.dp).testTag("restart_button")
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = "Restart Reel", tint = InkBlack)
                }

                FilledTonalIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(48.dp).testTag("play_pause_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = InkBlack,
                        contentColor = StudioWhite
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(48.dp).testTag("mute_button")
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (state.isMuted) "Unmute Audio" else "Mute Audio",
                        tint = InkBlack
                    )
                }

                IconButton(
                    onClick = onToggleCaptions,
                    modifier = Modifier.size(48.dp).testTag("captions_toggle_button")
                ) {
                    Icon(
                        imageVector = if (state.showCaptions) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                        contentDescription = "Toggle Captions",
                        tint = if (state.showCaptions) InkBlack else SlateMuted
                    )
                }

                IconButton(
                    onClick = onToggleBubbles,
                    modifier = Modifier.size(48.dp).testTag("bubbles_toggle_button")
                ) {
                    Icon(
                        imageVector = if (state.showSpeechBubbles) Icons.Filled.ChatBubble else Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Toggle Speech Bubbles",
                        tint = if (state.showSpeechBubbles) InkBlack else SlateMuted
                    )
                }

                IconButton(
                    onClick = onSavePngThumbnail,
                    modifier = Modifier.size(48.dp).testTag("save_png_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Save PNG Thumbnail",
                        tint = InkBlack
                    )
                }
            }
            }
        }
    }
}

private fun formatTimecode(ms: Long): String {
    val totalSec = ms / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format("%02d:%02d.%d", mins, secs, tenths)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format("%.1f MB", mb) else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
}
