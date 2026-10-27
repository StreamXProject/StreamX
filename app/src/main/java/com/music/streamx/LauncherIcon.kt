package com.music.streamx

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Keeps the home-screen icon present. Earlier builds let people pick another
 * icon, which switched this launcher entry off; those choices are gone, so an
 * entry left off would mean no icon at all.
 */
internal object LauncherIcon {
    fun restore(context: Context) {
        val pm = context.packageManager
        val launcher = ComponentName(context.packageName, "com.music.streamx.LauncherDefault")
        if (pm.getComponentEnabledSetting(launcher) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
    }
}
