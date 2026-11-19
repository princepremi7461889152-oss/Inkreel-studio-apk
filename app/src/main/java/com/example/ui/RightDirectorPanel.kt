package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import com.example.model.BgmMood
import com.example.model.CameraFraming
import com.example.model.CaptionFontStyle
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CharacterActivity
import com.example.model.CharacterGender
import com.example.model.CharacterProfile
import com.example.model.CharacterProp
import com.example.model.ComicSfx
import com.example.model.ExportResolution
import com.example.model.FacialHair
import com.example.model.HairStyle
import com.example.model.MemeSticker
import com.example.model.MoodPose
import com.example.model.ReelColorTheme
import com.example.model.ReelShot
import com.example.model.RoomSetting
import com.example.model.SpeakerSlot
import com.example.model.VoiceCatalog
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioCrimson
import com.example.ui.theme.StudioPaper
import com.example.ui.theme.StudioSurfaceMuted
import com.example.ui.theme.StudioWhite
import com.example.viewmodel.InkReelStudioState

@Composable
fun RightDirectorPanel(
    state: InkReelStudioState,
    onSelectShot: (String) -> Unit,
    onUpdateShot: (ReelShot) -> Unit,
    onAddShot: () -> Unit,
    onDeleteShot: (String) -> Unit,
    onRevoiceShot: (String) -> Unit,
    onVoiceAllShots: () -> Unit,
    onStartMic: (String) -> Unit,
    onStopMic: (String) -> Unit,
    onPreviewSfx: (ComicSfx) -> Unit,
    onUpdateCharacter: (Int, CharacterProfile) -> Unit,
    onUpdateSettings: (
        title: String?,
        setting: RoomSetting?,
        colorTheme: ReelColorTheme?,
        bgmMood: BgmMood?,
        bgmVolume: Float?,
        captionStyle: CaptionStyle?,
        captionFontStyle: CaptionFontStyle?,
        captionPosition: CaptionPosition?,
        introHookBanner: String?,
        enableOutro: Boolean?,
        outroText: String?,
        watermarkHandle: String?,
        exportResolution: ExportResolution?
    ) -> Unit,
    onCopyFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) } // 0=Shots, 1=Cast & Audio

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = StudioWhite),
        border = BorderStroke(1.dp, HairlineBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Shot Director",
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("director_tab_shots")
                ) {
                    Text("Shots (${state.project.shots.size})", style = MaterialTheme.typography.labelLarge)
                }
                SegmentedButton(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("director_tab_cast")
                ) {
                    Text("Cast & Style", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (tabIndex == 0) {
                ShotsDirectorTab(
                    state = state,
                    onSelectShot = onSelectShot,
                    onUpdateShot = onUpdateShot,
                    onAddShot = onAddShot,
                    onDeleteShot = onDeleteShot,
                    onRevoiceShot = onRevoiceShot,
                    onVoiceAllShots = onVoiceAllShots,
                    onStartMic = onStartMic,
                    onStopMic = onStopMic,
                    onPreviewSfx = onPreviewSfx
                )
            } else {
                CastAndAudioTab(
                    state = state,
                    onUpdateCharacter = onUpdateCharacter,
                    onUpdateSettings = onUpdateSettings,
                    onCopyFeedback = onCopyFeedback
                )
            }
        }
    }
}

@Composable
private fun ShotsDirectorTab(
    state: InkReelStudioState,
    onSelectShot: (String) -> Unit,
    onUpdateShot: (ReelShot) -> Unit,
    onAddShot: () -> Unit,
    onDeleteShot: (String) -> Unit,
    onRevoiceShot: (String) -> Unit,
    onVoiceAllShots: () -> Unit,
    onStartMic: (String) -> Unit,
    onStopMic: (String) -> Unit,
    onPreviewSfx: (ComicSfx) -> Unit
) {
    val context = LocalContext.current
    var pendingMicShotId by remember { mutableStateOf<String?>(null) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val sid = pendingMicShotId
        pendingMicShotId = null
        if (granted && sid != null) {
            onStartMic(sid)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onVoiceAllShots,
            enabled = !state.isSynthesizingAllVoices,
            modifier = Modifier.weight(1f).height(48.dp).testTag("voice_all_button"),
            colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = StudioWhite)
        ) {
            if (state.isSynthesizingAllVoices) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = StudioWhite, strokeWidth = 2.dp)
                Spacer(Modifier.size(6.dp))
                Text("Voicing...", style = MaterialTheme.typography.bodySmall)
            } else {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Voice All Shots", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(
            onClick = onAddShot,
            modifier = Modifier.height(48.dp).testTag("add_shot_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Shot", style = MaterialTheme.typography.bodySmall)
        }
    }

    // Shot Timeline Strip
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.project.shots.forEachIndexed { idx, shot ->
            val isSelected = shot.shotId == state.selectedShotId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectShot(shot.shotId) }
                    .testTag("shot_card_$idx"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) InkBlack else StudioPaper
                ),
                border = BorderStroke(1.dp, if (isSelected) InkBlack else HairlineBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${idx + 1} ${shot.speaker.label} • ${shot.framing.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) StudioWhite else InkBlack
                        )
                        Text(
                            text = shot.transitionCard.ifBlank { shot.dialogue.ifBlank { "(Silent beat)" } },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) StudioSurfaceMuted else SlateMuted,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = String.format("%.1fs", shot.durationMs / 1000f),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) StudioWhite else SlateMuted
                    )
                }
            }
        }
    }

    // Active Shot Editor
    val activeShot = state.project.shots.firstOrNull { it.shotId == state.selectedShotId }
    if (activeShot != null) {
        HorizontalDivider(color = HairlineBorder)
        Text("Edit Selected Shot", style = MaterialTheme.typography.titleSmall, color = InkBlack)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeakerSlot.entries.forEach { slot ->
                FilterChip(
                    selected = activeShot.speaker == slot,
                    onClick = { onUpdateShot(activeShot.copy(speaker = slot)) },
                    label = { Text(slot.label, style = MaterialTheme.typography.bodySmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = InkBlack,
                        selectedLabelColor = StudioWhite
                    )
                )
            }
        }

        EnumDropdown(
            label = "Camera Framing",
            selectedLabel = activeShot.framing.label,
            entries = CameraFraming.entries,
            labelOf = { it.label },
            onSelect = { onUpdateShot(activeShot.copy(framing = it)) }
        )

        EnumDropdown(
            label = "Stickman Activity / Action",
            selectedLabel = activeShot.activity.label,
            entries = CharacterActivity.entries,
            labelOf = { it.label },
            onSelect = { onUpdateShot(activeShot.copy(activity = it)) }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnumDropdown(
                label = "Expression / Pose",
                selectedLabel = activeShot.moodPose.label,
                entries = MoodPose.entries,
                labelOf = { it.label },
                onSelect = { onUpdateShot(activeShot.copy(moodPose = it)) },
                modifier = Modifier.weight(1f)
            )
            EnumDropdown(
                label = "Meme Sticker",
                selectedLabel = activeShot.sticker.label,
                entries = MemeSticker.entries,
                labelOf = { it.label },
                onSelect = { onUpdateShot(activeShot.copy(sticker = it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnumDropdown(
                label = "Comic SFX",
                selectedLabel = activeShot.sfx.label,
                entries = ComicSfx.entries,
                labelOf = { it.label },
                onSelect = { onUpdateShot(activeShot.copy(sfx = it)) },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onPreviewSfx(activeShot.sfx) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Preview SFX", tint = InkBlack)
            }
        }

        OutlinedTextField(
            value = activeShot.dialogue,
            onValueChange = { onUpdateShot(activeShot.copy(dialogue = it)) },
            label = { Text("Spoken Dialogue") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = activeShot.phoneticHint,
            onValueChange = { onUpdateShot(activeShot.copy(phoneticHint = it)) },
            label = { Text("Pronunciation Hint (Optional Hinglish Tuning)") },
            placeholder = { Text("e.g., Kyaa baat hai, ach-chaa...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = activeShot.speechBubble,
            onValueChange = { onUpdateShot(activeShot.copy(speechBubble = it)) },
            label = { Text("Speech Bubble Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (activeShot.framing == CameraFraming.TIME_SKIP_CARD || activeShot.transitionCard.isNotBlank()) {
            OutlinedTextField(
                value = activeShot.transitionCard,
                onValueChange = { onUpdateShot(activeShot.copy(transitionCard = it)) },
                label = { Text("Day / Time-Skip Flash Card") },
                placeholder = { Text("NEXT DAY") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column {
            Text(
                text = "Duration: ${String.format("%.1fs", activeShot.durationMs / 1000f)} (${activeShot.audioSourceLabel})",
                style = MaterialTheme.typography.labelMedium,
                color = SlateMuted
            )
            Slider(
                value = (activeShot.durationMs / 1000f).coerceIn(0.8f, 20.0f),
                onValueChange = { sec -> onUpdateShot(activeShot.copy(durationMs = (sec * 1000f).toLong())) },
                valueRange = 0.8f..20.0f
            )
        }

        val isVoicingThis = state.voicingShotIds.contains(activeShot.shotId)
        val isRecordingThis = state.recordingShotId == activeShot.shotId

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onRevoiceShot(activeShot.shotId) },
                enabled = !isVoicingThis && activeShot.speaker != SpeakerSlot.SILENT && activeShot.dialogue.isNotBlank(),
                modifier = Modifier.weight(1f).height(48.dp).testTag("revoice_shot_button"),
                colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = StudioWhite)
            ) {
                if (isVoicingThis) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = StudioWhite, strokeWidth = 2.dp)
                } else {
                    Text("Voice Shot", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(
                onClick = {
                    if (isRecordingThis) {
                        onStopMic(activeShot.shotId)
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onStartMic(activeShot.shotId)
                        } else {
                            pendingMicShotId = activeShot.shotId
                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("mic_dub_button")
            ) {
                Icon(
                    imageVector = if (isRecordingThis) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (isRecordingThis) StudioCrimson else InkBlack,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (isRecordingThis) "Stop Mic" else "Mic Dub",
                    color = if (isRecordingThis) StudioCrimson else InkBlack,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.project.shots.size > 1) {
                IconButton(
                    onClick = { onDeleteShot(activeShot.shotId) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Shot", tint = SlateMuted)
                }
            }
        }
    }
}

@Composable
private fun CastAndAudioTab(
    state: InkReelStudioState,
    onUpdateCharacter: (Int, CharacterProfile) -> Unit,
    onUpdateSettings: (
        title: String?,
        setting: RoomSetting?,
        colorTheme: ReelColorTheme?,
        bgmMood: BgmMood?,
        bgmVolume: Float?,
        captionStyle: CaptionStyle?,
        captionFontStyle: CaptionFontStyle?,
        captionPosition: CaptionPosition?,
        introHookBanner: String?,
        enableOutro: Boolean?,
        outroText: String?,
        watermarkHandle: String?,
        exportResolution: ExportResolution?
    ) -> Unit,
    onCopyFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val proj = state.project

    // Characters A, B, C
    proj.characters.forEachIndexed { idx, char ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StudioPaper),
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Character ${char.slot.name} — ${char.name} (${char.gender.label})",
                    style = MaterialTheme.typography.titleSmall,
                    color = InkBlack
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CharacterGender.entries.forEachIndexed { gIdx, genderOption ->
                        SegmentedButton(
                            selected = char.gender == genderOption,
                            onClick = {
                                if (char.gender != genderOption) {
                                    val updatedVoice = if (genderOption == CharacterGender.FEMALE && !VoiceCatalog.isFemaleVoice(char.voiceName)) {
                                        VoiceCatalog.defaultVoiceFor(CharacterGender.FEMALE)
                                    } else if (genderOption == CharacterGender.MALE && VoiceCatalog.isFemaleVoice(char.voiceName)) {
                                        VoiceCatalog.defaultVoiceFor(CharacterGender.MALE)
                                    } else char.voiceName

                                    val updatedHair = if (genderOption == CharacterGender.FEMALE &&
                                        char.hairStyle in listOf(HairStyle.BALD, HairStyle.SPIKY, HairStyle.PARTED, HairStyle.MESSY)
                                    ) {
                                        HairStyle.LONG_WAVY
                                    } else if (genderOption == CharacterGender.MALE &&
                                        char.hairStyle in listOf(HairStyle.LONG_WAVY, HairStyle.TWIN_BRAIDS, HairStyle.PONYTAIL, HairStyle.BOB)
                                    ) {
                                        HairStyle.PARTED
                                    } else char.hairStyle

                                    onUpdateCharacter(
                                        idx,
                                        char.copy(
                                            gender = genderOption,
                                            voiceName = updatedVoice,
                                            hairStyle = updatedHair,
                                            facialHair = if (genderOption == CharacterGender.FEMALE) FacialHair.NONE else char.facialHair
                                        )
                                    )
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(gIdx, CharacterGender.entries.size),
                            modifier = Modifier.testTag("char_${char.slot.name}_gender_${genderOption.name}")
                        ) {
                            Text(genderOption.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = char.name,
                        onValueChange = { onUpdateCharacter(idx, char.copy(name = it)) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = char.role,
                        onValueChange = { onUpdateCharacter(idx, char.copy(role = it)) },
                        label = { Text("Role") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                EnumDropdown(
                    label = "Voice Actor",
                    selectedLabel = VoiceCatalog.actors.firstOrNull { it.id == char.voiceName }
                        ?.let { "${it.displayName} (${it.tone})" } ?: char.voiceName,
                    entries = VoiceCatalog.actors,
                    labelOf = { "${it.displayName} (${it.tone})" },
                    onSelect = { onUpdateCharacter(idx, char.copy(voiceName = it.id)) }
                )
                Text(
                    text = "Voice Speed: ${String.format("%.2fx", char.voiceSpeed)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateMuted
                )
                Slider(
                    value = char.voiceSpeed,
                    onValueChange = { onUpdateCharacter(idx, char.copy(voiceSpeed = it)) },
                    valueRange = 0.85f..1.30f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnumDropdown(
                        label = "Hair",
                        selectedLabel = char.hairStyle.label,
                        entries = HairStyle.entries,
                        labelOf = { it.label },
                        onSelect = { onUpdateCharacter(idx, char.copy(hairStyle = it)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (char.gender == CharacterGender.MALE) {
                        EnumDropdown(
                            label = "Facial Hair",
                            selectedLabel = char.facialHair.label,
                            entries = FacialHair.entries,
                            labelOf = { it.label },
                            onSelect = { onUpdateCharacter(idx, char.copy(facialHair = it)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                EnumDropdown(
                    label = "Held Prop",
                    selectedLabel = char.prop.label,
                    entries = CharacterProp.entries,
                    labelOf = { it.label },
                    onSelect = { onUpdateCharacter(idx, char.copy(prop = it)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wear Glasses", style = MaterialTheme.typography.bodyMedium, color = InkBlack)
                    Switch(
                        checked = char.hasGlasses,
                        onCheckedChange = { onUpdateCharacter(idx, char.copy(hasGlasses = it)) }
                    )
                }
            }
        }
    }

    HorizontalDivider(color = HairlineBorder)
    Text("Theme, Intro/Outro & Audio", style = MaterialTheme.typography.titleSmall, color = InkBlack)

    OutlinedTextField(
        value = proj.title,
        onValueChange = { onUpdateSettings(it, null, null, null, null, null, null, null, null, null, null, null, null) },
        label = { Text("Reel Title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = proj.introHookBanner,
        onValueChange = { onUpdateSettings(null, null, null, null, null, null, null, null, it, null, null, null, null) },
        label = { Text("Top Viral Hook Banner (Optional)") },
        placeholder = { Text("WAIT FOR THE END!") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnumDropdown(
            label = "Room Setting",
            selectedLabel = proj.setting.label,
            entries = RoomSetting.entries,
            labelOf = { it.label },
            onSelect = { onUpdateSettings(null, it, null, null, null, null, null, null, null, null, null, null, null) },
            modifier = Modifier.weight(1f)
        )
        EnumDropdown(
            label = "Color Accent Theme",
            selectedLabel = proj.colorTheme.label,
            entries = ReelColorTheme.entries,
            labelOf = { it.label },
            onSelect = { onUpdateSettings(null, null, it, null, null, null, null, null, null, null, null, null, null) },
            modifier = Modifier.weight(1f)
        )
    }

    EnumDropdown(
        label = "BGM Mood (Auto-Ducks on Speech)",
        selectedLabel = proj.bgmMood.label,
        entries = BgmMood.entries,
        labelOf = { it.label },
        onSelect = { onUpdateSettings(null, null, null, it, null, null, null, null, null, null, null, null, null) }
    )

    Text(
        text = "BGM Volume: ${(proj.bgmVolume * 100).toInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = SlateMuted
    )
    Slider(
        value = proj.bgmVolume,
        onValueChange = { onUpdateSettings(null, null, null, null, it, null, null, null, null, null, null, null, null) },
        valueRange = 0f..1f
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnumDropdown(
            label = "Caption Style",
            selectedLabel = proj.captionStyle.label,
            entries = CaptionStyle.entries,
            labelOf = { it.label },
            onSelect = { onUpdateSettings(null, null, null, null, null, it, null, null, null, null, null, null, null) },
            modifier = Modifier.weight(1f)
        )
        EnumDropdown(
            label = "Caption Font",
            selectedLabel = proj.captionFontStyle.label,
            entries = CaptionFontStyle.entries,
            labelOf = { it.label },
            onSelect = { onUpdateSettings(null, null, null, null, null, null, it, null, null, null, null, null, null) },
            modifier = Modifier.weight(1f)
        )
    }

    EnumDropdown(
        label = "Caption Position",
        selectedLabel = proj.captionPosition.label,
        entries = CaptionPosition.entries,
        labelOf = { it.label },
        onSelect = { onUpdateSettings(null, null, null, null, null, null, null, it, null, null, null, null, null) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Include Animated Outro CTA Card", style = MaterialTheme.typography.bodyMedium, color = InkBlack)
        Switch(
            checked = proj.enableOutro,
            onCheckedChange = { onUpdateSettings(null, null, null, null, null, null, null, null, null, it, null, null, null) }
        )
    }

    if (proj.enableOutro) {
        OutlinedTextField(
            value = proj.outroText,
            onValueChange = { onUpdateSettings(null, null, null, null, null, null, null, null, null, null, it, null, null) },
            label = { Text("Outro CTA Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    OutlinedTextField(
        value = proj.watermarkHandle,
        onValueChange = { onUpdateSettings(null, null, null, null, null, null, null, null, null, null, null, it, null) },
        label = { Text("Channel Watermark (@handle)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    EnumDropdown(
        label = "MP4 Export Resolution",
        selectedLabel = proj.exportResolution.label,
        entries = ExportResolution.entries,
        labelOf = { it.label },
        onSelect = { onUpdateSettings(null, null, null, null, null, null, null, null, null, null, null, null, it) }
    )

    // Viral Title & Hashtags Copy Box
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceMuted),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Viral Social Caption & Hashtags", style = MaterialTheme.typography.titleSmall, color = InkBlack)
            Text(proj.socialCaption, style = MaterialTheme.typography.bodySmall, color = InkBlack)
            Text(
                text = proj.hashtags.joinToString(" "),
                style = MaterialTheme.typography.labelMedium,
                color = SlateMuted
            )
            OutlinedButton(
                onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val copyText = "${proj.title}\n\n${proj.socialCaption}\n\n${proj.hashtags.joinToString(" ")}"
                    clip.setPrimaryClip(ClipData.newPlainText("InkReel Caption", copyText))
                    onCopyFeedback("Copied viral title, caption & hashtags!")
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Copy Caption & Hashtags")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selectedLabel: String,
    entries: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(labelOf(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
