package moe.antimony.hoshi.features.manga

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.bookshelf.BookCoverSource
import moe.antimony.hoshi.features.bookshelf.MainShellLayoutSpec
import moe.antimony.hoshi.importing.DirectoryImportContent
import moe.antimony.hoshi.importing.FileImportContent
import moe.antimony.hoshi.importing.ImportFileType
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.ui.HoshiBlockingProgressOverlay
import moe.antimony.hoshi.ui.asString
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.hoshiSingleLineTextFieldLineLimits
import moe.antimony.hoshi.ui.replaceTextAndSelectStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaShelfView(
    layoutSpec: MainShellLayoutSpec,
    onOpenReader: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MangaShelfViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var contextMenuTarget by remember { mutableStateOf<MangaVolumeEntry?>(null) }
    var deleteCandidate by remember { mutableStateOf<MangaVolumeEntry?>(null) }
    var renameCandidate by remember { mutableStateOf<MangaVolumeEntry?>(null) }
    val renameTextState = rememberTextFieldState()

    // Real mokuro-processed CBZ downloads already bundle the .mokuro sidecar as a root-level
    // zip entry alongside the page subfolder and a cover image, so a single-file picker is the
    // primary flow -- also far friendlier to cloud-storage pickers (e.g. Google Drive) than
    // asking the user to multi-select two separate files from providers that handle that poorly.
    val cbzPicker = rememberLauncherForActivityResult(FileImportContent()) { uri ->
        if (uri != null) viewModel.importCbz(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(DirectoryImportContent()) { uri ->
        if (uri != null) viewModel.importFolder(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.reloadVolumes()
    }

    LaunchedEffect(uiState.openReaderVolumeId) {
        val volumeId = uiState.openReaderVolumeId ?: return@LaunchedEffect
        onOpenReader(volumeId)
        viewModel.consumeOpenReaderEvent()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.main_tab_manga)) },
                actions = {
                    IconButton(
                        onClick = { cbzPicker.launch(ImportFileType.MangaCbz.mimeTypes) },
                        enabled = !uiState.isImporting,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.manga_shelf_import_cbz))
                    }
                    IconButton(
                        onClick = { folderPicker.launch(Unit) },
                        enabled = !uiState.isImporting,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.manga_shelf_import_folder))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val contentWidthDp = layoutSpec.constrainedContentWidthDp(maxWidth.value.toInt())
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.hasLoadedVolumes && uiState.volumes.isEmpty() -> MangaEmptyShelfView(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = layoutSpec.pageHorizontalPaddingDp.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(layoutSpec.bookGridColumns(contentWidthDp)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = layoutSpec.contentMaxWidthDp.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = layoutSpec.pageHorizontalPaddingDp.dp,
                        end = layoutSpec.pageHorizontalPaddingDp.dp,
                        top = layoutSpec.bookGridTopPaddingDp.dp,
                        bottom = layoutSpec.bookGridBottomPaddingDp.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(layoutSpec.bookGridSpacingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(layoutSpec.bookGridVerticalSpacingDp.dp),
                ) {
                    items(
                        items = uiState.volumes,
                        key = { it.metadata.id },
                        contentType = { "manga-volume" },
                    ) { entry ->
                        Box {
                            MangaVolumeGridCell(
                                entry = entry,
                                progress = uiState.progressById[entry.metadata.id] ?: 0.0,
                                coverSource = uiState.coverSourcesById[entry.metadata.id],
                                onOpen = { viewModel.openVolume(entry) },
                                onLongPress = { contextMenuTarget = entry },
                            )
                            DropdownMenu(
                                expanded = contextMenuTarget?.metadata?.id == entry.metadata.id,
                                onDismissRequest = { contextMenuTarget = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        contextMenuTarget = null
                                        renameCandidate = entry
                                        renameTextState.replaceTextAndSelectStart(entry.displayTitle)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        contextMenuTarget = null
                                        deleteCandidate = entry
                                    },
                                )
                            }
                        }
                    }
                }
            }
            uiState.isImporting.let { importing ->
                if (importing) {
                    HoshiBlockingProgressOverlay(
                        message = stringResource(R.string.manga_shelf_importing),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeErrorMessage,
            title = { Text(stringResource(R.string.dialog_error_title)) },
            text = { Text(message.asString()) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeErrorMessage) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.bookshelf_delete_book_title_format, candidate.displayTitle)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVolume(candidate)
                        deleteCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    renameCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    state = renameTextState,
                    label = { Text(stringResource(R.string.bookshelf_title_label)) },
                    lineLimits = hoshiSingleLineTextFieldLineLimits(),
                    colors = hoshiOutlinedTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameVolume(candidate, renameTextState.text.toString())
                        renameCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MangaEmptyShelfView(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.manga_shelf_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.manga_shelf_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MangaVolumeGridCell(
    entry: MangaVolumeEntry,
    progress: Double,
    coverSource: BookCoverSource?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        val outerShape = RoundedCornerShape(7.dp)
        val innerShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.709f)
                .clip(outerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), outerShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            val coverModifier = Modifier.fillMaxSize().clip(innerShape)
            if (coverSource != null) {
                AsyncImage(
                    model = coverSource,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier.background(Color.Gray.copy(alpha = 0.3f)),
                )
            } else {
                Box(modifier = coverModifier.background(Color.Gray.copy(alpha = 0.3f)))
            }
        }
        if (progress > 0.0) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        Text(
            text = entry.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
