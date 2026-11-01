package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.lib.cheats.Cheat
import com.swordfish.lemuroid.lib.cheats.CheatCompilationResult
import com.swordfish.lemuroid.lib.cheats.CheatError
import com.swordfish.lemuroid.lib.cheats.CheatFormat
import com.swordfish.lemuroid.lib.cheats.presets.CheatPreset

@Composable
fun GameMenuCheatsScreen(viewModel: GameMenuCheatsViewModel) {
    val state = viewModel.uiState.collectAsState(initial = GameMenuCheatsViewModel.State())
    val entries = state.value.entries

    var editorVisible by remember { mutableStateOf(false) }
    var editedCheat by remember { mutableStateOf<Cheat?>(null) }
    var presetsVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (state.value.loaded && entries.isEmpty()) {
            Text(
                text = stringResource(R.string.game_menu_cheats_empty),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        entries.forEach { entry ->
            CheatRow(
                entry = entry,
                onToggle = { viewModel.setEnabled(entry.cheat.id, it) },
                onClick = {
                    editedCheat = entry.cheat
                    editorVisible = true
                },
            )
        }

        if (state.value.presets.isNotEmpty()) {
            LemuroidSettingsMenuLink(
                enabled = state.value.loaded,
                title = { Text(text = stringResource(R.string.game_menu_cheats_add_from_list)) },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.game_menu_cheats_add_from_list),
                    )
                },
                onClick = { presetsVisible = true },
            )
        }

        LemuroidSettingsMenuLink(
            enabled = state.value.loaded,
            title = { Text(text = stringResource(R.string.game_menu_cheats_add)) },
            icon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.game_menu_cheats_add),
                )
            },
            onClick = {
                editedCheat = null
                editorVisible = true
            },
        )
    }

    if (presetsVisible) {
        CheatPresetsDialog(
            presets = state.value.presets,
            onDismiss = { presetsVisible = false },
            onAdd = {
                viewModel.addPresets(it)
                presetsVisible = false
            },
        )
    }

    if (editorVisible) {
        CheatEditorDialog(
            cheat = editedCheat,
            defaultName = stringResource(R.string.game_menu_cheats_default_name, entries.size + 1),
            formats = viewModel.supportedFormats,
            validate = viewModel::validate,
            onDismiss = { editorVisible = false },
            onSave = {
                viewModel.save(it)
                editorVisible = false
            },
            onDelete = {
                viewModel.delete(it)
                editorVisible = false
            },
        )
    }
}

@Composable
private fun CheatPresetsDialog(
    presets: List<GameMenuCheatsViewModel.PresetEntry>,
    onDismiss: () -> Unit,
    onAdd: (List<CheatPreset>) -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<CheatPreset>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.game_menu_cheats_presets_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.game_menu_cheats_presets_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                presets.forEach { entry ->
                    val isChecked = entry.alreadyAdded || entry.preset in selected.value
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !entry.alreadyAdded) {
                                    selected.value =
                                        if (entry.preset in selected.value) {
                                            selected.value - entry.preset
                                        } else {
                                            selected.value + entry.preset
                                        }
                                }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            enabled = !entry.alreadyAdded,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "[${entry.preset.region}] ${entry.preset.name}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text =
                                    if (entry.alreadyAdded) {
                                        stringResource(R.string.game_menu_cheats_presets_added)
                                    } else {
                                        entry.preset.notes
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.value.isNotEmpty(),
                onClick = { onAdd(presets.map { it.preset }.filter { it in selected.value }) },
            ) {
                Text(text = stringResource(R.string.game_menu_cheats_presets_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.game_menu_cheats_cancel))
            }
        },
    )
}

@Composable
private fun CheatRow(
    entry: GameMenuCheatsViewModel.Entry,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val isValid = entry.error == null

    LemuroidSettingsMenuLink(
        title = {
            Text(
                text = entry.cheat.name.ifBlank { stringResource(R.string.game_menu_cheats) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = {
            if (isValid) {
                Text(
                    text = codePreview(entry.cheat.code),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    text = stringResource(R.string.game_menu_cheats_invalid_code),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        action = {
            Switch(
                checked = entry.cheat.enabled && isValid,
                onCheckedChange = onToggle,
                enabled = isValid,
            )
        },
        onClick = onClick,
    )
}

private fun codePreview(code: String): String {
    val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return when (lines.size) {
        0 -> ""
        1 -> lines.first()
        else -> "${lines.first()} (+${lines.size - 1})"
    }
}

@Composable
private fun CheatEditorDialog(
    cheat: Cheat?,
    defaultName: String,
    formats: List<CheatFormat>,
    validate: (String, CheatFormat) -> CheatCompilationResult,
    onDismiss: () -> Unit,
    onSave: (Cheat) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(cheat?.name ?: "") }
    var code by remember { mutableStateOf(cheat?.code ?: "") }
    var format by remember { mutableStateOf(cheat?.format ?: CheatFormat.AUTO) }

    val validation = remember(code, format) { validate(code, format) }
    val error = (validation as? CheatCompilationResult.Failure)?.error
    val canSave = validation is CheatCompilationResult.Success

    val hint = stringResource(R.string.game_menu_cheats_code_hint)
    val supportingMessage =
        if (code.isBlank()) {
            hint
        } else {
            errorMessage(error) ?: hint
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val titleId =
                if (cheat == null) {
                    R.string.game_menu_cheats_dialog_add
                } else {
                    R.string.game_menu_cheats_dialog_edit
                }
            Text(text = stringResource(titleId))
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.game_menu_cheats_name)) },
                    placeholder = { Text(text = defaultName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(text = stringResource(R.string.game_menu_cheats_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    isError = code.isNotBlank() && error != null,
                    supportingText = { Text(text = supportingMessage) },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrect = false,
                        ),
                )

                Text(
                    text = stringResource(R.string.game_menu_cheats_format),
                    style = MaterialTheme.typography.labelLarge,
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    formats.forEach { entry ->
                        FilterChip(
                            selected = format == entry,
                            onClick = { format = entry },
                            label = { Text(text = stringResource(entry.labelId())) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val cleanName = name.trim().ifBlank { defaultName }
                    val cleanCode = code.trim()
                    val result =
                        cheat?.copy(name = cleanName, code = cleanCode, format = format)
                            ?: Cheat(name = cleanName, code = cleanCode, format = format, enabled = true)
                    onSave(result)
                },
            ) {
                Text(text = stringResource(R.string.game_menu_cheats_save))
            }
        },
        dismissButton = {
            Row {
                if (cheat != null) {
                    TextButton(
                        onClick = { onDelete(cheat.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(text = stringResource(R.string.game_menu_cheats_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.game_menu_cheats_cancel))
                }
            }
        },
    )
}

@Composable
private fun errorMessage(error: CheatError?): String? {
    return when (error) {
        null -> null
        is CheatError.Empty -> stringResource(R.string.game_menu_cheats_error_empty)
        is CheatError.InvalidLine ->
            stringResource(R.string.game_menu_cheats_error_invalid_line, error.lineNumber, error.line)
        is CheatError.FormatMismatch ->
            stringResource(
                R.string.game_menu_cheats_error_format_mismatch,
                error.lineNumber,
                stringResource(error.expected.labelId()),
            )
        is CheatError.TooLong -> stringResource(R.string.game_menu_cheats_error_too_long, error.maxLines)
    }
}

private fun CheatFormat.labelId(): Int {
    return when (this) {
        CheatFormat.AUTO -> R.string.game_menu_cheats_format_auto
        CheatFormat.GAMESHARK -> R.string.game_menu_cheats_format_gameshark
        CheatFormat.CODEBREAKER -> R.string.game_menu_cheats_format_codebreaker
        CheatFormat.RAW -> R.string.game_menu_cheats_format_raw
        CheatFormat.ACTION_REPLAY_DS -> R.string.game_menu_cheats_format_action_replay_ds
    }
}
