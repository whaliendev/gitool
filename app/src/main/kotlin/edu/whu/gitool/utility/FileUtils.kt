package edu.whu.gitool.utility

import java.io.File

object FileUtils {
    fun overwriteCSV(
        dest: File,
        header: Array<String>?,
        data: List<List<String>>,
        separator: String = ","
    ) {
        dest.bufferedWriter().use { writer ->
            if (header != null) {
                writer.write(header.joinToString(separator))
                writer.newLine()
            }

            data.forEach { row ->
                writer.write(row.joinToString(separator))
                writer.newLine()
            }
        }
    }
}
