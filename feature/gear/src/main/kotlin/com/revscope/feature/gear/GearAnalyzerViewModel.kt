package com.revscope.feature.gear

import androidx.lifecycle.ViewModel
import com.revscope.core.intelligence.IntelligenceOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GearAnalyzerViewModel @Inject constructor(
    val orchestrator: IntelligenceOrchestrator,
) : ViewModel()
