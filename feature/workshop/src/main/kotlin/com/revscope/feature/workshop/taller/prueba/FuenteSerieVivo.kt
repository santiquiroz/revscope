package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.telemetry.captura.VentanaCaptura
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

// Los últimos segundos de la captura en curso, por PID, para la minigráfica y el valor en vivo de la prueba.
interface FuenteSerieVivo {
    fun leer(pid: String): SerieVivo
}

class FuenteSerieCaptura @Inject constructor(private val manager: ObdSessionManager) : FuenteSerieVivo {

    private val ventana = VentanaCaptura()
    private var capturaId: String? = null

    @Synchronized
    override fun leer(pid: String): SerieVivo {
        val captura = manager.captura
        val id = captura.capturaEnMemoria()?.id ?: return emptyList()
        if (id != capturaId) {
            ventana.reiniciar()
            capturaId = id
        }
        captura.pagina(id, ventana.cursor, MAX_POR_LECTURA, pids = null)?.let(ventana::agregar)
        return ventana.series()[pid].orEmpty()
    }

    private companion object {
        const val MAX_POR_LECTURA = 20_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FuenteSerieVivoModule {
    @Binds
    abstract fun bindFuenteSerieVivo(impl: FuenteSerieCaptura): FuenteSerieVivo
}
