package edu.whu.gitool.subcommand.command

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import java.io.File

@Parameters(
    commandDescription = SubCommandEnum.Descriptions.FIND
)
class CommandFind {
    @Parameter(
        names = ["--only-conflicts"],
        description = "only find merge scenarios with conflicts",
        arity = 1
    )
    // indicate gitool find to only record conflicts merge scenario
    var onlyConflicts: Boolean = true

    @Parameter(
        names = ["--only-merged"],
        description = "return merge scenario with only merged commit node set, output will store " +
            "merged commit id if set to true; otherwise, output will be set to " +
            "our, their, merged commit nodes with [queryBase] set to false; If " +
            "[queryBase] is true and [onlyMerged] is false, output will store " +
            "our, their, base, merged"
    )
    // record commit id of merged node only
    var onlyMerged: Boolean = false

    @Parameter(
        names = ["--query-base"],
        description = "return merge scenario with base field set"
    )
    var queryBase: Boolean = false

    @Parameter(
        names = ["--since", "-s"],
        description = "since which commit node"
    )
    var sinceHash: String? = null

    @Parameter(
        names = ["--before", "-b"],
        description = "before which commit node"
    )
    var beforeHash: String? = null

    @Parameter(
        names = ["--count", "-c"],
        description = "count of merge scenarios to look up, default is $LOOKUP_THRESHOLD",
    )
    var count: Int = LOOKUP_THRESHOLD

    @Parameter(
        names = ["--output-to-tmp", "-ott"],
        description = "output merge scenario found to $MERGE_SCENARIO_DEST in tmp dir"
    )
    var outputToTmp = false

    @Parameter(
        names = ["-d", "--output-file"],
        description = "output merge scenarios found to destination file, with the following sequence: our, their, base, merged",
        converter = FileConverter::class
    )
    var outputFile: File = File(System.getProperty("java.io.tmpdir")).resolve(MERGE_SCENARIO_DEST)

    @Parameter(
        names = ["--write-header"],
        description = "when we want to write results to a external csv, whether we need to write header row"
    )
    var writeHeader = false

    @Parameter(
        description = "<project path>",
        required = true
    )
    var projectPath: String = ""

    @Parameter(names = ["--help", "-h"], help = true, description = "display help message")
    var help: Boolean = false

    internal companion object {
        const val MERGE_SCENARIO_DEST = "merge-scenarios.csv"
        const val LOOKUP_THRESHOLD = 3000
    }
}
