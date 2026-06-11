package com.dean.navplayer.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dean.navplayer.data.NavPlayerPrefs
import com.dean.navplayer.ui.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        if (!NavPlayerPrefs(context).autoStartOnBoot) return
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launch)
    }
}
