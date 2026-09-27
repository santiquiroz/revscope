package com.revscope.feature.workshop.taller.informe

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.export.CsvShare

@Composable
fun InformeScreen(onVolver: () -> Unit, viewModel: InformeViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val webView = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.archivoCompartir.collect { archivo -> CsvShare.shareFile(context, archivo, MIME_HTML) }
    }
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    Box(Modifier.fillMaxSize()) {
        InformeContent(
            estado = estado,
            acciones = AccionesInforme(
                onVolver = onVolver,
                onReintentar = viewModel::cargar,
                onCompartir = { viewModel.prepararArchivo(CsvShare.exportsDir(context)) },
                onImprimir = { webView.value?.imprimir(context, estado.titulo) },
                onEditarInterpretacion = viewModel::abrirEditor,
                onCambiarInterpretacion = viewModel::cambiarInterpretacion,
                onGuardarInterpretacion = viewModel::guardarInterpretacion,
                onCancelarEdicion = viewModel::cancelarEditor,
            ),
            vistaPrevia = { modifier -> VistaPreviaHtml(estado.html, { webView.value = it }, modifier) },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun VistaPreviaHtml(html: String, onWebView: (WebView) -> Unit, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> webViewSeguro(context).also(onWebView) },
        update = { webView ->
            onWebView(webView)
            // Recargar en cada recomposición reinicia el scroll de la vista previa.
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, MIME_HTML, "UTF-8", null)
            }
        },
    )
}

private fun webViewSeguro(context: Context) = WebView(context).apply {
    settings.javaScriptEnabled = false
    settings.domStorageEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.blockNetworkLoads = true
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
    }
}

private fun WebView.imprimir(context: Context, titulo: String) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    manager.print(titulo, createPrintDocumentAdapter(titulo), PrintAttributes.Builder().build())
}

private const val MIME_HTML = "text/html"
