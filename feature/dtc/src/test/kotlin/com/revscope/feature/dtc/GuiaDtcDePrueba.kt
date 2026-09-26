package com.revscope.feature.dtc

// Guía mínima con la forma del asset real: el ViewModel se prueba sin depender del contenido completo.
val GUIA_JSON = """
    {"version": 1, "fuenteDescripciones": "SAE J2012", "aviso": "Orientación general, no es el procedimiento del fabricante",
     "codigos": [
      {"codigo": "P0122", "titulo": "Sensor de posición del acelerador «A»: circuito con señal baja",
       "sistema": "TPS", "urgencia": "REVISAR_PRONTO",
       "causas": ["Sensor dañado o no equivalente al original", "Mal contacto en el conector"],
       "verificaciones": [
         {"paso": "Confirmar que el TPS instalado es el original"},
         {"paso": "Barrido guiado del TPS", "accion": "PRUEBA:TPS_BARRIDO"},
         {"paso": "Medir el conector con el multímetro", "accion": "MULTIMETRO:TPS"},
         {"paso": "Borrar el código y reaprender el mínimo", "accion": "DTC:BORRAR"}],
       "notasMoto": [], "relacionados": ["P0123"]}
     ]}
""".trimIndent()
