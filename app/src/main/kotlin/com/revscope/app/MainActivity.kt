package com.revscope.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.revscope.app.navigation.RevScopeNavGraph
import com.revscope.app.ui.theme.RevScopeTheme
import com.revscope.core.obd.service.TripSummaryNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingSessionId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSessionId.value = sessionIdFrom(intent)
        setContent {
            val openSessionId by pendingSessionId.collectAsState()
            RevScopeTheme {
                RevScopeNavGraph(
                    initialSessionId = openSessionId,
                    onInitialSessionConsumed = { pendingSessionId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingSessionId.value = sessionIdFrom(intent)
    }

    private fun sessionIdFrom(intent: Intent): Long? =
        intent.getLongExtra(TripSummaryNotifier.EXTRA_SESSION_ID, -1L).takeIf { it > 0 }
}
