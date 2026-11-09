package com.revscope.core.obd.taller.informe

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.sesion.Veredicto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object InformeTallerHtml {

    fun render(informe: InformeTaller, resolver: StringResolver): String = buildString {
        append("<!doctype html><html lang=\"").append(escapar(resolver.locale.language)).append("\"><head><meta charset=\"utf-8\">") // i18n-ignore
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">") // i18n-ignore
        append("<title>").append(escapar(resolver.get(R.string.obd_report_document_title, informe.titulo))).append("</title><style>")
        append(CSS)
        append("</style></head><body><main>")
        append(encabezado(informe, resolver))
        append(conclusion(informe, resolver))
        append(multimetro(informe, resolver))
        append(ecu(informe, resolver))
        append(comparacion(informe, resolver))
        append(interpretacion(informe, resolver))
        append(repuestos(informe, resolver))
        append(guia(informe, resolver))
        append(pendientes(informe, resolver))
        append(fuentes(informe, resolver))
        append("</main></body></html>")
    }

    private fun encabezado(i: InformeTaller, resolver: StringResolver): String =
        seccion("informe", resolver.get(R.string.obd_report_title), resolver) {
        "<p class=\"fecha\">${e(fecha(i.generadoEn, resolver))}</p>" + // i18n-ignore
            "<h2>${e(i.titulo)}</h2>" +
            "<div class=\"ficha\">" + // i18n-ignore
            celda(resolver.get(R.string.obd_report_vehicle), i.vehiculo) +
            celda(resolver.get(R.string.obd_report_code_read), i.codigo) +
            celda(resolver.get(R.string.obd_report_symptoms), i.sintomas) +
            celda(resolver.get(R.string.obd_report_how_measured), i.comoSeMidio) +
            "</div>"
    }

    private fun conclusion(i: InformeTaller, resolver: StringResolver): String =
        seccion("conclusion", resolver.get(R.string.obd_report_conclusion), resolver) {
        insignia(i.conclusion.veredicto, resolver) + "<p class=\"principal\">${e(i.conclusion.frase)}</p>" + // i18n-ignore
            ifVacio(i.conclusion.cables, resolver.get(R.string.obd_report_no_wire_measurements)) { cables ->
                "<div class=\"mosaico\">" + cables.joinToString("") { cable -> // i18n-ignore
                    "<article class=\"cable\"><strong>${e(cable.etiqueta)}</strong><span>${e(cable.valor)}</span>${insignia(cable.veredicto, resolver)}</article>" // i18n-ignore
                } + "</div>"
            }
    }

    private fun multimetro(i: InformeTaller, resolver: StringResolver): String =
        seccion("multimetro", resolver.get(R.string.obd_report_multimeter_measurement), resolver) {
        ifVacio(i.mediciones, resolver.get(R.string.obd_report_no_multimeter_measurements)) { mediciones ->
            val pesas = mediciones.map { m ->
                PesaGrafica(m.cable, m.valor, m.bandaMin, m.bandaMax, m.unidad, origen(m.referencia, resolver))
            }
            GraficaSvg.pesas(pesas, resolver = resolver) + tabla(
                encabezados = listOf(
                    R.string.obd_report_sensor,
                    R.string.obd_report_wire,
                    R.string.obd_report_condition,
                    R.string.obd_report_measured,
                    R.string.obd_report_status,
                    R.string.obd_report_reference_source,
                ).map { resolver.get(it) },
                filas = mediciones.map { m ->
                    listOf(m.sensor, m.cable, m.condicion, valor(m.valor, m.unidad, resolver), estadoMedicion(m.estado, resolver), m.referencia)
                },
            )
        }
    }

    private fun ecu(i: InformeTaller, resolver: StringResolver): String =
        seccion("ecu", resolver.get(R.string.obd_report_ecu_title), resolver) {
        val pruebas = ifVacio(i.pruebas, resolver.get(R.string.obd_report_no_guided_tests)) { lista ->
            lista.joinToString("") { prueba ->
                "<article class=\"prueba\"><h3>${e(prueba.titulo)}</h3>${insignia(prueba.veredicto, resolver)}" + // i18n-ignore
                    "<p>${e(prueba.resumen)}</p>" + pasos(prueba, resolver) +
                    prueba.series.takeIf(List<*>::isNotEmpty)?.let {
                        GraficaSvg.lineas(it, prueba.tramos, resolver = resolver)
                    }.orEmpty() + "</article>"
            }
        }
        pruebas + "<h3>${e(resolver.get(R.string.obd_report_timeline))}</h3>" + lineaTiempo(i, resolver)
    }

    private fun pasos(prueba: PruebaInforme, resolver: StringResolver): String =
        ifVacio(prueba.pasos, resolver.get(R.string.obd_report_no_step_table)) { pasos ->
        tabla(
            encabezados = listOf(
                R.string.obd_report_step,
                R.string.obd_report_position,
                R.string.obd_report_approx_voltage,
            ).map { resolver.get(it) },
            filas = pasos.map { paso ->
                listOf(
                    paso.nombre,
                    paso.porcentaje?.let { "${LocaleNumbers.decimal(it, 1, resolver.locale)} %" } ?: "—",
                    paso.voltios?.let { "≈${LocaleNumbers.decimal(it, 2, resolver.locale)} V" } ?: "—",
                )
            },
        )
    }

    private fun lineaTiempo(i: InformeTaller, resolver: StringResolver): String =
        ifVacio(i.lineaTiempo, resolver.get(R.string.obd_report_no_events)) { eventos ->
        "<ol class=\"linea-tiempo\">" + eventos.joinToString("") { evento -> // i18n-ignore
            "<li><time>${e(hora(evento.instante))}</time><div><strong>${icono(evento.veredicto)} ${e(evento.titulo)}</strong>" + // i18n-ignore
                "<span>${e(evento.tipo)} · ${e(textoVeredicto(evento.veredicto, resolver))}</span><p>${e(evento.resumen)}</p></div></li>" // i18n-ignore
        } + "</ol>"
    }

    private fun comparacion(i: InformeTaller, resolver: StringResolver): String =
        seccion("comparacion", resolver.get(R.string.obd_report_comparison_title), resolver) {
        ifVacio(i.comparacion, resolver.get(R.string.obd_report_no_comparable_check)) { filas ->
            tabla(
                encabezados = listOf(
                    R.string.obd_report_metric,
                    R.string.obd_report_previous,
                    R.string.obd_report_now,
                    R.string.obd_report_change,
                    R.string.obd_report_reference,
                ).map { resolver.get(it) },
                filas = filas.map { listOf(it.metrica.etiqueta(resolver), it.base, it.ahora, it.cambio, it.referencia ?: "—") },
            )
        }
    }

    private fun interpretacion(i: InformeTaller, resolver: StringResolver): String =
        seccion("interpretacion", resolver.get(R.string.obd_report_interpretation), resolver) {
        val automatica = ifVacio(i.interpretacionAutomatica, resolver.get(R.string.obd_report_no_automatic_interpretation)) { puntos ->
            "<ul>" + puntos.joinToString("") { "<li>${e(it)}</li>" } + "</ul>"
        }
        automatica + "<h3>${e(resolver.get(R.string.obd_report_technician_interpretation))}</h3><p class=\"texto-libre\">${e(i.interpretacionTecnico.ifBlank { resolver.get(R.string.obd_report_no_technician_note) })}</p>" // i18n-ignore
    }

    private fun repuestos(i: InformeTaller, resolver: StringResolver): String =
        seccion("repuestos", resolver.get(R.string.obd_report_parts_title), resolver) {
        ifVacio(i.repuestos, resolver.get(R.string.obd_report_no_parts)) { repuestos ->
            tabla(
                encabezados = listOf(
                    R.string.obd_report_role,
                    R.string.obd_report_reference,
                    R.string.obd_report_description,
                    R.string.obd_report_type,
                    R.string.obd_report_notice,
                    R.string.obd_report_source,
                ).map { resolver.get(it) },
                filas = repuestos.map { listOf(it.rol, it.referencia, it.descripcion, it.tipo, it.nota, it.fuente) },
            ) + "<p class=\"aviso\">${e(resolver.get(R.string.obd_report_verify_part_warning))}</p>" // i18n-ignore
        }
    }

    private fun guia(i: InformeTaller, resolver: StringResolver): String =
        seccion("guia", resolver.get(R.string.obd_report_guide_title), resolver) {
        ifVacio(i.pasosGuia, resolver.get(R.string.obd_report_no_guide_steps)) { pasos ->
            "<ol>" + pasos.joinToString("") { "<li><strong>${e(it.titulo)}</strong><p>${e(it.detalle)}</p></li>" } + "</ol>"
        }
    }

    private fun pendientes(i: InformeTaller, resolver: StringResolver): String =
        seccion("pendientes", resolver.get(R.string.obd_report_pending_title), resolver) {
        ifVacio(i.pendientes, resolver.get(R.string.obd_report_no_pending_items)) { pendientes ->
            "<ul>" + pendientes.joinToString("") { "<li>${e(it)}</li>" } + "</ul>"
        }
    }

    private fun fuentes(i: InformeTaller, resolver: StringResolver): String =
        seccion("fuentes", resolver.get(R.string.obd_report_sources_title), resolver) {
        val lista = ifVacio(i.fuentes, resolver.get(R.string.obd_report_no_external_sources)) { fuentes ->
            "<ul>" + fuentes.joinToString("") { fuente -> "<li>${fuente(fuente)}</li>" } + "</ul>"
        }
        lista + "<p class=\"aviso\"><strong>${e(resolver.get(R.string.obd_report_typical_values_footer))}</strong> " + // i18n-ignore
            "${e(resolver.get(R.string.obd_report_generated_with, i.versionApp))}</p>"
    }

    private fun fuente(fuente: FuenteInforme): String {
        val url = fuente.url?.takeIf(::urlSegura)
        return if (url == null) e(fuente.nombre) else "<a href=\"${atributo(url)}\">${e(fuente.nombre)}</a>" // i18n-ignore
    }

    private fun tabla(encabezados: List<String>, filas: List<List<String>>): String =
        "<div class=\"tabla-wrap\"><table><thead><tr>" + encabezados.joinToString("") { "<th>${e(it)}</th>" } + // i18n-ignore
            "</tr></thead><tbody>" + filas.joinToString("") { fila ->
            "<tr>" + fila.joinToString("") { "<td>${e(it)}</td>" } + "</tr>"
        } + "</tbody></table></div>"

    private fun celda(etiqueta: String, valor: String) =
        "<div><dt>${e(etiqueta)}</dt><dd>${e(valor)}</dd></div>"

    private fun insignia(veredicto: Veredicto, resolver: StringResolver) =
        "<span class=\"insignia ${veredicto.name.lowercase(Locale.ROOT)}\">${icono(veredicto)} ${e(textoVeredicto(veredicto, resolver))}</span>" // i18n-ignore

    private fun icono(veredicto: Veredicto) = when (veredicto) {
        Veredicto.OK -> "✓"
        Veredicto.ATENCION -> "⚠"
        Veredicto.FALLA -> "✕"
        Veredicto.INFO -> "ⓘ"
    }

    private fun textoVeredicto(veredicto: Veredicto, resolver: StringResolver) = when (veredicto) {
        Veredicto.OK -> resolver.get(R.string.obd_report_verdict_ok)
        Veredicto.ATENCION -> resolver.get(R.string.obd_report_verdict_attention)
        Veredicto.FALLA -> resolver.get(R.string.obd_report_verdict_failure)
        Veredicto.INFO -> resolver.get(R.string.obd_report_verdict_info)
    }

    private fun estadoMedicion(estado: String, resolver: StringResolver) = when (estado) {
        "DENTRO" -> "✓ ${resolver.get(R.string.obd_report_measurement_within)}"
        "BAJO" -> "✕ ${resolver.get(R.string.obd_report_measurement_low)}"
        "ALTO" -> "✕ ${resolver.get(R.string.obd_report_measurement_high)}"
        else -> "ⓘ ${resolver.get(R.string.obd_report_no_reference)}"
    }

    private fun origen(referencia: String, resolver: StringResolver): String = when {
        referencia.contains(resolver.get(R.string.obd_report_source_prefix), ignoreCase = true) ->
            referencia.substringAfter(resolver.get(R.string.obd_report_source_prefix), "").trim(':', ' ')
        referencia.contains(resolver.get(R.string.obd_report_edited_by_you), ignoreCase = true) ->
            resolver.get(R.string.obd_report_edited_by_you)
        else -> resolver.get(R.string.obd_report_typical)
    }

    private fun valor(valor: Double, unidad: String, resolver: StringResolver) =
        "${LocaleNumbers.decimal(valor, if (unidad == "V") 2 else 1, resolver.locale)} $unidad".trim() // i18n-ignore

    private fun fecha(instante: Long, resolver: StringResolver): String {
        val pattern = if (resolver.locale.language == "es") "d 'de' MMMM 'de' yyyy, HH:mm" else "MMM d, yyyy, h:mm a" // i18n-ignore
        return DateTimeFormatter.ofPattern(pattern, resolver.locale).format(
            Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault()),
        )
    }

    private fun hora(instante: Long): String = FORMATO_HORA.format(Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault()))

    private fun seccion(id: String, titulo: String, resolver: StringResolver, contenido: () -> String) =
        "<section id=\"$id\"><h1>${e(titulo)}</h1>${contenido()}</section>" // i18n-ignore

    private fun <T> ifVacio(lista: List<T>, mensaje: String, contenido: (List<T>) -> String): String =
        if (lista.isEmpty()) "<p class=\"vacio\">${e(mensaje)}</p>" else contenido(lista) // i18n-ignore

    private fun urlSegura(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun e(texto: String): String = escapar(texto)

    private fun atributo(texto: String): String = escapar(texto)

    private fun escapar(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)

    private const val CSS = """
:root{color-scheme:light dark;--fondo:#f7f7f2;--superficie:#fff;--texto:#1d2118;--sec:#4b5343;--borde:#d8dccf;--acento:#728400;--ok:#276738;--atencion:#755b00;--falla:#9b2525}
*{box-sizing:border-box}body{margin:0;background:var(--fondo);color:var(--texto);font-family:system-ui,-apple-system,"Segoe UI",sans-serif;line-height:1.5}main{max-width:980px;margin:auto;padding:24px}section{background:var(--superficie);border:1px solid var(--borde);border-radius:14px;padding:22px;margin:0 0 18px;break-inside:avoid}h1{font-size:1.45rem;margin:0 0 16px}h2{font-size:1.8rem;margin:.25rem 0 1rem}h3{margin:1.25rem 0 .5rem}.fecha,.vacio,.aviso{color:var(--sec)}.ficha,.mosaico{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.ficha>div,.cable{border:1px solid var(--borde);border-radius:10px;padding:12px}.ficha dt{font-size:.8rem;color:var(--sec);font-weight:700}.ficha dd{margin:4px 0 0;overflow-wrap:anywhere}.principal{font-size:1.1rem}.cable{display:flex;flex-direction:column;gap:6px}.insignia{display:inline-flex;align-items:center;gap:6px;border:1px solid currentColor;border-radius:999px;padding:3px 9px;font-weight:700;font-size:.85rem}.ok{color:var(--ok)}.atencion{color:var(--atencion)}.falla{color:var(--falla)}.info{color:var(--sec)}.tabla-wrap{overflow-x:auto;margin:12px 0}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{text-align:left;vertical-align:top;border-bottom:1px solid var(--borde);padding:9px;overflow-wrap:anywhere}th{color:var(--sec);font-size:.8rem}svg{display:block;width:100%;height:auto;margin:14px 0;border:1px solid var(--borde);border-radius:10px;background:var(--superficie)}.linea-tiempo{list-style:none;padding:0}.linea-tiempo li{display:grid;grid-template-columns:80px 1fr;gap:12px;padding:10px 0;border-bottom:1px solid var(--borde)}.linea-tiempo time{font-variant-numeric:tabular-nums;color:var(--sec)}.linea-tiempo span{display:block;color:var(--sec);font-size:.85rem}.linea-tiempo p{margin:.25rem 0}.texto-libre{white-space:pre-wrap;overflow-wrap:anywhere}a{color:var(--acento);font-weight:700}
@media(max-width:520px){main{padding:10px}section{padding:16px;border-radius:10px}.ficha,.mosaico{grid-template-columns:1fr}.linea-tiempo li{grid-template-columns:64px 1fr}th,td{padding:7px}}
@media(prefers-color-scheme:dark){:root{--fondo:#11140e;--superficie:#1d2118;--texto:#f1f2e9;--sec:#c4c9b9;--borde:#464d3d;--acento:#e6f23a;--ok:#88d69b;--atencion:#f3d36a;--falla:#ff9b95}}
@media print{:root{--fondo:#fff;--superficie:#fff;--texto:#000;--sec:#333;--borde:#bbb;--acento:#435000;--ok:#155927;--atencion:#654c00;--falla:#851b1b}body{font-size:10pt}main{max-width:none;padding:0}section{border:0;border-radius:0;padding:0;margin:0 0 14mm;box-shadow:none}a{color:#000;text-decoration:none}a:after{content:" (" attr(href) ")"}svg{max-height:75mm}.tabla-wrap{overflow:visible}thead{display:table-header-group}}
"""
}
