package com.music.streamx.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * Settings controls a skin can draw itself. Each is its own interface, so a skin
 * takes on exactly the controls it restyles, and one built before a control was
 * added keeps working: the host only calls a skin that says it can.
 */

/** The two- or three-way choice Settings uses for options such as output precision. */
interface SkinSegmentedControl {
    @Composable fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, enabled: Boolean, modifier: Modifier)
}

/** Every on/off switch in the app. [onCheckedChange] is null where the row itself takes the tap. */
interface SkinToggle {
    @Composable fun Toggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean, modifier: Modifier)
}

/** The slider under a Settings row, for values such as the crossfade length. */
interface SkinSlider {
    @Composable fun Slider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, modifier: Modifier)
}

/** Confirmations and short prompts. The buttons arrive as the host's own, to be laid out by the skin. */
interface SkinAlertDialog {
    @Composable fun AlertDialog(
        onDismissRequest: () -> Unit,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?,
        title: (@Composable () -> Unit)?,
        text: (@Composable () -> Unit)?,
        modifier: Modifier,
    )
}

/** One choice in a [SkinOptionSheet]: its name and the line under it. */
data class SkinOption(val label: String, val detail: String?)

/** The pick-one sheets Settings raises, such as streaming and download quality. */
interface SkinOptionSheet {
    @Composable fun OptionSheet(
        title: String,
        subtitle: String?,
        options: List<SkinOption>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        onDismissRequest: () -> Unit,
    )
}

/** The text fields inside Settings' prompts, such as a token or a sign-in. */
interface SkinTextField {
    @Composable fun TextField(value: String, onValueChange: (String) -> Unit, placeholder: String?, singleLine: Boolean, modifier: Modifier)
}
