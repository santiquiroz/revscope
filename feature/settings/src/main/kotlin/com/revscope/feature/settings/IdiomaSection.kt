package com.revscope.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.RevScopeColors

@Composable
fun IdiomaSection(tagActual: String, onElegir: (String) -> Unit) {
    val opciones = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_english),
        "en-GB" to stringResource(R.string.settings_language_english_uk),
        "es" to stringResource(R.string.settings_language_spanish),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEach { (tag, label) ->
            val seleccionado = tag == tagActual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !seleccionado) {
                        if (!seleccionado) onElegir(tag)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = seleccionado,
                    onClick = {
                        if (!seleccionado) onElegir(tag)
                    },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = RevScopeColors.Accent,
                        unselectedColor = RevScopeColors.TextSecondary,
                    ),
                )
                Text(
                    text = label,
                    color = RevScopeColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
