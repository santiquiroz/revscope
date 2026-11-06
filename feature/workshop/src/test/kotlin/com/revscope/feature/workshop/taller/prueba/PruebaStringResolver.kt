package com.revscope.feature.workshop.taller.prueba

internal fun resolverPrueba() = com.revscope.core.uitesting.XmlStringResolver.es(
    com.revscope.core.obd.R::class.java,
    com.revscope.feature.workshop.R::class.java,
    com.revscope.core.designsystem.R::class.java,
    resDirs = listOf("src/main/res", "../../core/obd/src/main/res", "../../core/designsystem/src/main/res"),
)
