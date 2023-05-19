package edu.whu.gitool

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File

class AppKtTest {

    @Test
    fun `find subcommand should work as expected`() {
        val rocksDBLocation = File("/home/whalien/Desktop/rocksdb")
        if (!rocksDBLocation.exists() || !rocksDBLocation.isDirectory) return
        val findArgs1 = arrayOf(
            "find",
            "--output-to-tmp",
            "--write-header",
            rocksDBLocation.absolutePath
        )
        assertDoesNotThrow {
            main(findArgs1)
        }
    }
}
