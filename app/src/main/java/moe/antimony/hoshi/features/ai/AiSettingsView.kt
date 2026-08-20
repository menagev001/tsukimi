package moe.antimony.hoshi.features.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.hoshiSingleLineTextFieldLineLimits
import moe.antimony.hoshi.ui.rememberSyncedTextFieldState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AiSettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var editingBaseUrl by remember { mutableStateOf(false) }
    var baseUrlInput by remember { mutableStateOf("") }
    var editingModel by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf("") }
    var editingApiKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }

    if (editingBaseUrl) {
        val baseUrlState = rememberSyncedTextFieldState(
            value = baseUrlInput,
            onValueChange = { baseUrlInput = it },
        )
        AlertDialog(
            onDismissRequest = { editingBaseUrl = false },
            title = { Text(stringResource(R.string.ai_base_url)) },
            text = {
                OutlinedTextField(
                    state = baseUrlState,
                    label = { Text(stringResource(R.string.ai_base_url)) },
                    placeholder = { Text(AiSettings.DEFAULT_BASE_URL) },
                    lineLimits = hoshiSingleLineTextFieldLineLimits(),
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateBaseUrl(baseUrlInput.trim())
                        editingBaseUrl = false
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBaseUrl = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (editingModel) {
        val modelState = rememberSyncedTextFieldState(
            value = modelInput,
            onValueChange = { modelInput = it },
        )
        AlertDialog(
            onDismissRequest = { editingModel = false },
            title = { Text(stringResource(R.string.ai_model)) },
            text = {
                OutlinedTextField(
                    state = modelState,
                    label = { Text(stringResource(R.string.ai_model)) },
                    placeholder = { Text(AiSettings.DEFAULT_MODEL) },
                    lineLimits = hoshiSingleLineTextFieldLineLimits(),
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateModel(modelInput.trim())
                        editingModel = false
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingModel = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (editingApiKey) {
        val apiKeyState = rememberSyncedTextFieldState(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
        )
        AlertDialog(
            onDismissRequest = { editingApiKey = false },
            title = { Text(stringResource(R.string.ai_api_key)) },
            text = {
                OutlinedSecureTextField(
                    state = apiKeyState,
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    textObfuscationMode = TextObfuscationMode.Hidden,
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateApiKey(apiKeyInput)
                        editingApiKey = false
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingApiKey = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_ai),
        onClose = onClose,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            item {
                AiSettingsCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.ai_base_url)) },
                        supportingContent = { Text(settings.baseUrl) },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    baseUrlInput = settings.baseUrl
                                    editingBaseUrl = true
                                },
                            ) {
                                Text(stringResource(R.string.action_edit))
                            }
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.ai_model)) },
                        supportingContent = { Text(settings.model) },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    modelInput = settings.model
                                    editingModel = true
                                },
                            ) {
                                Text(stringResource(R.string.action_edit))
                            }
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.ai_api_key)) },
                        supportingContent = {
                            Text(
                                if (settings.apiKey.isEmpty()) {
                                    stringResource(R.string.none)
                                } else {
                                    stringResource(R.string.ai_api_key_configured)
                                },
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    apiKeyInput = settings.apiKey
                                    editingApiKey = true
                                },
                            ) {
                                Text(stringResource(R.string.action_edit))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        androidx.compose.foundation.layout.Column {
            content()
        }
    }
}
