package com.music.streamx.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.data.extensions.ExtensionRepository
import com.music.streamx.data.extensions.ExtensionRepositoryLinks
import com.music.streamx.ui.components.AlertAction
import com.music.streamx.ui.components.AlertRule
import com.music.streamx.ui.components.AlertScaffold
import com.music.streamx.ui.components.EditorField
import com.music.streamx.ui.components.ServerEditorAlert
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExtensionRepositoriesDialog(hazeState: HazeState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repositories by remember(context) { ExtensionRepositoryLinks.observe(context) }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(repositories.isEmpty()) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsGood by remember { mutableStateOf(false) }
    fun closeEditor() {
        if (repositories.isEmpty()) onDismiss() else adding = false
    }
    BackHandler {
        if (!busy) {
            if (adding) closeEditor() else onDismiss()
        }
    }
    fun checkRepository(save: Boolean) {
        scope.launch {
            busy = true; status = null
            try {
                val repository = ExtensionRepository.inspectLink(url)
                check(repositories.none { it.url == repository.url }) { "This repository has already been added" }
                if (save) {
                    withContext(Dispatchers.IO) { ExtensionRepositoryLinks.add(context, repository) }
                    onDismiss()
                } else {
                    status = "${context.getString(R.string.connected)} · ${repository.name}"
                    statusIsGood = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                status = failure.message ?: "Could not add repository"
                statusIsGood = false
            } finally { busy = false }
        }
    }
    if (adding) {
        ServerEditorAlert(
            hazeState = hazeState,
            title = "Add repository",
            description = "Use a GitHub repository or index.json link for StreamX plugins and skins.",
            fields = listOf(EditorField(url, { url = it; status = null },
                "https://github.com/owner/repository", KeyboardType.Uri)),
            status = status, statusIsGood = statusIsGood, testing = busy,
            canSubmit = url.isNotBlank(),
            onTest = { checkRepository(save = false) },
            onSave = { checkRepository(save = true) },
            onDismiss = ::closeEditor,
        )
        return
    }
    AlertScaffold(hazeState, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Extension repositories",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                repositories.forEach { repository ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(repository.name, style = MaterialTheme.typography.bodyMedium)
                            Text(repository.url, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            try { ExtensionRepositoryLinks.remove(context, repository.url) }
                            catch (failure: Exception) { status = failure.message; statusIsGood = false }
                        }) { Icon(Icons.Rounded.DeleteOutline, "Remove ${repository.name}") }
                    }
                }
            }
            status?.let { Text(it, color = if (statusIsGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        }
        AlertRule()
        AlertAction("Add repository", emphasised = true, onClick = { adding = true; url = ""; status = null })
        AlertRule()
        AlertAction(stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}
