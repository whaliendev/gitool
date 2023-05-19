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
    private val onlyMerged: Boolean,    // we only want merged commit ids
    private val queryBase: Boolean,     // query base commit id
    private var sinceHash: String?,
    private var beforeHash: String?,
    private val threshold: Int,
    private val outputToTmp: Boolean,
    private var outputFile: File?,
    private val writeHeader: Boolean,
    private val projectPath: String
) : Task<List<List<String>>> {
    private val logger = Logger.getLogger(MergeScenarioFinder::class.java)
    private var useThreshold = true
    private var headerEnum = OnlyMerged

    init {
        if (onlyMerged && queryBase) {
            throw ParameterException(
                "only merged only store merged commit node, while query base " +
                    "will store all our, their, base, merged commit nodes"
            )
        }
        ArgValidator.checkGitRepo(projectPath)
        if (sinceHash != null) {
            sinceHash = ArgValidator.checkObjectId(sinceHash!!, projectPath)
        }
        if (beforeHash != null) {
            beforeHash = ArgValidator.checkObjectId(beforeHash!!, projectPath)
        }
        if (sinceHash != null && beforeHash == null) {
            throw ParameterException(
                "Due to git's internal storage mechanism, " +
                    "gitool don't support walk from since node"
            )
        }

        if (outputFile != null && outputFile!!.isDirectory) {
            outputFile = File(outputFile, CommandFind.MERGE_SCENARIO_DEST)
        }
        if (outputToTmp) {
            outputFile = File(System.getProperty("java.io.tmpdir"), CommandFind.MERGE_SCENARIO_DEST)
            logger.info("merge scenario found will be written to $outputFile")
        }
        if (outputFile == null && writeHeader) {
            throw ParameterException("-d or --output-file must be specified if you want to output results")
        }

        if (sinceHash != null && beforeHash != null) useThreshold = false

        headerEnum = if (onlyMerged) {
            OnlyMerged
        } else if (queryBase) {
            MergeWithBase
        } else {
            MergeWithoutBase
        }
    }

    override var result: Optional<List<List<String>>> = Optional.empty()

    override fun run(): Result<Boolean> {
        val mergeQuadruples = mutableListOf<MergeQuadruple>()
        if (!useThreshold) {
            walkFromBeforeToSince(mergeQuadruples)
        } else if (sinceHash == null && beforeHash != null) {
            walkFromBefore(mergeQuadruples)
        } else {
            walk(mergeQuadruples)
        }

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

        result = Optional.of(data)

        return Result.success(true)
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

                logger.info("walking all commits starting at $beforeHash until we find $sinceHash")
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
        logger.info("we found ${mergeQuadruples.size} merge scenarios satisfying conditions")
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

                logger.info("walking all commits starting at HEAD until we find $threshold merge scenarios")
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
        logger.info("we found ${mergeQuadruples.size} merge scenarios satisfying conditions")
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

                logger.info("walking all commits starting at HEAD until we find $threshold merge scenarios")
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
        logger.info("we found ${mergeQuadruples.size} merge scenarios satisfying conditions")
    }

    private fun addToMergeQuadruples(rev: RevCommit, mergeQuadruples: MutableList<MergeQuadruple>) {
        logger.info("we found a merge scenario with merged id = [${rev.id.name}]")
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
