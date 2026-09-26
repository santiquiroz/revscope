package com.revscope.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable

/**
 * Barra de pestañas. Con letra al 200 % «Conducir» no cabe en un quinto de 360 dp: la etiqueta
 * se achica en una sola línea en vez de partirse.
 */
@Composable
internal fun BarraInferior(rutaActual: String?, onNavegar: (BottomNavItem) -> Unit) {
    NavigationBar(containerColor = RevScopeColors.Surface) {
        bottomNavItems.forEach { item ->
            val seleccionada = rutaActual == item.screen.route
            NavigationBarItem(
                selected = seleccionada,
                onClick = { if (!seleccionada) onNavegar(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    TextoAjustable(
                        texto = item.label,
                        estilo = RevScopeType.bodySmall,
                        color = LocalContentColor.current,
                        minimo = 7.sp,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RevScopeColors.Accent,
                    selectedTextColor = RevScopeColors.Accent,
                    unselectedIconColor = RevScopeColors.TextSecondary,
                    unselectedTextColor = RevScopeColors.TextSecondary,
                    indicatorColor = RevScopeColors.SurfaceHigh,
                ),
            )
        }
    }
}
