package com.revscope.core.obd.mcp.escritura

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Recibe Permitir/Rechazar (o el deslizamiento) de la notificación de confirmación. */
@AndroidEntryPoint
class ConfirmacionEscrituraReceiver : BroadcastReceiver() {

    @Inject lateinit var pendientes: ConfirmacionesPendientes
    @Inject lateinit var pedidor: PedidorConfirmacion

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getIntExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: return
        pendientes.resolver(id, intent.getBooleanExtra(EXTRA_PERMITIDO, false))
        pedidor.retirar(id)
    }

    companion object {
        const val ACTION = "com.revscope.core.obd.action.MCP_CONFIRMACION_ESCRITURA"
        const val EXTRA_ID = "id"
        const val EXTRA_PERMITIDO = "permitido"
    }
}
