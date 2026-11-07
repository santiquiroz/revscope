package com.revscope.core.data.backup

import com.revscope.core.data.R
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMessageResourcesTest {

    @Test
    fun `invalid zip entry message is available in english`() {
        val resolver = XmlStringResolver.en(R::class.java)

        assertEquals(
            "Invalid ZIP entry: ../outside",
            resolver.get(R.string.data_invalid_zip_entry, "../outside"),
        )
    }

    @Test
    fun `busy database message is available in english and spanish`() {
        assertEquals(
            "The database is busy; try the backup again in a few seconds.",
            XmlStringResolver.en(R::class.java).get(R.string.data_backup_busy),
        )
        assertEquals(
            "La base de datos está ocupada; intenta la copia de nuevo en unos segundos.",
            XmlStringResolver.es(R::class.java).get(R.string.data_backup_busy),
        )
    }
}
