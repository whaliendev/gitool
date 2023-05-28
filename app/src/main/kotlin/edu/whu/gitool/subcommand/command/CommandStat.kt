package edu.whu.gitool.subcommand.command

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import java.io.File

@Parameters(
    commandDescription = SubCommandEnum.Descriptions.STAT
)
class CommandStat {
    @Parameter(
        names = ["--before", "-b"],
        description = "walk from which commit node, use this option to resolve git branch"
    )
    var beforeHash: String? = null

    @Parameter(
        names = ["--count", "-c"],
        description = "number of merge scenarios to look up, default is ${CommandFind.LOOKUP_THRESHOLD}",
    )
    var count: Int = CommandFind.LOOKUP_THRESHOLD


    @Parameter(
        description = "<project path>",
        required = true,
        converter = FileConverter::class
    )
    var projectPath: File = File("")

    @Parameter(
        names = ["--dump-stat"],
        description = "dump statistics to which file; " +
            "if not specified, statistics will be dumped to <java.io.tmpdir>/stat.csv"
    )
    var statFile: File = File(System.getProperty("java.io.tmpdir")).resolve("stat.csv")

    @Parameter(
        names = ["--dump-path", "-d"],
        description = "directory to dump resolution results; " +
            "if not specified, resolutions will be dumped <java.io.tmpdir>"
    )
    var dumpPath: File = File(System.getProperty("java.io.tmpdir"))

    @Parameter(names = ["--help", "-h"], help = true, description = "display help message")
    var help: Boolean = false
}
