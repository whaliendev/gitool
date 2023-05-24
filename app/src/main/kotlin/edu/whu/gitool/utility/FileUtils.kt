package edu.whu.gitool.utility

import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object FileUtils {
    private val logger = LoggerFactory.getLogger(FileUtils::class.java)
    fun overwriteCSV(
        dest: File,
        header: Array<String>?,
        data: List<List<String>>,
        separator: String = ","
    ): Boolean {
        if (dest.exists() && !dest.isFile) {
            logger.error("we're overwriting ${dest.absolutePath}, however, it's not a file, we'll skip this write")
            return false
        }
        dest.bufferedWriter().use { writer ->
            if (header != null) {
                writer.write(header.joinToString(separator))
                writer.newLine()
            }

            data.forEach { row ->
                writer.write(row.joinToString(separator))
                writer.newLine()
            }
            return true
        }
    }

    fun writeFile(
        dest: String,
        content: String
    ): Boolean {
        val file = File(dest)
        if (file.exists() && file.isFile) {
            logger.warn("dumping $dest, however, it's there already, we'll skip this write")
            return false
        }
        if (file.exists() && !file.isFile) {
            logger.error("we're overwriting $dest, however, it's not a file, we'll skip this write")
            return false
        }

        BufferedWriter(FileWriter(file)).use { writer ->
            writer.write(content)
            return true
        }
    }

    fun prepareDirectories(directory: String) {
        val dir = File(directory)
        dir.mkdirs()
    }

    fun readFileAsLines(file: String): List<String> {
        val fileF = File(file)
        if (!fileF.exists() || !fileF.isFile) return emptyList()
        return fileF.readLines()
    }
}
