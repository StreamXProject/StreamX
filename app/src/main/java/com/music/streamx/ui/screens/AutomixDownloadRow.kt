package com.music.streamx.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.playback.smart.AutomixEngine
import com.music.streamx.playback.smart.AutomixEngine.State

/**
 * The advanced Automix download, under the Automix switch: its size before,
 * its progress during, and Retry or Remove after. See [AutomixEngine].
 */
@Composable
internal fun AutomixDownloadRow() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AutomixEngine.attach(context)
        AutomixEngine.refreshSize()
    }
    val state by AutomixEngine.state.collectAsStateWithLifecycle()
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    val subtitle: String? = when (val current = state) {
        State.Unavailable -> stringResource(R.string.automix_engine_unavailable)
        is State.Missing -> current.bytes
            ?.let { stringResource(R.string.automix_engine_missing_size, size(it)) }
            ?: stringResource(R.string.automix_engine_missing)
        is State.Failed -> stringResource(R.string.automix_engine_failed, current.reason)
        is State.Ready -> if (current.restartPending) {
            stringResource(R.string.automix_engine_restart, size(current.bytesOnDisk))
        } else {
            stringResource(R.string.automix_engine_ready, size(current.bytesOnDisk))
        }
        is State.Downloading, State.Verifying -> null
    }
    SettingsRow(
        icon = Icons.Rounded.Download,
        title = stringResource(R.string.automix_engine),
        subtitle = subtitle,
        subtitleContent = when (val current = state) {
            is State.Downloading -> {
                {
                    Progress(
                        stringResource(R.string.automix_engine_downloading, size(current.done), size(current.total)),
                        current.done.toFloat() / current.total.coerceAtLeast(1),
                    )
                }
            }
            State.Verifying -> {
                { Progress(stringResource(R.string.automix_engine_verifying), null) }
            }
            else -> null
        },
        trailing = when (state) {
            is State.Missing -> {
                { TextButton(onClick = AutomixEngine::download) { Text(stringResource(R.string.automix_engine_download)) } }
            }
            is State.Failed -> {
                { TextButton(onClick = AutomixEngine::download) { Text(stringResource(R.string.retry)) } }
            }
            is State.Ready -> {
                { TextButton(onClick = AutomixEngine::remove) { Text(stringResource(R.string.automix_engine_remove)) } }
            }
            else -> null
        },
    )
}

@Composable
private fun Progress(label: String, fraction: Float?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = modifier)
        } else {
            LinearProgressIndicator(modifier = modifier)
        }
    }
}

/**
 * Where the Automix switch sits while its engine downloads: a ring filling
 * with the download, and a stop square inside it, as an App Store download.
 */
@Composable
internal fun AutomixDownloadProgress(state: State) {
    val fraction = (state as? State.Downloading)?.let { it.done.toFloat() / it.total.coerceAtLeast(1) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)
    val stop = stringResource(R.string.automix_engine_stop)
    androidx.compose.foundation.layout.Box(
        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = state is State.Downloading, onClickLabel = stop, onClick = AutomixEngine::cancel)
            .semantics { contentDescription = stop },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        val ring = Modifier.size(30.dp)
        if (fraction != null) {
            CircularProgressIndicator(progress = { fraction }, modifier = ring, strokeWidth = 2.5.dp, trackColor = track,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        } else {
            CircularProgressIndicator(modifier = ring, strokeWidth = 2.5.dp, trackColor = track)
        }
        if (state is State.Downloading) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            )
        }
    }
}
