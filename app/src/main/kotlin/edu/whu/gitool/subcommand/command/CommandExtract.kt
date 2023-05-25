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
        description = "project path to extract merge scenario resolutions",
        converter = FileConverter::class,
        required = true
    )
    lateinit var projectPath: File

    @Parameter(
        names = ["--dump-stat"],
        description = "dump statistic info to which file"
    )
    var statFile: File? = null

    @Parameter(
        names = ["--dump-path", "-d"],
        required = true,
        description = "directory to dump resolution results"
    )
    lateinit var dumpPath: File

    @Parameter(
        names = ["--mss"],
        description = "list of merge scenarios, represented by merged commit id"
    )
    var mss: List<String> = mutableListOf()

    @Parameter(
        names = ["--mss-file"],
        description = "merge scenarios to extract resolutions, should be file path of " +
            "merge scenarios, one per line"
    )
    var mssFile: File? = null

    @Parameter(names = ["--help"], help = true)
    var help: Boolean = false
}
