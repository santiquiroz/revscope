package com.revscope.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.social.RoomClient

/**
 * Un solo banner secundario visible a la vez (fix C — "muchas cosas en pantalla se tapan entre
 * ellas"). Prioridad de campo: un mapa offline corrupto bloquea la app entera y gana siempre;
 * un error de navegación es la próxima señal más urgente (la ruta dejó de ser confiable); un
 * radar acercándose es tiempo-crítico pero acotado a esa calle; un destino compartido puede
 * esperar sin riesgo si hay algo más urgente en pantalla; la promo del tier remoto (fix W1) es
 * puramente informativa — la de MENOR prioridad, nunca debe tapar nada de lo anterior.
 */
internal sealed interface SecondaryBanner {
    data class MapCorrupted(val message: String) : SecondaryBanner
    data class NavError(val message: String) : SecondaryBanner
    data class Radar(val target: SpeedCameraAlerter.ApproachingCamera) : SecondaryBanner
    data class SharedDest(val dest: RoomClient.SharedDest) : SecondaryBanner
    data object RemoteMapPromo : SecondaryBanner
}

internal fun pickSecondaryBanner(
    mapCorruptedMessage: String?,
    navigationErrorMessage: String?,
    approachingRadar: SpeedCameraAlerter.ApproachingCamera?,
    incomingSharedDest: RoomClient.SharedDest?,
    showRemoteMapPromo: Boolean = false,
): SecondaryBanner? = when {
    mapCorruptedMessage != null -> SecondaryBanner.MapCorrupted(mapCorruptedMessage)
    navigationErrorMessage != null -> SecondaryBanner.NavError(navigationErrorMessage)
    approachingRadar != null -> SecondaryBanner.Radar(approachingRadar)
    incomingSharedDest != null -> SecondaryBanner.SharedDest(incomingSharedDest)
    showRemoteMapPromo -> SecondaryBanner.RemoteMapPromo
    else -> null
}

/** Renderiza el banner que [pickSecondaryBanner] eligió, reusando los composables ya definidos
 * en LiveMapScreen.kt para cada caso — mismo look, una sola vez cada uno. */
@Composable
internal fun SecondaryBannerContent(
    banner: SecondaryBanner,
    onDismissMapCorrupted: () -> Unit,
    onDismissNavError: () -> Unit,
    onAcceptSharedDest: (RoomClient.SharedDest) -> Unit,
    onDismissSharedDest: (RoomClient.SharedDest) -> Unit,
    onDismissRemoteMapPromo: () -> Unit = {},
    onDownloadRemoteMapPromo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (banner) {
        is SecondaryBanner.MapCorrupted -> ErrorBanner(banner.message, onDismiss = onDismissMapCorrupted, modifier = modifier)
        is SecondaryBanner.NavError -> ErrorBanner(banner.message, onDismiss = onDismissNavError, modifier = modifier)
        is SecondaryBanner.Radar -> ApproachingCameraBanner(banner.target, modifier = modifier)
        is SecondaryBanner.SharedDest -> SharedDestBanner(
            dest = banner.dest,
            onAccept = { onAcceptSharedDest(banner.dest) },
            onDismiss = { onDismissSharedDest(banner.dest) },
            modifier = modifier,
        )
        is SecondaryBanner.RemoteMapPromo -> RemoteMapPromoBanner(
            onDownload = onDownloadRemoteMapPromo,
            onDismiss = onDismissRemoteMapPromo,
            modifier = modifier,
        )
    }
}
