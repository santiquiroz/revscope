package com.revscope.feature.settings

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenStringsTest {
    @Test
    fun `backup restore warning has an English translation`() {
        val resolverEn = XmlStringResolver.en(R::class.java)

        assertEquals(
            "This replaces ALL current data (trips, profiles, reports, and settings) with the selected backup. " +
                "This action cannot be undone.",
            resolverEn.get(R.string.settings_backup_restore_warning),
        )
    }
}
