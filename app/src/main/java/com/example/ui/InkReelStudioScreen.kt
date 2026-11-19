package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.export.ExportResult
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioCrimson
import com.example.ui.theme.StudioCrimsonSoft
import com.example.ui.theme.StudioPaper
import com.example.ui.theme.StudioSurfaceMuted
import com.example.ui.theme.StudioWhite
import com.example.viewmodel.InkReelViewModel

@Composable
fun InkReelStudioScreen(
    viewModel: InkReelViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val savedReels by viewModel.savedReels.collectAsStateWithLifecycle()

    var showSavedSheet by remember { mutableStateOf(false) }
    var compactTabIndex by remember { mutableIntStateOf(0) } // Start on Create on phones so user sets key/generates right away
    var mediumPaneTab by remember { mutableIntStateOf(0) } // 0=Create, 1=Director
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    val lipAmp = viewModel.currentLipAmplitude()

    fun shareExportResult(exp: ExportResult) {
        val mime = if (exp.displayPath.endsWith(".png")) "image/png" else "video/mp4"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, exp.shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share InkReel"))
    }

    Scaffold(
        containerColor = StudioPaper,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioWhite)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val isCompactHeader = maxWidth < 680.dp

                    val headerActions: @Composable () -> Unit = {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.openApiKeyDialog() },
                                modifier = Modifier.height(42.dp).testTag("header_api_key_button")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    text = if (apiKey.isBlank()) "Set API Key" else "API Key ✓",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            OutlinedButton(
                                onClick = { showSavedSheet = true },
                                modifier = Modifier.height(42.dp).testTag("saved_reels_button")
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Reels (${savedReels.size})", style = MaterialTheme.typography.bodySmall)
                            }

                            OutlinedButton(
                                onClick = { viewModel.saveCurrentReel() },
                                enabled = !state.isSavingReel,
                                modifier = Modifier.height(42.dp).testTag("save_reel_button")
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(if (state.isSavingReel) "Saving..." else "Save", style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = { viewModel.exportMp4(context) },
                                enabled = !state.isExportingMp4,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkBlack,
                                    contentColor = StudioWhite
                                ),
                                modifier = Modifier.height(42.dp).testTag("export_mp4_button")
                            ) {
                                if (state.isExportingMp4) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = StudioWhite,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("${(state.exportProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Download MP4", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (isCompactHeader) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MovieCreation,
                                    contentDescription = "InkReel Studio",
                                    tint = InkBlack
                                )
                                Text(
                                    text = "InkReel Studio",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = InkBlack
                                )
                            }
                            headerActions()
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MovieCreation,
                                    contentDescription = "InkReel Studio",
                                    tint = InkBlack
                                )
                                Text(
                                    text = "InkReel Studio",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = InkBlack
                                )
                            }
                            headerActions()
                        }
                    }
                }

                if (state.isExportingMp4) {
                    LinearProgressIndicator(
                        progress = { state.exportProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = InkBlack,
                        trackColor = StudioSurfaceMuted
                    )
                }
                HorizontalDivider(color = HairlineBorder)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Status / Error / Share Banner
            val activeError = state.errorBanner
            val activeStatus = localToastMessage ?: state.statusBanner
            if (activeError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioCrimsonSoft),
                    border = BorderStroke(1.dp, StudioCrimson)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeError,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioCrimson,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissBanners() }) {
                            Text("Dismiss", color = StudioCrimson)
                        }
                    }
                }
            } else if (activeStatus != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioWhite),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkBlack,
                            modifier = Modifier.weight(1f)
                        )
                        state.lastExportResult?.let { exp ->
                            TextButton(onClick = { shareExportResult(exp) }) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Share", color = InkBlack)
                            }
                        }
                        TextButton(onClick = {
                            localToastMessage = null
                            viewModel.dismissBanners()
                        }) {
                            Text("OK", color = SlateMuted)
                        }
                    }
                }
            }

            // Responsive Studio Workspace (3-Column Expanded / 2-Column Medium / 3-Tab Compact)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val widthDp = maxWidth

                val leftPanel: @Composable (Modifier) -> Unit = { mod ->
                    LeftCreatePanel(
                        state = state,
                        apiKey = apiKey,
                        watermarkHandle = state.project.watermarkHandle,
                        onUpdateWatermarkHandle = viewModel::updateWatermarkUsername,
                        onOpenApiKeyDialog = viewModel::openApiKeyDialog,
                        onBrainstormHooks = viewModel::brainstormHooks,
                        onGenerateFromNiche = { niche, twist, lang, hook ->
                            viewModel.generateReelFromNiche(niche, twist, lang, hook)
                            if (widthDp < 600.dp && apiKey.isNotBlank()) compactTabIndex = 1
                        },
                        onConvertScript = { script, setting, lang ->
                            viewModel.convertScriptToReel(script, setting, lang)
                            if (widthDp < 600.dp && script.isNotBlank()) compactTabIndex = 1
                        },
                        onAnalyzeReferenceClip = { uri ->
                            viewModel.analyzeReferenceClip(context, uri)
                        },
                        modifier = mod
                    )
                }

                val centerPanel: @Composable (Modifier) -> Unit = { mod ->
                    CenterStagePanel(
                        state = state,
                        lipAmplitude = lipAmp,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onRestart = viewModel::restartPlayback,
                        onSeekTo = viewModel::seekTo,
                        onToggleMute = viewModel::toggleMute,
                        onToggleCaptions = viewModel::toggleCaptions,
                        onToggleBubbles = viewModel::toggleSpeechBubbles,
                        onSetStageMode = viewModel::setStageViewMode,
                        onSavePngThumbnail = { viewModel.saveThumbnailPng(context) },
                        onExportMp4 = { viewModel.exportMp4(context) },
                        onShareExport = { exp -> shareExportResult(exp) },
                        modifier = mod
                    )
                }

                val rightPanel: @Composable (Modifier) -> Unit = { mod ->
                    RightDirectorPanel(
                        state = state,
                        onSelectShot = viewModel::selectShot,
                        onUpdateShot = viewModel::updateShot,
                        onAddShot = viewModel::addShot,
                        onDeleteShot = viewModel::deleteShot,
                        onRevoiceShot = viewModel::revoiceShot,
                        onVoiceAllShots = viewModel::synthesizeAllSpokenShots,
                        onStartMic = viewModel::startMicRecording,
                        onStopMic = viewModel::stopMicRecording,
                        onPreviewSfx = viewModel::previewSfx,
                        onUpdateCharacter = viewModel::updateCharacter,
                        onUpdateSettings = viewModel::updateProjectSettings,
                        onCopyFeedback = { msg -> localToastMessage = msg },
                        modifier = mod
                    )
                }

                when {
                    widthDp >= 920.dp -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            leftPanel(Modifier.weight(1.05f).fillMaxHeight())
                            centerPanel(Modifier.weight(0.95f).fillMaxHeight())
                            rightPanel(Modifier.weight(1.1f).fillMaxHeight())
                        }
                    }

                    widthDp >= 600.dp -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { mediumPaneTab = 0 },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Create")
                                    }
                                    OutlinedButton(
                                        onClick = { mediumPaneTab = 1 },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Director & Cast")
                                    }
                                }
                                if (mediumPaneTab == 0) {
                                    leftPanel(Modifier.weight(1f).fillMaxWidth())
                                } else {
                                    rightPanel(Modifier.weight(1f).fillMaxWidth())
                                }
                            }
                            centerPanel(Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                when (compactTabIndex) {
                                    0 -> leftPanel(Modifier.fillMaxSize())
                                    1 -> centerPanel(Modifier.fillMaxSize())
                                    else -> rightPanel(Modifier.fillMaxSize())
                                }
                            }

                            NavigationBar(
                                containerColor = StudioWhite,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = compactTabIndex == 0,
                                    onClick = { compactTabIndex = 0 },
                                    icon = { Icon(Icons.Filled.Create, contentDescription = "Create") },
                                    label = { Text("Create") }
                                )
                                NavigationBarItem(
                                    selected = compactTabIndex == 1,
                                    onClick = { compactTabIndex = 1 },
                                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = "Stage 9:16") },
                                    label = { Text("Stage 9:16") }
                                )
                                NavigationBarItem(
                                    selected = compactTabIndex == 2,
                                    onClick = { compactTabIndex = 2 },
                                    icon = { Icon(Icons.Filled.Tune, contentDescription = "Director") },
                                    label = { Text("Director") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = apiKey,
            onDismiss = viewModel::dismissApiKeyDialog,
            onSaveKey = viewModel::saveApiKey,
            onClearKey = viewModel::clearApiKey
        )
    }

    if (showSavedSheet) {
        SavedReelsSheet(
            savedReels = savedReels,
            activeProjectId = state.project.id,
            onDismiss = { showSavedSheet = false },
            onLoadReel = { reel ->
                viewModel.loadSavedReel(reel)
                compactTabIndex = 1
            },
            onDuplicateReel = { reel -> viewModel.duplicateSavedReel(reel) },
            onDeleteReel = { id -> viewModel.deleteSavedReel(id) }
        )
    }
}
