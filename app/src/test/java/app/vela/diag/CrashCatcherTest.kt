package app.vela.diag

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CrashCatcherTest {

    @Test
    fun testPendingCrashReportsFiltering() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "vela_test_crash_${System.currentTimeMillis()}").apply { mkdirs() }
        val dummyContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
        }

        val crashDir = File(tempDir, "diag/crash").apply { mkdirs() }
        File(crashDir, "crash-1000.txt").writeText("Exception report")
        File(crashDir, "crash-exit-2000.txt").writeText("Process exit report")
        File(crashDir, "non_crash_file.txt").writeText("ignore")

        val pending = CrashCatcher.pending(dummyContext)
        assertEquals(2, pending.size)
        assertTrue(pending.any { it.name == "crash-1000.txt" })
        assertTrue(pending.any { it.name == "crash-exit-2000.txt" })

        CrashCatcher.clear(dummyContext)
        assertTrue(CrashCatcher.pending(dummyContext).isEmpty())

        tempDir.deleteRecursively()
    }
}
