package moe.antimony.hoshi.features.watch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.ui.UiText
import moe.antimony.hoshi.ui.asString
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.hoshiSingleLineTextFieldLineLimits

@Composable
fun WatchSettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WatchSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_watch),
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
                when {
                    !uiState.isLoaded -> Unit
                    uiState.settings.isLoggedIn -> JellyfinAccountCard(
                        settings = uiState.settings,
                        isBusy = uiState.isBusy,
                        onLogout = viewModel::logout,
                    )
                    else -> JellyfinLoginCard(
                        initialServerUrl = uiState.settings.serverUrl,
                        isBusy = uiState.isBusy,
                        errorMessage = uiState.errorMessage,
                        onInputChanged = viewModel::clearError,
                        onLogin = viewModel::login,
                    )
                }
            }
            if (uiState.isLoaded) {
                item {
                    WatchPlaybackCard(
                        preferences = uiState.preferences,
                        onMeteredQualityChange = viewModel::updateMeteredQuality,
                        onAudioLanguageChange = viewModel::updateAudioLanguage,
                        onSubtitleSizeChange = viewModel::updateSubtitleSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinAccountCard(
    settings: JellyfinSettings,
    isBusy: Boolean,
    onLogout: () -> Unit,
) {
    WatchSettingsCard {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            headlineContent = { Text(stringResource(R.string.watch_jellyfin_server)) },
            supportingContent = { Text(settings.serverUrl) },
        )
        HorizontalDivider()
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            headlineContent = { Text(stringResource(R.string.watch_signed_in_as_format, settings.userName)) },
            trailingContent = {
                OutlinedButton(onClick = onLogout, enabled = !isBusy) {
                    Text(stringResource(R.string.action_sign_out))
                }
            },
        )
    }
}

@Composable
private fun JellyfinLoginCard(
    initialServerUrl: String,
    isBusy: Boolean,
    errorMessage: UiText?,
    onInputChanged: () -> Unit,
    onLogin: (serverUrl: String, username: String, password: String) -> Unit,
) {
    val serverUrlState = rememberTextFieldState(initialServerUrl)
    val usernameState = rememberTextFieldState()
    // The password is kept out of saved instance state on purpose.
    val passwordState = remember { TextFieldState() }
    val submit = {
        onLogin(
            serverUrlState.text.toString(),
            usernameState.text.toString(),
            passwordState.text.toString(),
        )
    }
    val canSubmit = !isBusy && serverUrlState.text.isNotBlank() && usernameState.text.isNotBlank()

    WatchSettingsCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.watch_jellyfin_server),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                state = serverUrlState,
                label = { Text(stringResource(R.string.watch_server_url)) },
                placeholder = { Text(JellyfinSettings.DEFAULT_SERVER_URL) },
                lineLimits = hoshiSingleLineTextFieldLineLimits(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                enabled = !isBusy,
                colors = hoshiOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = usernameState,
                label = { Text(stringResource(R.string.watch_username)) },
                lineLimits = hoshiSingleLineTextFieldLineLimits(),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                enabled = !isBusy,
                colors = hoshiOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedSecureTextField(
                state = passwordState,
                label = { Text(stringResource(R.string.watch_password)) },
                textObfuscationMode = TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { if (canSubmit) submit() },
                enabled = !isBusy,
                colors = hoshiOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = submit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(stringResource(R.string.watch_sign_in))
                    }
                }
            }
        }
    }

    LaunchedEffect(serverUrlState, usernameState, passwordState) {
        snapshotFlow {
            Triple(serverUrlState.text.toString(), usernameState.text.toString(), passwordState.text.toString())
        }.collect { onInputChanged() }
    }
}

@Composable
private fun WatchPlaybackCard(
    preferences: WatchPreferences,
    onMeteredQualityChange: (WatchStreamingQuality) -> Unit,
    onAudioLanguageChange: (WatchAudioLanguage) -> Unit,
    onSubtitleSizeChange: (WatchSubtitleSize) -> Unit,
) {
    WatchSettingsCard {
        Text(
            text = stringResource(R.string.watch_playback_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        WatchChoiceRow(
            title = stringResource(R.string.watch_audio_language),
            options = WatchAudioLanguage.entries,
            selected = preferences.audioLanguage,
            label = { stringResource(it.labelRes) },
            onSelect = onAudioLanguageChange,
        )
        HorizontalDivider()
        WatchChoiceRow(
            title = stringResource(R.string.watch_subtitle_size),
            options = WatchSubtitleSize.entries,
            selected = preferences.subtitleSize,
            label = { stringResource(it.labelRes) },
            onSelect = onSubtitleSizeChange,
        )
        HorizontalDivider()
        WatchChoiceRow(
            title = stringResource(R.string.watch_metered_quality),
            supporting = stringResource(R.string.watch_metered_quality_hint),
            options = WatchStreamingQuality.entries,
            selected = preferences.meteredQuality,
            label = { stringResource(it.labelRes) },
            onSelect = onMeteredQualityChange,
        )
    }
}

@Composable
private fun <T> WatchChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    supporting: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Box {
                TextButton(onClick = { menuOpen = true }) { Text(label(selected)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(label(option)) },
                            onClick = {
                                onSelect(option)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun WatchSettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column {
            content()
        }
    }
}
