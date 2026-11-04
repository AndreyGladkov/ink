package io.github.andreygladkov.inkmessages.mms

import android.net.Uri
import android.provider.OpenableColumns
import io.github.andreygladkov.inkmessages.TestData.context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class MmsFilesTest {
    @Test fun servesAFileWithItsNameSizeAndType() {
        val file = MmsFiles.file(context, "card.vcf").apply { writeText("BEGIN:VCARD") }
        val uri = MmsFiles.uri(context, file)
        assertEquals("BEGIN:VCARD", context.contentResolver.openInputStream(uri)!!.use { it.reader().readText() })
        assertEquals("text/x-vcard", context.contentResolver.getType(uri))
        context.contentResolver.query(uri, null, null, null, null)!!.use {
            it.moveToFirst()
            assertEquals("card.vcf", it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(11L, it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
    }

    @Test fun refusesPathsOutsideItsFolder() {
        context.filesDir.resolve("secret").writeText("x")
        val escape = Uri.parse("content://${context.packageName}.mmsfiles/..%2F..%2Ffiles%2Fsecret")
        assertFailsWith<FileNotFoundException> { context.contentResolver.openInputStream(escape) }
    }
}
