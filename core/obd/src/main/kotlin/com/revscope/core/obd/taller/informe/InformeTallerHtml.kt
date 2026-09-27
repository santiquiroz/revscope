package com.revscope.core.obd.taller.informe

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.sesion.Veredicto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object InformeTallerHtml {

    fun render(informe: InformeTaller): String = buildString {
        append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>").append(escapar(informe.titulo)).append(" · RevScope</title><style>")
        append(CSS)
        append("</style></head><body><main>")
        append(encabezado(informe))
        append(conclusion(informe))
        append(multimetro(informe))
        append(ecu(informe))
        append(comparacion(informe))
        append(interpretacion(informe))
        append(repuestos(informe))
        append(guia(informe))
        append(pendientes(informe))
        append(fuentes(informe))
        append("</main></body></html>")
    }

    private fun encabezado(i: InformeTaller): String = seccion("informe", "Informe de taller") {
        "<p class=\"fecha\">${e(fecha(i.generadoEn))}</p>" +
            "<h2>${e(i.titulo)}</h2>" +
            "<div class=\"ficha\">" +
            celda("Vehículo", i.vehiculo) + celda("Código leído", i.codigo) +
            celda("Síntomas", i.sintomas) + celda("Cómo se midió", i.comoSeMidio) +
            "</div>"
    }

    private fun conclusion(i: InformeTaller): String = seccion("conclusion", "Conclusión") {
        insignia(i.conclusion.veredicto) + "<p class=\"principal\">${e(i.conclusion.frase)}</p>" +
            ifVacio(i.conclusion.cables, "Sin mediciones de cable registradas.") { cables ->
                "<div class=\"mosaico\">" + cables.joinToString("") { cable ->
                    "<article class=\"cable\"><strong>${e(cable.etiqueta)}</strong><span>${e(cable.valor)}</span>${insignia(cable.veredicto)}</article>"
                } + "</div>"
            }
    }

    private fun multimetro(i: InformeTaller): String = seccion("multimetro", "Medición con multímetro") {
        ifVacio(i.mediciones, "No se registraron mediciones con multímetro.") { mediciones ->
            val pesas = mediciones.map { m ->
                PesaGrafica(m.cable, m.valor, m.bandaMin, m.bandaMax, m.unidad, origen(m.referencia))
            }
            GraficaSvg.pesas(pesas) + tabla(
                encabezados = listOf("Sensor", "Cable", "Condición", "Medido", "Estado", "Referencia y origen"),
                filas = mediciones.map { m ->
                    listOf(m.sensor, m.cable, m.condicion, valor(m.valor, m.unidad), estadoMedicion(m.estado), m.referencia)
                },
            )
        }
    }

    private fun ecu(i: InformeTaller): String = seccion("ecu", "Lo que registró la ECU") {
        val pruebas = ifVacio(i.pruebas, "No se registraron pruebas guiadas.") { lista ->
            lista.joinToString("") { prueba ->
                "<article class=\"prueba\"><h3>${e(prueba.titulo)}</h3>${insignia(prueba.veredicto)}" +
                    "<p>${e(prueba.resumen)}</p>" + pasos(prueba) +
                    prueba.series.takeIf(List<*>::isNotEmpty)?.let { GraficaSvg.lineas(it, prueba.tramos) }.orEmpty() + "</article>"
            }
        }
        pruebas + "<h3>Línea de tiempo</h3>" + lineaTiempo(i)
    }

    private fun pasos(prueba: PruebaInforme): String = ifVacio(prueba.pasos, "Sin tabla por paso.") { pasos ->
        tabla(
            encabezados = listOf("Paso", "Posición", "Voltaje aproximado"),
            filas = pasos.map { paso ->
                listOf(
                    paso.nombre,
                    paso.porcentaje?.let { "${FormatoTaller.numero(it, 1)} %" } ?: "—",
                    paso.voltios?.let { "≈${FormatoTaller.numero(it, 2)} V" } ?: "—",
                )
            },
        )
    }

    private fun lineaTiempo(i: InformeTaller): String = ifVacio(i.lineaTiempo, "La sesión no tiene eventos.") { eventos ->
        "<ol class=\"linea-tiempo\">" + eventos.joinToString("") { evento ->
            "<li><time>${e(hora(evento.instante))}</time><div><strong>${icono(evento.veredicto)} ${e(evento.titulo)}</strong>" +
                "<span>${e(evento.tipo)} · ${e(textoVeredicto(evento.veredicto))}</span><p>${e(evento.resumen)}</p></div></li>"
        } + "</ol>"
    }

    private fun comparacion(i: InformeTaller): String = seccion("comparacion", "Comparación con el chequeo anterior") {
        ifVacio(i.comparacion, "No hay un chequeo anterior comparable.") { filas ->
            tabla(
                encabezados = listOf("Métrica", "Anterior", "Ahora", "Cambio", "Referencia"),
                filas = filas.map { listOf(it.metrica.etiqueta, it.base, it.ahora, it.cambio, it.referencia ?: "—") },
            )
        }
    }

    private fun interpretacion(i: InformeTaller): String = seccion("interpretacion", "Interpretación") {
        val automatica = ifVacio(i.interpretacionAutomatica, "Sin interpretación automática adicional.") { puntos ->
            "<ul>" + puntos.joinToString("") { "<li>${e(it)}</li>" } + "</ul>"
        }
        automatica + "<h3>Interpretación del técnico</h3><p class=\"texto-libre\">${e(i.interpretacionTecnico.ifBlank { "Sin anotación del técnico." })}</p>"
    }

    private fun repuestos(i: InformeTaller): String = seccion("repuestos", "¿Es el repuesto correcto?") {
        ifVacio(i.repuestos, "No hay repuestos documentados para este modelo.") { repuestos ->
            tabla(
                encabezados = listOf("Rol", "Referencia", "Descripción", "Tipo", "Aviso", "Fuente"),
                filas = repuestos.map { listOf(it.rol, it.referencia, it.descripcion, it.tipo, it.nota, it.fuente) },
            ) + "<p class=\"aviso\">Confirma la referencia y el número de chasis antes de comprar; un genérico puede no ser equivalente.</p>"
        }
    }

    private fun guia(i: InformeTaller): String = seccion("guia", "Qué revisar, en orden") {
        ifVacio(i.pasosGuia, "No quedan pasos de la guía por marcar.") { pasos ->
            "<ol>" + pasos.joinToString("") { "<li><strong>${e(it.titulo)}</strong><p>${e(it.detalle)}</p></li>" } + "</ol>"
        }
    }

    private fun pendientes(i: InformeTaller): String = seccion("pendientes", "Pendiente por confirmar") {
        ifVacio(i.pendientes, "No hay pendientes registrados.") { pendientes ->
            "<ul>" + pendientes.joinToString("") { "<li>${e(it)}</li>" } + "</ul>"
        }
    }

    private fun fuentes(i: InformeTaller): String = seccion("fuentes", "Fuentes") {
        val lista = ifVacio(i.fuentes, "No se adjuntaron fuentes externas.") { fuentes ->
            "<ul>" + fuentes.joinToString("") { fuente -> "<li>${fuente(fuente)}</li>" } + "</ul>"
        }
        lista + "<p class=\"aviso\"><strong>Valores típicos salvo que se indique fuente.</strong> Generado con RevScope v${e(i.versionApp)}.</p>"
    }

    private fun fuente(fuente: FuenteInforme): String {
        val url = fuente.url?.takeIf(::urlSegura)
        return if (url == null) e(fuente.nombre) else "<a href=\"${atributo(url)}\">${e(fuente.nombre)}</a>"
    }

    private fun tabla(encabezados: List<String>, filas: List<List<String>>): String =
        "<div class=\"tabla-wrap\"><table><thead><tr>" + encabezados.joinToString("") { "<th>${e(it)}</th>" } +
            "</tr></thead><tbody>" + filas.joinToString("") { fila ->
            "<tr>" + fila.joinToString("") { "<td>${e(it)}</td>" } + "</tr>"
        } + "</tbody></table></div>"

    private fun celda(etiqueta: String, valor: String) =
        "<div><dt>${e(etiqueta)}</dt><dd>${e(valor)}</dd></div>"

    private fun insignia(veredicto: Veredicto) =
        "<span class=\"insignia ${veredicto.name.lowercase(Locale.ROOT)}\">${icono(veredicto)} ${e(textoVeredicto(veredicto))}</span>"

    private fun icono(veredicto: Veredicto) = when (veredicto) {
        Veredicto.OK -> "✓"
        Veredicto.ATENCION -> "⚠"
        Veredicto.FALLA -> "✕"
        Veredicto.INFO -> "ⓘ"
    }

    private fun textoVeredicto(veredicto: Veredicto) = when (veredicto) {
        Veredicto.OK -> "Dentro de referencia"
        Veredicto.ATENCION -> "Atención"
        Veredicto.FALLA -> "Fuera de referencia"
        Veredicto.INFO -> "Información"
    }

    private fun estadoMedicion(estado: String) = when (estado) {
        "DENTRO" -> "✓ Dentro"
        "BAJO" -> "✕ Bajo"
        "ALTO" -> "✕ Alto"
        else -> "ⓘ Sin referencia"
    }

    private fun origen(referencia: String): String = when {
        referencia.contains("Fuente", ignoreCase = true) -> referencia.substringAfter("Fuente", "Fuente").trim(':', ' ')
        referencia.contains("Editado por ti", ignoreCase = true) -> "Editado por ti"
        else -> "Típico"
    }

    private fun valor(valor: Double, unidad: String) =
        "${FormatoTaller.numero(valor, if (unidad == "V") 2 else 1)} $unidad".trim()

    private fun fecha(instante: Long): String = FORMATO_FECHA.format(Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault()))

    private fun hora(instante: Long): String = FORMATO_HORA.format(Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault()))

    private fun seccion(id: String, titulo: String, contenido: () -> String) =
        "<section id=\"$id\"><h1>${e(titulo)}</h1>${contenido()}</section>"

    private fun <T> ifVacio(lista: List<T>, mensaje: String, contenido: (List<T>) -> String): String =
        if (lista.isEmpty()) "<p class=\"vacio\">${e(mensaje)}</p>" else contenido(lista)

    private fun urlSegura(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun e(texto: String): String = escapar(texto)

    private fun atributo(texto: String): String = escapar(texto)

    private fun escapar(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-CO"))
    private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)

    private const val CSS = """
:root{color-scheme:light dark;--fondo:#f7f7f2;--superficie:#fff;--texto:#1d2118;--sec:#4b5343;--borde:#d8dccf;--acento:#728400;--ok:#276738;--atencion:#755b00;--falla:#9b2525}
*{box-sizing:border-box}body{margin:0;background:var(--fondo);color:var(--texto);font-family:system-ui,-apple-system,"Segoe UI",sans-serif;line-height:1.5}main{max-width:980px;margin:auto;padding:24px}section{background:var(--superficie);border:1px solid var(--borde);border-radius:14px;padding:22px;margin:0 0 18px;break-inside:avoid}h1{font-size:1.45rem;margin:0 0 16px}h2{font-size:1.8rem;margin:.25rem 0 1rem}h3{margin:1.25rem 0 .5rem}.fecha,.vacio,.aviso{color:var(--sec)}.ficha,.mosaico{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.ficha>div,.cable{border:1px solid var(--borde);border-radius:10px;padding:12px}.ficha dt{font-size:.8rem;color:var(--sec);font-weight:700}.ficha dd{margin:4px 0 0;overflow-wrap:anywhere}.principal{font-size:1.1rem}.cable{display:flex;flex-direction:column;gap:6px}.insignia{display:inline-flex;align-items:center;gap:6px;border:1px solid currentColor;border-radius:999px;padding:3px 9px;font-weight:700;font-size:.85rem}.ok{color:var(--ok)}.atencion{color:var(--atencion)}.falla{color:var(--falla)}.info{color:var(--sec)}.tabla-wrap{overflow-x:auto;margin:12px 0}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{text-align:left;vertical-align:top;border-bottom:1px solid var(--borde);padding:9px;overflow-wrap:anywhere}th{color:var(--sec);font-size:.8rem}svg{display:block;width:100%;height:auto;margin:14px 0;border:1px solid var(--borde);border-radius:10px;background:var(--superficie)}.linea-tiempo{list-style:none;padding:0}.linea-tiempo li{display:grid;grid-template-columns:80px 1fr;gap:12px;padding:10px 0;border-bottom:1px solid var(--borde)}.linea-tiempo time{font-variant-numeric:tabular-nums;color:var(--sec)}.linea-tiempo span{display:block;color:var(--sec);font-size:.85rem}.linea-tiempo p{margin:.25rem 0}.texto-libre{white-space:pre-wrap;overflow-wrap:anywhere}a{color:var(--acento);font-weight:700}
@media(max-width:520px){main{padding:10px}section{padding:16px;border-radius:10px}.ficha,.mosaico{grid-template-columns:1fr}.linea-tiempo li{grid-template-columns:64px 1fr}th,td{padding:7px}}
@media(prefers-color-scheme:dark){:root{--fondo:#11140e;--superficie:#1d2118;--texto:#f1f2e9;--sec:#c4c9b9;--borde:#464d3d;--acento:#e6f23a;--ok:#88d69b;--atencion:#f3d36a;--falla:#ff9b95}}
@media print{:root{--fondo:#fff;--superficie:#fff;--texto:#000;--sec:#333;--borde:#bbb;--acento:#435000;--ok:#155927;--atencion:#654c00;--falla:#851b1b}body{font-size:10pt}main{max-width:none;padding:0}section{border:0;border-radius:0;padding:0;margin:0 0 14mm;box-shadow:none}a{color:#000;text-decoration:none}a:after{content:" (" attr(href) ")"}svg{max-height:75mm}.tabla-wrap{overflow:visible}thead{display:table-header-group}}
"""
}
