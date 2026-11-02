package com.revscope.core.obd.perfilcan

import com.revscope.core.obd.model.ObdReading

object FiltroFuentes {

    fun aceptaPid(r: ObdReading, res: ResolutorFuentes): Boolean = !res.usaBus(r.pid)

    fun aceptaBus(r: ObdReading, res: ResolutorFuentes): Boolean = res.usaBus(r.pid)

    fun aceptaDerivada(r: ObdReading, res: ResolutorFuentes): Boolean = !res.usaBus(r.pid)
}
