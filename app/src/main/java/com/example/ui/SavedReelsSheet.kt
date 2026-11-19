package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ReelProject
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioPaper
import com.example.ui.theme.StudioWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedReelsSheet(
    savedReels: List<ReelProject>,
    activeProjectId: String,
    onDismiss: () -> Unit,
    onLoadReel: (ReelProject) -> Unit,
    onDuplicateReel: (ReelProject) -> Unit,
    onDeleteReel: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Saved Reels Library",
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack
            )

            if (savedReels.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No saved reels yet. Tap 'Save' in the header to store your current reel on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedReels, key = { it.id }) { reel ->
                        val isCurrent = reel.id == activeProjectId
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) StudioWhite else StudioPaper
                            ),
                            border = BorderStroke(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) InkBlack else HairlineBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = reel.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InkBlack
                                    )
                                    Text(
                                        text = "${reel.setting.label} • ${reel.shots.size} shots • ${String.format("%.1fs", reel.totalDurationMs / 1000f)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SlateMuted
                                    )
                                }

                                Button(
                                    onClick = {
                                        onLoadReel(reel)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InkBlack,
                                        contentColor = StudioWhite
                                    ),
                                    modifier = Modifier.height(42.dp).testTag("load_reel_${reel.id}")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Open", style = MaterialTheme.typography.bodySmall)
                                }

                                IconButton(
                                    onClick = { onDuplicateReel(reel) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate Reel", tint = InkBlack)
                                }

                                IconButton(
                                    onClick = { onDeleteReel(reel.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Reel", tint = SlateMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
