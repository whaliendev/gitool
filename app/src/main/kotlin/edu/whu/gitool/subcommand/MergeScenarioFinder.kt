package edu.whu.gitool.subcommand

import com.beust.jcommander.ParameterException
import edu.whu.gitool.model.MergeQuadruple
import edu.whu.gitool.model.MergeTriple
import edu.whu.gitool.subcommand.MergeScenarioFinder.HeaderEnum.*
import edu.whu.gitool.subcommand.command.CommandFind
import edu.whu.gitool.utility.ArgValidator
import edu.whu.gitool.utility.FileUtils
import edu.whu.gitool.utility.GitService
import edu.whu.gitool.utility.GitService.Companion.getMergeBase
import edu.whu.gitool.utility.GitService.Companion.openGitRepo
import org.apache.log4j.Logger
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import java.io.File
import java.util.*

class MergeScenarioFinder(
    private val onlyConflicts: Boolean, // only store merge scenario with merge conflicts
    onlyMerged: Boolean,    // we only want merged commit ids
    queryBase: Boolean,     // query base commit id
    private var sinceHash: String?,
    private var beforeHash: String?,
    private val threshold: Int,
    outputToTmp: Boolean,
    private var outputFile: File?,
    private val writeHeader: Boolean,
    private val projectPath: String
) : Task<List<List<String>>> {
    private val logger = Logger.getLogger(MergeScenarioFinder::class.java)
    private var useThreshold = true
    private var headerEnum = OnlyMerged

    init {
        // onlyMerged and queryBase is incompatible
        if (onlyMerged && queryBase) {
            throw ParameterException(
                "only merged only store merged commit node, while query base " +
                    "will store all our, their, base, merged commit nodes"
            )
        }

        ArgValidator.checkGitRepo(projectPath)

        // complete sinceHash and beforeHash if nonnull
        if (sinceHash != null) {
            sinceHash = ArgValidator.checkObjectId(sinceHash!!, projectPath)
        }
        if (beforeHash != null) {
            beforeHash = ArgValidator.checkObjectId(beforeHash!!, projectPath)
        }
        if (sinceHash != null && beforeHash == null) {
            throw ParameterException(
                "Due to the way Git stores commit nodes internally, we do not support traversing " +
                    "from the sinceHash."
            )
        }

        // if outputFile is a directory, append filename to it
        if (outputFile != null && outputFile!!.isDirectory) {
            outputFile = File(outputFile, CommandFind.MERGE_SCENARIO_DEST)
        }
        // if outputToTmp specified, output to tmp file instead of outputFile
        if (outputToTmp) {
            outputFile = File(System.getProperty("java.io.tmpdir"), CommandFind.MERGE_SCENARIO_DEST)
            logger.info("merge scenario found will be written to $outputFile")
        }

        // outputFile must be set if writing header is needed
        if (outputFile == null && writeHeader) {
            throw ParameterException("-d or --output-file must be specified if you want to output results")
        }

        // if both sinceHash and beforeHash is set, we don't use threshold
        if (sinceHash != null && beforeHash != null) useThreshold = false

        // the format we write header
        headerEnum = if (onlyMerged) {
            OnlyMerged
        } else if (queryBase) {
            MergeWithBase
        } else {
            MergeWithoutBase
        }
    }

    override fun execute(): Optional<List<List<String>>> {
        val mergeQuadruples = mutableListOf<MergeQuadruple>()
        if (!useThreshold) {
            logger.info("begin walking from $beforeHash to $sinceHash")
            walkFromBeforeToSince(mergeQuadruples)
        } else if (sinceHash == null && beforeHash != null) {
            logger.info("begin walking up to $threshold merge scenarios from $beforeHash")
            walkFromBefore(mergeQuadruples)
        } else {
            logger.info("begin walking up to $threshold merge scenarios from HEAD")
            walk(mergeQuadruples)
        }
        logger.info("${mergeQuadruples.size} merge scenarios found at this setting")

        var data: List<List<String>> = listOf()
        if (outputFile != null) {   // write results to external file
            logger.info("merge scenario found will be written to $outputFile")
            var header: Array<String>? = null
            if (writeHeader) {
                header = headerEnum.header.split(",").toTypedArray()
            }

            data = when (headerEnum) {
                OnlyMerged -> mergeQuadruples.map { listOf(it.merged) }
                MergeWithoutBase -> mergeQuadruples.map {
                    listOf(it.our!!, it.their!!, it.merged)
                }

                MergeWithBase -> mergeQuadruples.map {
                    listOf(it.our!!, it.their!!, it.base!!, it.merged)
                }
            }
            FileUtils.overwriteCSV(outputFile!!, header, data)
        } else {
            logger.info("merge scenario found will be print to stdout, the header of data is ${headerEnum.header}")
            data.forEach { row ->
                println(row.joinToString(" "))
            }
        }

        return Optional.of(data)
    }

    private fun walkFromBeforeToSince(mergeQuadruples: MutableList<MergeQuadruple>) {
        openGitRepo(projectPath).get().use { repo ->
            RevWalk(repo).apply {
                revFilter = if (onlyConflicts) {
                    GitService.Companion.MergeConflictsFilter(repo as FileRepository)
                } else {
                    RevFilter.ONLY_MERGES
                }
            }.use { walk ->
                walk.isRetainBody = false
                val commit = walk.parseCommit(repo.resolve(beforeHash))
                logger.info("start commit: $commit")

                walk.markStart(commit)
                run breaking@{
                    walk.forEach { rev ->
                        addToMergeQuadruples(rev, mergeQuadruples)
                        if (rev.id.name == sinceHash) {
                            logger.info("found since commit, stopping walk")
                            return@breaking
                        }
                    }
                }
            }
        }
    }

    private fun walkFromBefore(mergeQuadruples: MutableList<MergeQuadruple>) {
        openGitRepo(projectPath).get().use { repo ->
            RevWalk(repo).apply {
                revFilter = if (onlyConflicts) {
                    GitService.Companion.MergeConflictsFilter(repo as FileRepository)
                } else {
                    RevFilter.ONLY_MERGES
                }
            }.use { walk ->
                walk.isRetainBody = false
                val commit = walk.parseCommit(repo.resolve(beforeHash))
                logger.info("start commit: $commit")

                walk.markStart(commit)
                var count = 0
                run breaking@{
                    walk.forEach { rev ->
                        addToMergeQuadruples(rev, mergeQuadruples)
                        count++
                        if (count == threshold) {
                            return@breaking
                        }
                    }
                }
            }
        }
    }

    private fun walk(mergeQuadruples: MutableList<MergeQuadruple>) {
        openGitRepo(projectPath).get().use { repo ->
            RevWalk(repo).apply {
                revFilter = if (onlyConflicts) {
                    GitService.Companion.MergeConflictsFilter(repo as FileRepository)
                } else {
                    RevFilter.ONLY_MERGES
                }
            }.use { walk ->
                walk.isRetainBody = false
                val commit = walk.parseCommit(repo.resolve("HEAD"))
                logger.info("start commit: $commit")

                walk.markStart(commit)
                var count = 0
                run breaking@{
                    walk.forEach { rev ->
                        addToMergeQuadruples(rev, mergeQuadruples)
                        count++
                        if (count == threshold) {
                            return@breaking
                        }
                    }
                }
            }
        }
    }

    private fun addToMergeQuadruples(rev: RevCommit, mergeQuadruples: MutableList<MergeQuadruple>) {
        logger.info("we found a merge scenario with merged id [${rev.id.name}]")
        val mergeTriple = MergeTriple("", "", "")
        val mergeQuadruple = MergeQuadruple(mergeTriple, rev.id.name)
        when (headerEnum) {
            OnlyMerged -> mergeQuadruple.merged = rev.id.name
            MergeWithoutBase -> {
                mergeQuadruple.merged = rev.id.name
                mergeQuadruple.our = rev.parents[0].id.name
                mergeQuadruple.their = rev.parents[1].id.name
            }

            MergeWithBase -> {
                mergeQuadruple.merged = rev.id.name
                mergeQuadruple.our = rev.parents[0].id.name
                mergeQuadruple.their = rev.parents[1].id.name
                mergeQuadruple.base =
                    getMergeBase(projectPath, rev.parents[0], rev.parents[1])
            }
        }
        mergeQuadruples.add(mergeQuadruple)
    }

    private enum class HeaderEnum(val header: String) {
        OnlyMerged("merged"),
        MergeWithoutBase("our,their,merged"),
        MergeWithBase("our,their,base,merged")
    }
}
