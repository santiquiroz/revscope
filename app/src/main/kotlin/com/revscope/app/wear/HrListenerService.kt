package com.revscope.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.revscope.core.data.db.dao.HrDao
import com.revscope.core.data.db.entities.HrPointEntity
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.wearlink.HrPayload
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val HR_MESSAGE_PATH = "/revscope/hr"

/**
 * Receives heart-rate readings streamed from the Galaxy Watch (Data Layer) and
 * records them against the active telemetry session. Readings that arrive with
 * no session running are dropped — the watch can stream standalone but the data
 * only means something tied to a trip.
 */
@AndroidEntryPoint
class HrListenerService : WearableListenerService() {

    @Inject lateinit var hrDao: HrDao
    @Inject lateinit var sessionManager: ObdSessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HR_MESSAGE_PATH) return
        val (timestamp, bpm) = HrPayload.parse(event.data) ?: return
        val sessionId = sessionManager.currentSessionId.value ?: return

        scope.launch {
            runCatching {
                hrDao.insert(HrPointEntity(sessionId = sessionId, timestamp = timestamp, bpm = bpm))
            }.onFailure { Timber.w(it, "HrListenerService: insert failed") }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
