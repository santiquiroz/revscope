package com.revscope.app.navigation

import androidx.navigation.NavHostController
import com.revscope.feature.workshop.taller.AccionesHub

internal fun accionesHub(nav: NavHostController): AccionesHub {
    val ir: (Screen) -> () -> Unit = { destino -> { nav.navigate(destino.navRoute) } }
    return AccionesHub(
        onIniciarDiagnostico = ir(Screen.TallerNuevaSesion),
        onAbrirSesion = { id -> nav.navigate(Screen.TallerSesion.withId(id)) },
        onConectarAdaptador = ir(Screen.AdapterScan),
        onElegirVehiculo = ir(Screen.VehicleProfile),
        onCodigos = ir(Screen.Dtc),
        onSensores = ir(Screen.Sensors),
        onChequeo = ir(Screen.HealthCheck),
        onPruebasGuiadas = ir(Screen.PruebaGuiada),
        onMultimetro = ir(Screen.Multimetro),
        onAlDia = ir(Screen.AlDia),
        onMezcla = ir(Screen.LiveMixture),
        onEscaner = ir(Screen.Mode22Scanner),
        onOndaO2 = ir(Screen.O2Wave),
        onMode06 = ir(Screen.Mode06),
        onMecanicoIa = ir(Screen.MechanicChat),
        onMarchas = ir(Screen.GearAnalyzer),
        onPerfiles = ir(Screen.VehicleProfile),
        onMantenimiento = ir(Screen.Maintenance),
        onOdometro = ir(Screen.Odometer),
        onVelocimetros = ir(Screen.SpeedComparison),
        onConocimientoModelo = ir(Screen.ConocimientoModelo),
    )
}
