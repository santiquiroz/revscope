package com.revscope.core.obd.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Handles the "ESTOY BIEN" action button on the crash-alarm notification. */
@AndroidEntryPoint
class CrashCancelReceiver : BroadcastReceiver() {

    @Inject lateinit var crashResponder: CrashResponder

    override fun onReceive(context: Context, intent: Intent) {
        crashResponder.cancelAlarm()
    }
}
