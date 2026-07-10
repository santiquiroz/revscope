package com.revscope.app.safety

import androidx.lifecycle.ViewModel
import com.revscope.core.obd.safety.CrashResponder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Bridges [CrashResponder]'s alarm state into Compose. The alarm can be triggered
 * from the background (service coroutine), so the dialog is driven reactively by
 * this state rather than by the notification's full-screen intent extra — it must
 * show up no matter how the user brings the app back to the foreground.
 */
@HiltViewModel
class CrashAlertViewModel @Inject constructor(
    private val crashResponder: CrashResponder,
) : ViewModel() {

    val alarmState: StateFlow<CrashResponder.AlarmState?> = crashResponder.alarmState

    fun confirmSafe() {
        crashResponder.cancelAlarm()
    }
}
