package com.revscope.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.revscope.app.navigation.RevScopeNavGraph
import com.revscope.app.ui.theme.RevScopeTheme
import com.revscope.core.obd.service.TripSummaryNotifier
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val openSessionId = intent
                .getLongExtra(TripSummaryNotifier.EXTRA_SESSION_ID, -1L)
                .takeIf { it > 0 }
            RevScopeTheme {
                RevScopeNavGraph(initialSessionId = openSessionId)
            }
        }
    }
}
