package edu.whu.gitool.subcommand.command

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import java.io.File

@Parameters(
    commandDescription = SubCommandEnum.Descriptions.EXTRACT
)
class CommandExtract {
    @Parameter(
        description = "<project path>",
        converter = FileConverter::class,
        required = true
    )
    var projectPath: File = File("")

    @Parameter(
        names = ["--dump-stat"],
        description = "dump statistic info to which file; if not specified, statistics will not be dumped"
    )
    var statFile: File? = null

    @Parameter(
        names = ["--dump-path", "-d"],
        required = true,
        description = "directory to dump resolution results"
    )
    var dumpPath: File = File(System.getProperty("java.io.tmpdir"))

    @Parameter(
        names = ["--mss"],
        description = "list of merge scenarios, represented by merged commit id"
    )
    var mss: List<String> = mutableListOf()

    @Parameter(
        names = ["--mss-file"],
        description = "merge scenarios to extract resolutions, should be file path containing " +
            "merge scenarios, one per line"
    )
    var mssFile: File? = null

    @Parameter(names = ["--help", "-h"], help = true, description = "display help message")
    var help: Boolean = false
}
