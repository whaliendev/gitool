package edu.whu.gitool

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import edu.whu.gitool.subcommand.MergeScenarioFinder
import edu.whu.gitool.subcommand.ResolutionExtractor
import edu.whu.gitool.subcommand.command.CommandExtract
import edu.whu.gitool.subcommand.command.CommandFind
import edu.whu.gitool.subcommand.command.SubCommandEnum
import org.slf4j.LoggerFactory
import java.util.*

class AppKt {
    private val logger = LoggerFactory.getLogger("gitool")

    @Parameter(
        help = true,
        names = ["--help", "-h"],
        description = "display help message"
    )
    var help: Boolean = false

    @Parameter(
        names = ["--version", "-v"],
        description = "show version"
    )
    var showVersion: Boolean = false

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val gitool = AppKt()
            val finder = CommandFind()
            val extractor = CommandExtract()
            val jc = JCommander.newBuilder()
                .addObject(gitool)
                .addCommand(SubCommandEnum.FIND.commandName, finder)
                .addCommand(SubCommandEnum.EXTRACT.commandName, extractor)
                .build().apply {
                    programName = "gitool"
                }.also {
                    it.setAllowAbbreviatedOptions(true)
                }

            jc.parse(*args)

            if (gitool.help) {
                jc.usage()
                return
            }

            if (gitool.showVersion) {
                println("gitool 1.0.0-alpha\n\nCopyright © 2023 Hwa")
                return
            }

            try {
                when (SubCommandEnum.valueOf(jc.parsedCommand.uppercase(Locale.ENGLISH))) {
                    SubCommandEnum.FIND -> {
                        if (finder.help) {
                            jc.usageFormatter.usage(SubCommandEnum.FIND.commandName)
                            return
                        }
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
                        if (extractor.help) {
                            jc.usageFormatter.usage(SubCommandEnum.EXTRACT.commandName)
                            return
                        }
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
            } catch (ex: NullPointerException) {
                jc.usage()
                return
            }
        }
    }
}

