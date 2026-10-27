package com.music.streamx.ui.components

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import com.music.streamx.ui.skins.liquidPress
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import com.music.streamx.ui.skins.SkinIcon as Icon
import com.music.streamx.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * The pill-shaped search field used at the top of the search page — a
 * magnifier that doubles as the submit button, a hint that gives way to typed
 * text, and a clear button once there is something to clear. Reused wherever
 * else the app needs the same "search this" affordance, such as filtering the
 * settings list.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    /** What the empty field says it is for — the one part that changes per screen. */
    placeholder: String = stringResource(Res.string.search_hint),
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Both ways of saying "search this" do the same two things, so they're
    // written once here rather than twice.
    val submit = {
        onSubmit()
        focusManager.clearFocus()
    }
    // Apple Music's field is a floating glass capsule: frosted, rimmed, fully
    // round, swelling under a held finger like the rest of its glass.
    val glass = com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages
    val shape = if (glass) CircleShape else RoundedCornerShape(com.music.streamx.ui.skins.LocalSkinLayout.current.searchRadiusDp.dp)
    Row(
        modifier = modifier
            .then(if (glass) Modifier.liquidPress() else Modifier)
            .fillMaxWidth()
            // Fixed height prevents the row from growing when text is entered
            .height(if (glass) 50.dp else 46.dp)
            // Apple's field at rest is flat: one fill, no rim, no shine.
            .then(if (glass) Modifier.clip(shape).background(appleFieldFill()) else Modifier)
            .background(if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant, shape)
            // Asymmetric: the magnifier is a button now and wants a real touch
            // target, so it's given the room by pulling the field's own start
            // padding in rather than by pushing the glyph and the text along.
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The search button. It reads as one — a magnifier at the head of a
        // text field is the search affordance on every platform — and now that
        // pressing it is the only thing that runs a search, leaving it
        // decorative would mean the keyboard's own key was the single way in.
        Icon(
            Icons.Rounded.Search,
            contentDescription = stringResource(Res.string.search),
            tint = if (glass) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = query.isNotBlank(), onClick = submit)
                .padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = if (glass) MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp) else MaterialTheme.typography.bodyLarge,
                    color = if (glass) appleFieldHint() else MaterialTheme.colorScheme.onSurfaceVariant,
                    // A narrow field (the phone's, beside the account photo)
                    // must cut the hint short, not wrap it out of the pill.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        // Emptying the field is also how the recent searches are got back to,
        // so it needs to be one tap rather than a held backspace.
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        onQueryChange("")
                        // Clearing is an edit, not a dismissal: leave the user
                        // ready to immediately type their next search.
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** iOS's search field fill and its hint, measured off Apple Music: #1C1C1E / #767769 dark, #E3E3E8 / #8A8A8E light. */
@Composable
private fun appleFieldFill(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF1C1C1E) else Color(0xFFE3E3E8)

@Composable
private fun appleFieldHint(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF767769) else Color(0xFF8A8A8E)
