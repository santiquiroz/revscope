package com.revscope.feature.workshop.taller

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R as ObdR
import com.revscope.core.uitesting.XmlStringResolver
import com.revscope.feature.workshop.R

internal object PruebaStringResolver {
    private val resDirs = listOf("src/main/res", "../../core/obd/src/main/res")

    fun es(): StringResolver = XmlStringResolver.es(R::class.java, ObdR::class.java, resDirs = resDirs)

    fun en(): StringResolver = XmlStringResolver.en(R::class.java, ObdR::class.java, resDirs = resDirs)
}
