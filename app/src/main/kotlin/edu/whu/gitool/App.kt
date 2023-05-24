package edu.whu.gitool

import com.beust.jcommander.JCommander
import edu.whu.gitool.subcommand.MergeScenarioFinder
import edu.whu.gitool.subcommand.ResolutionExtractor
import edu.whu.gitool.subcommand.command.CommandExtract
import edu.whu.gitool.subcommand.command.CommandFind
import edu.whu.gitool.subcommand.command.SubCommandEnum
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("gitool")


fun main(args: Array<String>) {
    val finder = CommandFind()
    val extractor = CommandExtract()
    val jc = JCommander.newBuilder()
        .addCommand(SubCommandEnum.FIND.commandName, finder)
        .addCommand(SubCommandEnum.EXTRACT.commandName, extractor)
        .build()

    jc.parse(*args)
    when (SubCommandEnum.valueOf(jc.parsedCommand.uppercase(Locale.ENGLISH))) {
        SubCommandEnum.FIND -> {
            MergeScenarioFinder(
                finder.onlyConflicts,
                finder.onlyMerged,
                finder.queryBase,
                finder.sinceHash,
                finder.beforeHash,
                finder.count,
                finder.outputToTmp,
                finder.outputFile,
                finder.writeHeader,
                finder.projectPath
            ).run()
        }

        SubCommandEnum.CLONE -> TODO()
        SubCommandEnum.EXTRACT -> {
            ResolutionExtractor(
                extractor.projectPath,
                extractor.dumpPath,
                extractor.statFile,
                extractor.mss,
                extractor.mssFile
            ).run()
        }

        SubCommandEnum.VALIDATE -> TODO()
    }
}

