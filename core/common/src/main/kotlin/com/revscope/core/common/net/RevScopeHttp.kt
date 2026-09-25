package com.revscope.core.common.net

import com.revscope.core.common.BuildConfig

object RevScopeHttp {

    private const val CONTACT_URL = "https://github.com/santiquiroz/revscope"

    // Android manda "Dalvik/..." por defecto y el demo de OSRM respondió 403 (hotfix v1.12.1);
    // Overpass y las políticas de OSM también piden identificar la app y un contacto.
    const val USER_AGENT = "RevScope/" + BuildConfig.APP_VERSION_NAME + " (+" + CONTACT_URL + ")"

    fun userAgent(version: String): String = "RevScope/$version (+$CONTACT_URL)"
}
