package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.InkBlack
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioCrimson
import com.example.ui.theme.StudioWhite

@Composable
fun ApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit
) {
    var keyInput by remember(currentKey) { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioWhite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = InkBlack)
                Text(
                    text = "Your Gemini API Key",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkBlack
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your Gemini API key from Google AI Studio (aistudio.google.com). Your key is stored locally on your device and used directly for storyboards and character voices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API Key (AIza...)") },
                    placeholder = { Text("Paste your API key here") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showKey = !showKey },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide API Key" else "Show API Key"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveKey(keyInput) },
                enabled = keyInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkBlack,
                    contentColor = StudioWhite
                ),
                modifier = Modifier.testTag("save_api_key_button")
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentKey.isNotBlank()) {
                    TextButton(
                        onClick = onClearKey,
                        modifier = Modifier.testTag("clear_api_key_button")
                    ) {
                        Text("Clear", color = StudioCrimson)
                    }
                    Spacer(Modifier.size(2.dp))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_api_key_button")
                ) {
                    Text("Cancel", color = SlateMuted)
                }
            }
        }
    )
}
