package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.example.model.ClonedStyleBlueprint
import com.example.model.NichePreset
import com.example.model.ReelLanguage
import com.example.model.RoomSetting
import com.example.model.SampleScripts
import com.example.model.TwistStructure
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioPaper
import com.example.ui.theme.StudioSurfaceMuted
import com.example.ui.theme.StudioWhite
import com.example.viewmodel.InkReelStudioState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeftCreatePanel(
    state: InkReelStudioState,
    apiKey: String,
    watermarkHandle: String,
    onUpdateWatermarkHandle: (String) -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onBrainstormHooks: (niche: String, twist: String, language: String) -> Unit,
    onGenerateFromNiche: (niche: String, twist: String, language: String, hook: String) -> Unit,
    onConvertScript: (script: String, settingHint: RoomSetting?, language: String) -> Unit,
    onAnalyzeReferenceClip: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var modeIndex by remember { mutableIntStateOf(0) } // 0=Niche, 1=Script, 2=Clone

    // Niche state
    var selectedNiche by remember { mutableStateOf(NichePreset.SCHOOL_TEACHER.label) }
    var useCustomNiche by remember { mutableStateOf(false) }
    var customNicheText by remember { mutableStateOf("") }
    var selectedTwist by remember { mutableStateOf(TwistStructure.RULE_OF_3) }
    var selectedLanguage by remember { mutableStateOf(ReelLanguage.ENGLISH) }
    var hookInput by remember { mutableStateOf("") }

    // Script state
    var scriptText by remember { mutableStateOf(SampleScripts.englishSample) }
    var scriptSettingHint by remember { mutableStateOf<RoomSetting?>(null) }
    var settingMenuExpanded by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAnalyzeReferenceClip(uri)
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create Reel",
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack
            )

            if (apiKey.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceMuted),
                    border = BorderStroke(1.5.dp, InkBlack)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Gemini API Key Needed",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkBlack
                        )
                        Text(
                            text = "Paste your own Gemini API key to generate AI storyboards, hooks, and character voices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                        Button(
                            onClick = onOpenApiKeyDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkBlack,
                                contentColor = StudioWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("left_enter_api_key_button")
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Enter Gemini API Key")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApiKeyDialog() }
                        .testTag("left_change_api_key_card"),
                    colors = CardDefaults.cardColors(containerColor = StudioPaper),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = InkBlack)
                            Text(
                                text = "Gemini API Key Connected",
                                style = MaterialTheme.typography.labelLarge,
                                color = InkBlack
                            )
                        }
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelLarge,
                            color = SlateMuted
                        )
                    }
                }
            }

            // Creator Username / Watermark Input
            val wmPreview = watermarkHandle.trim().let {
                when {
                    it.isEmpty() -> "No watermark shown (leave blank to hide)"
                    it.startsWith("@") -> "Watermark on video & outro: $it"
                    else -> "Watermark on video & outro: @$it"
                }
            }
            OutlinedTextField(
                value = watermarkHandle,
                onValueChange = onUpdateWatermarkHandle,
                label = { Text("Your Username (Watermark)") },
                placeholder = { Text("@your_channel") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AlternateEmail,
                        contentDescription = null,
                        tint = InkBlack
                    )
                },
                trailingIcon = {
                    if (watermarkHandle.isNotEmpty()) {
                        IconButton(onClick = { onUpdateWatermarkHandle("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear Watermark",
                                tint = SlateMuted
                            )
                        }
                    }
                },
                supportingText = {
                    Text(
                        text = wmPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_watermark_username_input")
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf("Niche", "Script", "Clone Style")
                modes.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = modeIndex == idx,
                        onClick = { modeIndex = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                        modifier = Modifier.testTag("create_mode_${idx}")
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }

            // Shared Language Picker for Niche & Script
            if (modeIndex != 2) {
                Text("Language", style = MaterialTheme.typography.titleSmall, color = InkBlack)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReelLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang.label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InkBlack,
                                selectedLabelColor = StudioWhite
                            )
                        )
                    }
                }
            }

            when (modeIndex) {
                0 -> {
                    val effectiveNiche = if (useCustomNiche && customNicheText.isNotBlank()) {
                        customNicheText.trim()
                    } else {
                        selectedNiche
                    }

                    Text("Comedy Niche", style = MaterialTheme.typography.titleSmall, color = InkBlack)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NichePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = !useCustomNiche && selectedNiche == preset.label,
                                onClick = {
                                    useCustomNiche = false
                                    selectedNiche = preset.label
                                },
                                label = { Text(preset.label, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = InkBlack,
                                    selectedLabelColor = StudioWhite
                                )
                            )
                        }
                        FilterChip(
                            selected = useCustomNiche,
                            onClick = { useCustomNiche = true },
                            label = { Text("Custom", style = MaterialTheme.typography.bodySmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InkBlack,
                                selectedLabelColor = StudioWhite
                            )
                        )
                    }

                    if (useCustomNiche) {
                        OutlinedTextField(
                            value = customNicheText,
                            onValueChange = { customNicheText = it },
                            label = { Text("Custom Niche Topic") },
                            placeholder = { Text("e.g., Cricket Match & Relatives") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Comedy Twist Structure", style = MaterialTheme.typography.titleSmall, color = InkBlack)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TwistStructure.entries.forEach { twist ->
                            val selected = selectedTwist == twist
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTwist = twist },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) InkBlack else StudioPaper
                                ),
                                border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder)
                            ) {
                                Text(
                                    text = twist.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) StudioWhite else InkBlack,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = hookInput,
                        onValueChange = { hookInput = it },
                        label = { Text("Optional Story Hook") },
                        placeholder = { Text("Leave blank or tap Brainstorm 5 Hooks") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hook_input")
                    )

                    OutlinedButton(
                        onClick = {
                            onBrainstormHooks(effectiveNiche, selectedTwist.label, selectedLanguage.label)
                        },
                        enabled = !state.isBrainstorming && !state.isGeneratingReel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("brainstorm_hooks_button")
                    ) {
                        if (state.isBrainstorming) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Brainstorming...")
                        } else {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Brainstorm 5 Hooks")
                        }
                    }

                    if (state.brainstormedHooks.isNotEmpty()) {
                        Text(
                            text = "Tap a hook to load:",
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateMuted
                        )
                        state.brainstormedHooks.forEach { idea ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hookInput = idea },
                                colors = CardDefaults.cardColors(containerColor = StudioSurfaceMuted),
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Text(
                                    text = idea,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkBlack,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    state.clonedStyleBlueprint?.let { bp ->
                        ClonedBlueprintCard(blueprint = bp)
                    }

                    Button(
                        onClick = {
                            onGenerateFromNiche(
                                effectiveNiche,
                                selectedTwist.label,
                                selectedLanguage.label,
                                hookInput
                            )
                        },
                        enabled = !state.isGeneratingReel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_niche_reel_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = StudioWhite)
                    ) {
                        if (state.isGeneratingReel) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = StudioWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Generating Reel...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Generate Stickman Reel")
                        }
                    }
                }

                1 -> {
                    Text(
                        text = "Supports [Next Day] / [Day 3] tags. Preserves every script line (works with Gemini AI or instant offline parser):",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scriptText = SampleScripts.englishSample
                                selectedLanguage = ReelLanguage.ENGLISH
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("English Sample", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                scriptText = SampleScripts.hinglishSample
                                selectedLanguage = ReelLanguage.HINGLISH
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hinglish Sample", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = settingMenuExpanded,
                        onExpandedChange = { settingMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = scriptSettingHint?.label ?: "Auto-Detect Room Setting",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Scene Setting") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = settingMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = settingMenuExpanded,
                            onDismissRequest = { settingMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auto-Detect Room Setting") },
                                onClick = {
                                    scriptSettingHint = null
                                    settingMenuExpanded = false
                                }
                            )
                            RoomSetting.entries.forEach { room ->
                                DropdownMenuItem(
                                    text = { Text(room.label) },
                                    onClick = {
                                        scriptSettingHint = room
                                        settingMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = scriptText,
                        onValueChange = { scriptText = it },
                        label = { Text("Dialogue Script") },
                        minLines = 7,
                        maxLines = 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("script_input")
                    )

                    Button(
                        onClick = {
                            onConvertScript(scriptText, scriptSettingHint, selectedLanguage.label)
                        },
                        enabled = !state.isGeneratingReel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("convert_script_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = StudioWhite)
                    ) {
                        if (state.isGeneratingReel) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = StudioWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Converting Script...")
                        } else {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Convert Script to Reel")
                        }
                    }
                }

                2 -> {
                    Text(
                        text = "Pick any short comedy video (MP4/WebM) to extract its editing rhythm, shot cadence, and comedy formula with Gemini multimodal AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )

                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        enabled = !state.isAnalyzingStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pick_reference_video_button")
                    ) {
                        if (state.isAnalyzingStyle) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Analyzing Video Rhythm...")
                        } else {
                            Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Select Reference Video")
                        }
                    }

                    if (state.referenceVideoUri != null) {
                        Text(
                            text = "Reference clip attached. Switch to 'Reference Clip' on the 9:16 Stage to compare side-by-side.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkBlack
                        )
                    }

                    state.clonedStyleBlueprint?.let { bp ->
                        ClonedBlueprintCard(blueprint = bp)
                        Button(
                            onClick = { modeIndex = 0 },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = StudioWhite)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Use Blueprint in Niche Mode")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClonedBlueprintCard(blueprint: ClonedStyleBlueprint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceMuted),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Cloned Style Blueprint Active",
                style = MaterialTheme.typography.titleSmall,
                color = InkBlack
            )
            Text(
                text = "${blueprint.pacingStyle} • ~${blueprint.averageShotDurationMs}ms/cut",
                style = MaterialTheme.typography.labelMedium,
                color = SlateMuted
            )
            Text(
                text = blueprint.summary,
                style = MaterialTheme.typography.bodySmall,
                color = InkBlack
            )
            Text(
                text = "Formula: ${blueprint.comedyFormula}",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )
        }
    }
}
