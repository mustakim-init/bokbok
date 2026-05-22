package com.mustakim.bokbok.music.ui.dialogs
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mustakim.bokbok.core.R
import com.mustakim.bokbok.music.ui.component.PlaylistThumbnail
import com.mustakim.bokbok.ui.shared.DefaultDialog

@Composable
fun EditPlaylistDialog(
    initialName: String,
    initialThumbnailUrl: String?,
    fallbackThumbnails: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, thumbnailUrl: String?) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    var thumbnailUrl by rememberSaveable { mutableStateOf(initialThumbnailUrl) }

    val previewThumbnails by remember(thumbnailUrl, fallbackThumbnails) {
        derivedStateOf {
            val custom = thumbnailUrl
            if (!custom.isNullOrBlank()) listOf(custom) else fallbackThumbnails
        }
    }

    fun releasePersistablePermissionIfPossible(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        if (uri.scheme != "content") return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val old = thumbnailUrl
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (old != null && old != uri.toString()) {
                releasePersistablePermissionIfPossible(old)
            }
            thumbnailUrl = uri.toString()
        }

    val canSave by remember {
        derivedStateOf { nameField.text.isNotBlank() }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painter = painterResource(CoreR.drawable.edit), contentDescription = null) },
        title = { Text(text = "Edit Playlist") },
        contentScrollable = true,
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(android.R.string.cancel))
            }
            androidx.compose.material3.FilledTonalButton(
                enabled = canSave,
                onClick = {
                    keyboardController?.hide()
                    onSave(nameField.text.trim(), thumbnailUrl?.takeUnless { it.isBlank() })
                    onDismiss()
                },
            ) {
                Text(text = "Save")
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(modifier = Modifier.size(140.dp)) {
                val thumbnailSize = maxWidth
                val badgeSize = (thumbnailSize * 0.34f).coerceIn(36.dp, 48.dp)
                val badgePadding = (thumbnailSize * 0.06f).coerceIn(4.dp, 10.dp)
                val iconSize = (badgeSize * 0.46f).coerceIn(18.dp, 24.dp)

                PlaylistThumbnail(
                    thumbnails = previewThumbnails,
                    size = thumbnailSize,
                    placeHolder = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.queue_music),
                                contentDescription = null,
                                tint = LocalContentColor.current.copy(alpha = 0.8f),
                                modifier = Modifier.size(thumbnailSize / 2),
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                )

                Surface(
                    onClick = { pickCoverLauncher.launch(arrayOf("image/*")) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(badgePadding)
                        .size(badgeSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(CoreR.drawable.edit),
                            contentDescription = "Change Cover",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!thumbnailUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        releasePersistablePermissionIfPossible(thumbnailUrl)
                        thumbnailUrl = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = "Remove Cover")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            TextField(
                value = nameField,
                onValueChange = { nameField = it },
                placeholder = { Text(text = "Playlist Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!canSave) return@KeyboardActions
                        keyboardController?.hide()
                        onSave(nameField.text.trim(), thumbnailUrl?.takeUnless { it.isBlank() })
                        onDismiss()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
