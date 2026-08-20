package moe.antimony.hoshi.features.migaku

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.GroupCard
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.ui.asString

@Composable
fun MigakuExportView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MigakuExportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_migaku_export),
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
                GroupCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.settings_migaku_export)) },
                        supportingContent = { Text(stringResource(R.string.migaku_export_description)) },
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Button(
                        onClick = { exportLauncher.launch("hoshi-migaku-export.json") },
                        enabled = state !is MigakuExportState.Exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_export))
                    }
                    when (val current = state) {
                        MigakuExportState.Idle -> Unit
                        MigakuExportState.Exporting -> {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                        }
                        is MigakuExportState.Success -> {
                            Text(
                                text = stringResource(R.string.migaku_export_success_format, current.count),
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                        is MigakuExportState.Error -> {
                            Text(
                                text = current.message.asString(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
