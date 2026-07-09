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
        pendingSessionId.value = consumeSessionId(intent)
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
        setIntent(intent)
        pendingSessionId.value = consumeSessionId(intent)
    }

    private fun consumeSessionId(intent: Intent?): Long? {
        val id = intent?.getLongExtra(TripSummaryNotifier.EXTRA_SESSION_ID, -1L)?.takeIf { it > 0 }
        intent?.removeExtra(TripSummaryNotifier.EXTRA_SESSION_ID)
        return id
    }
}
