package edu.whu.gitool

import com.beust.jcommander.JCommander
import edu.whu.gitool.subcommand.MergeScenarioFinder
import edu.whu.gitool.subcommand.command.CommandFind
import edu.whu.gitool.subcommand.command.SubCommandEnum
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("gitool")

fun main(args: Array<String>) {
    val finder = CommandFind()
    val jc = JCommander.newBuilder()
        .addCommand(SubCommandEnum.FIND.commandName, finder)
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
        SubCommandEnum.EXTRACT -> TODO()
        SubCommandEnum.VALIDATE -> TODO()
    }
}
