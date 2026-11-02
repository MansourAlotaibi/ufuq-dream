package com.ufuq.dream

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager

/** Some TVs resolve document intents to a stub that only displays an error toast. */
internal object VideoPicker {
    internal fun usable(info: ActivityInfo): Boolean = info.enabled && info.exported &&
        !info.packageName.endsWith(".frameworkpackagestubs") &&
        info.permission.isNullOrEmpty()

    fun resolve(context: Context): Intent? {
        for(action in listOf(Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT)) {
            val intent=Intent(action).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="video/mp4"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val target=context.packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo }.firstOrNull { usable(it) } ?: continue
            // Explicit routing also avoids a default TV stub when a real picker is installed.
            return intent.setComponent(ComponentName(target.packageName,target.name))
        }
        return null
    }
}
