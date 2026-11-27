package com.lakhyadeep.papai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lakhyadeep.papai.data.local.db.PapiDatabase
import com.lakhyadeep.papai.data.local.db.PredefinedData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PapiRobolectricTest {

    @Test
    fun `app name and package match Papi AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Papi AI", context.getString(R.string.app_name))
        assertEquals("com.lakhyadeep.papai", context.packageName)
    }

    @Test
    fun `in-memory Room database stores predefined assistants`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PapiDatabase.createInMemory(context)
        try {
            db.assistantDao().insertAllIgnore(PredefinedData.assistants())
            val assistants = db.assistantDao().observeAllAssistants().first()
            assertEquals(5, assistants.size)
            assertTrue(assistants.any { it.name == "Coding Assistant" })
        } finally {
            db.close()
        }
    }
}
