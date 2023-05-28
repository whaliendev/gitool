package edu.whu.gitool

import com.beust.jcommander.JCommander
import com.beust.jcommander.MissingCommandException
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import edu.whu.gitool.exception.GitoolBaseException
import edu.whu.gitool.subcommand.MergeScenarioFinder
import edu.whu.gitool.subcommand.ResolutionExtractor
import edu.whu.gitool.subcommand.command.CommandExtract
import edu.whu.gitool.subcommand.command.CommandFind
import edu.whu.gitool.subcommand.command.CommandStat
import edu.whu.gitool.subcommand.command.SubCommandEnum
import org.slf4j.LoggerFactory
import java.io.File
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
            val statCalculator = CommandStat()

            val jc = JCommander.newBuilder()
                .addObject(gitool)
                .addCommand(SubCommandEnum.FIND.commandName, finder)
                .addCommand(SubCommandEnum.EXTRACT.commandName, extractor)
                .addCommand(SubCommandEnum.STAT.commandName, statCalculator)
                .build().apply {
                    programName = "gitool"
                }

            try {
                jc.parse(*args)
            } catch (ex: MissingCommandException) {
                println("missing subcommand: ${ex.message}")
                return
            } catch (ex: ParameterException) {
                println("parsing error: ${ex.message}")
            }

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
                        ).execute()
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
                        ).execute()
                    }

                    SubCommandEnum.VALIDATE -> TODO()
                    SubCommandEnum.STAT -> {
                        if (statCalculator.help) {
                            jc.usageFormatter.usage(SubCommandEnum.STAT.commandName)
                            return
                        }
                        runCommandStat(statCalculator)
                    }
                }
            } catch (ex: NullPointerException) {
                jc.usage()
                return
            } catch (ex: ParameterException) {
                println("illegal use of gitool: ${ex.message}")
                return
            } catch (ex: MissingCommandException) {
                println("missing subcommand: ${ex.message}")
            } catch (ex: GitoolBaseException) {
                println(ex.message)
                return
            }
        }

        private fun runCommandStat(statCalculator: CommandStat) {
            val msList = MergeScenarioFinder(
                onlyConflicts = true,
                onlyMerged = true,
                queryBase = false,
                sinceHash = null,
                beforeHash = statCalculator.beforeHash,
                threshold = statCalculator.count,
                outputToTmp = true,
                outputFile = null,
                writeHeader = false,
                projectPath = statCalculator.projectPath.absolutePath
            ).execute().orElseThrow().map { it.firstOrNull() }
            val valid = msList.all { it != null && it.length == 40 }
            if (!valid) {
                throw GitoolBaseException("fatal error: something went wrong, fail to find merge scenarios")
            }
            if (msList.isEmpty()) {
                throw GitoolBaseException("no merge scenarios satisfied specified conditions")
            }
            val mssFile =
                File(System.getProperty("java.io.tmpdir")).resolve(CommandFind.MERGE_SCENARIO_DEST)
            if (!mssFile.exists() || !mssFile.isFile) {
                throw GitoolBaseException("fatal error: intermediate caches accidentally removed")
            }

            ResolutionExtractor(
                projectPath = statCalculator.projectPath,
                dumpPath = statCalculator.dumpPath,
                statFile = statCalculator.statFile,
                msscli = emptyList(),
                mssFile = mssFile
            ).execute()
        }
    }
}

