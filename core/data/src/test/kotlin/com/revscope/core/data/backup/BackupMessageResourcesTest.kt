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
}
