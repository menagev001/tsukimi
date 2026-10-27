package moe.antimony.hoshi.features.furigana

import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.importing.FileImportContent

@Composable
fun KnownWordsSettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: KnownWordsSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(FileImportContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }
            }.getOrNull()
            viewModel.importWordList(text)
        }
    }
    // Reading AnkiDroid needs the same permission as adding cards; ask for it the first time.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.syncNow()
    }
    val syncNow = {
        val granted = ContextCompat.checkSelfPermission(context, AnkiDroidReadWritePermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.syncNow() else permissionLauncher.launch(AnkiDroidReadWritePermission)
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_known_words),
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
                KnownWordsCard {
                    val counts = uiState.counts
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = {
                            Text(
                                if (counts == null) {
                                    ""
                                } else {
                                    pluralStringResource(R.plurals.known_words_total, counts.total, counts.total)
                                },
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.known_words_description))
                                if (counts != null) {
                                    Text(
                                        stringResource(
                                            R.string.known_words_breakdown,
                                            counts.imported,
                                            counts.marked,
                                            counts.anki,
                                        ),
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
            item {
                KnownWordsCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.known_words_anki_sync)) },
                        supportingContent = { Text(stringResource(R.string.known_words_anki_sync_description)) },
                        trailingContent = {
                            Switch(
                                checked = uiState.settings.ankiSyncEnabled,
                                onCheckedChange = viewModel::updateAnkiSyncEnabled,
                            )
                        },
                    )
                    if (uiState.settings.ankiSyncEnabled) {
                        HorizontalDivider()
                        val status = uiState.syncStatus
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text(ankiSyncStatusText(status, uiState.counts?.anki ?: 0)) },
                            supportingContent = uiState.settings.lastAnkiSyncMillis?.let { millis ->
                                {
                                    Text(
                                        stringResource(
                                            R.string.known_words_last_sync,
                                            DateUtils.getRelativeTimeSpanString(millis).toString(),
                                        ),
                                    )
                                }
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = syncNow,
                                    enabled = status != KnownWordsAnkiSyncStatus.Syncing,
                                ) {
                                    Text(stringResource(R.string.known_words_sync_now))
                                }
                            },
                        )
                    }
                }
            }
            item {
                KnownWordsCard {
                    val importState = uiState.importState
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.known_words_import_title)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.known_words_import_description))
                                when (importState) {
                                    is KnownWordsImportState.Imported -> Text(
                                        stringResource(
                                            R.string.known_words_import_result,
                                            importState.summary.newlyAddedCount,
                                            importState.summary.totalKnownCount,
                                        ),
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                    KnownWordsImportState.Failed -> Text(
                                        stringResource(R.string.known_words_import_error),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                    else -> Unit
                                }
                            }
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { importer.launch(arrayOf("application/json", "text/*")) },
                                enabled = importState != KnownWordsImportState.Importing,
                            ) {
                                Text(
                                    if (importState == KnownWordsImportState.Importing) {
                                        stringResource(R.string.reader_appearance_importing)
                                    } else {
                                        stringResource(R.string.action_import)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ankiSyncStatusText(status: KnownWordsAnkiSyncStatus, ankiWordCount: Int): String =
    when (status) {
        KnownWordsAnkiSyncStatus.Idle ->
            pluralStringResource(R.plurals.known_words_anki_count, ankiWordCount, ankiWordCount)
        KnownWordsAnkiSyncStatus.Syncing -> stringResource(R.string.known_words_syncing)
        is KnownWordsAnkiSyncStatus.Done ->
            pluralStringResource(R.plurals.known_words_anki_count, status.ankiWordCount, status.ankiWordCount)
        is KnownWordsAnkiSyncStatus.Failed -> stringResource(status.failure.userMessageRes)
    }

@Composable
private fun KnownWordsCard(content: @Composable () -> Unit) {
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

private const val AnkiDroidReadWritePermission = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
