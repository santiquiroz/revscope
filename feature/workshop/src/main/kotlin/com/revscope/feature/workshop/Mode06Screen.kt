package com.revscope.feature.workshop

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.protocol.Mode06Parser
import com.revscope.core.common.i18n.LocaleNumbers
import java.util.Locale


@Composable
fun Mode06Screen(
    onNavigateBack: () -> Unit,
    viewModel: Mode06ViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val state by viewModel.state.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val context = LocalContext.current
    val doneState = state as? Mode06ViewModel.UiState.Done

    LaunchedEffect(isConnected) {
        if (isConnected && state is Mode06ViewModel.UiState.Idle) viewModel.runScan()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.revscope.core.designsystem.R.string.ds_back), tint = RevScopeColors.TextPrimary)
            }
            Text(
                stringResource(R.string.workshop_mode06_title),
                color = RevScopeColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { viewModel.exportResults(context) },
                enabled = !doneState?.groups.isNullOrEmpty(),
            ) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.workshop_mode06_export_csv), tint = RevScopeColors.Accent)
            }
        }
        Text(
            stringResource(R.string.workshop_mode06_description),
            color = RevScopeColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (!isConnected) {
            Text(
                stringResource(R.string.workshop_mode06_connect_adapter),
                color = RevScopeColors.Danger,
                fontSize = 13.sp,
            )
            return@Column
        }

        Mode06Body(state, onRetry = viewModel::runScan)
    }
}

@Composable
private fun Mode06Body(state: Mode06ViewModel.UiState, onRetry: () -> Unit) {
    when (state) {
        Mode06ViewModel.UiState.Idle -> Text(stringResource(R.string.workshop_mode06_preparing), color = RevScopeColors.TextSecondary, fontSize = 13.sp)
        is Mode06ViewModel.UiState.Scanning -> ScanningProgress(state)
        is Mode06ViewModel.UiState.Done -> ResultsList(state.groups, state.incomplete, onRetry)
        is Mode06ViewModel.UiState.Error -> ErrorState(state.message, onReintentar = onRetry)
    }
}

@Composable
private fun ScanningProgress(state: Mode06ViewModel.UiState.Scanning) {
    val progress = if (state.total > 0) state.current.toFloat() / state.total else 0f
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = RevScopeColors.Accent,
        trackColor = RevScopeColors.Surface,
    )
    Text(
        stringResource(R.string.workshop_mode06_reading_progress, state.current, state.total),
        color = RevScopeColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ResultsList(groups: List<Mode06ViewModel.MidGroup>, incomplete: Boolean, onRetry: () -> Unit) {
    Column {
        if (incomplete) IncompleteScanBanner()
        if (groups.isEmpty()) {
            ErrorState(stringResource(R.string.workshop_mode06_no_results), onReintentar = onRetry)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { it.mid }) { group -> MidGroupCard(group) }
            }
        }
    }
}

@Composable
private fun IncompleteScanBanner() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = RevScopeColors.Danger.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Text(
            stringResource(R.string.workshop_mode06_scan_incomplete),
            color = RevScopeColors.Danger,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun MidGroupCard(group: Mode06ViewModel.MidGroup) {
    Surface(shape = RoundedCornerShape(12.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.workshop_mode06_group_mid, group.name, group.mid),
                color = RevScopeColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            group.results.forEach { result -> TestResultRow(result) }
        }
    }
}

@Composable
private fun TestResultRow(result: Mode06Parser.TestResult) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (result.pass) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = stringResource(
                if (result.pass) R.string.workshop_mode06_within_limits else R.string.workshop_mode06_outside_limits,
            ),
            tint = if (result.pass) RevScopeColors.Success else RevScopeColors.Danger,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    R.string.workshop_mode06_tid_uas,
                    result.tid,
                    "%02X".format(Locale.ROOT, result.uasId),
                ),
                color = RevScopeColors.TextSecondary,
                fontSize = 12.sp,
            )
            Text(
                stringResource(
                    R.string.workshop_mode06_value_unit,
                    LocaleNumbers.decimal(result.value, 2, locale),
                    result.unit,
                ),
                color = RevScopeColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(
                    R.string.workshop_mode06_limit_raw,
                    LocaleNumbers.decimal(result.min, 2, locale),
                    LocaleNumbers.decimal(result.max, 2, locale),
                    result.unit,
                    result.rawValue,
                    result.rawMin,
                    result.rawMax,
                ),
                color = RevScopeColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
