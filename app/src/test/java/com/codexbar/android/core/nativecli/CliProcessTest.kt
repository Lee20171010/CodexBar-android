package com.codexbar.android.core.nativecli

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CliProcessTest {
    @Test fun drainsBothPipesAndBoundsOutputWithoutDeadlock() {
        val result = CliProcess.run(File("/bin/sh"), listOf("-c",
            "head -c 1100000 /dev/zero; head -c 1100000 /dev/zero >&2; exit 7"),
            mapOf("PATH" to "/usr/bin:/bin"), File("/"), 5000)
        assertEquals(7, result.exitCode)
        assertTrue(result.stdoutTruncated)
        assertTrue(result.stderrTruncated)
        assertEquals(1024 * 1024, result.stdout.length)
        assertFalse(result.timedOut)
    }

    @Test fun timeoutReapsDirectChild() {
        val result = CliProcess.run(File("/bin/sh"), listOf("-c", "echo \$\$; exec sleep 30"),
            mapOf("PATH" to "/usr/bin:/bin"), File("/"), 100)
        assertTrue(result.timedOut)
        val pid = result.stdout.trim().toLong()
        assertFalse(File("/proc/$pid").exists())
    }

    @Test fun cancellationReapsBeforeReturning() = runBlocking {
        val directory = Files.createTempDirectory("cli-cancel-test").toFile()
        val pidFile = File(directory, "pid")
        val task = async {
            runInterruptible(Dispatchers.IO) {
                CliProcess.run(File("/bin/sh"), listOf("-c", "echo \$\$ > \"\$1\"; exec sleep 30", "fixture", pidFile.path),
                    mapOf("PATH" to "/usr/bin:/bin"), directory, 30_000)
            }
        }
        try {
            withTimeout(5000) {
                while (!pidFile.exists() || pidFile.readText().trim().isEmpty()) delay(10)
            }
            val pid = pidFile.readText().trim().toLong()
            withTimeout(6000) { task.cancelAndJoin() }
            assertFalse(File("/proc/$pid").exists())
        } finally {
            task.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
