package com.revscope.core.obd.taller.modelo

object CatalogoDePrueba {

    const val CLAVE_BENELLI = "benelli-tnt150i-2022"

    fun textoReal(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(SembradorConocimiento.ASSET_CATALOGO)) {
            "${SembradorConocimiento.ASSET_CATALOGO} no está en el classpath de tests"
        }.bufferedReader().use { it.readText() }

    fun conVersion(version: Int, nombre: String = "Moto de prueba", referenciaTps: String = "111"): String = """
        {"version": 1, "modelos": [{
          "clave": "moto-prueba", "seedVersion": $version, "nombre": "$nombre", "tipo": "MOTORCYCLE",
          "ecu": null, "notas": [], "cableado": [],
          "repuestos": [{"rol": "TPS", "referencia": "$referenciaTps", "descripcion": "TPS", "tipo": "OEM",
                         "fuente": "Catálogo de prueba"}],
          "bandas": [{"clave": "TPS_CERRADO_V", "min": 0.5, "max": 0.7, "unidad": "V", "fuente": "Manual de prueba"}]
        }]}
    """.trimIndent()
}
