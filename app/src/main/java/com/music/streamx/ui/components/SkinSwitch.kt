package com.music.streamx.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.music.streamx.ui.skins.LocalSkinRenderer
import com.music.streamx.ui.skins.SkinDefaultPlayerProvider

@Composable internal fun SkinSwitch(
    checked: Boolean,onCheckedChange: ((Boolean)->Unit)?,modifier: Modifier=Modifier,
    thumbContent: (@Composable ()->Unit)?=null,enabled: Boolean=true,
    colors: SwitchColors=SwitchDefaults.colors(),interactionSource: MutableInteractionSource?=null,
) {
    // A skin that draws its own switch gets it everywhere the app shows one.
    (LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinToggle)?.let {
        it.Toggle(checked,onCheckedChange,enabled,modifier)
        return
    }
    val appleRed=Color(0xFFFA2D55)
    val resolved=if(LocalSkinRenderer.current is SkinDefaultPlayerProvider)
        colors.copy(
            checkedTrackColor=appleRed,checkedBorderColor=Color.Transparent,checkedThumbColor=Color.White,
            uncheckedBorderColor=Color.Transparent,
            disabledCheckedTrackColor=appleRed.copy(alpha=.38f),disabledCheckedBorderColor=Color.Transparent,
            disabledCheckedThumbColor=Color.White.copy(alpha=.7f),disabledUncheckedBorderColor=Color.Transparent,
        ) else colors
    androidx.compose.material3.Switch(checked,onCheckedChange,modifier,thumbContent,enabled,resolved,interactionSource)
}

/**
 * Material's alert, unless the skin draws its own. Same parameters as the ones
 * Settings passes, so a screen switches to it by import alone.
 */
@Composable internal fun SkinAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    /** Material's only; a skin's alert brings its own surface. */
    containerColor: Color = Color.Unspecified,
) {
    val skin = LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinAlertDialog
    if (skin != null) skin.AlertDialog(onDismissRequest, confirmButton, dismissButton, title, text, modifier)
    else androidx.compose.material3.AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
        modifier = modifier, dismissButton = dismissButton, title = title, text = text,
        containerColor = containerColor.takeOrElse { androidx.compose.material3.AlertDialogDefaults.containerColor })
}

/**
 * Material's outlined field, unless the skin draws its own. [labelText] is what
 * a skin is handed, as its placeholder; the field takes the same parameters
 * Settings' prompts pass, so a screen switches to it by import alone.
 */
@Composable internal fun SkinOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    labelText: String? = null,
    singleLine: Boolean = false,
) {
    val skin = LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinTextField
    if (skin != null) skin.TextField(value, onValueChange, labelText, singleLine, modifier)
    else androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier, label = label, singleLine = singleLine)
}
