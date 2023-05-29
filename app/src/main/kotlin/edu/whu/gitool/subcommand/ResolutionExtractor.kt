package edu.whu.gitool.subcommand

import com.beust.jcommander.ParameterException
import com.google.gson.GsonBuilder
import edu.whu.gitool.model.*
import edu.whu.gitool.utility.FileUtils
import edu.whu.gitool.utility.GitService
import edu.whu.gitool.utility.Utils
import org.eclipse.jgit.diff.Sequence
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeResult
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.RecursiveMerger
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class ResolutionExtractor(
    private val projectPath: File,
    private val dumpPath: File,
    private val statFile: File?,
    msscli: List<String>,
    private val mssFile: File?,
) : Task<List<String>> {
    private val logger = LoggerFactory.getLogger(ResolutionExtractor::class.java)

    private var conflictsMSs: Int = 0
    private var conflictBlockCnt: Int = 0

    private val statMap: MutableMap<String, Int> = mutableMapOf()
    private var needDumpStat: Boolean = false
    private val mss: MutableList<String> = mutableListOf()

    private var repo: Repository

    init {
        if (!GitService.isGitRepo(projectPath.absolutePath)) {
            throw ParameterException("${projectPath.absolutePath} should be a valid git repo")
        }

        repo = GitService.openGitRepo(projectPath.absolutePath).get()

        if (!dumpPath.exists() || !dumpPath.isDirectory) {
            throw ParameterException("${dumpPath.absolutePath} should be a valid directory")
        }

        if (statFile != null) {
            if (statFile.isDirectory) {
                throw ParameterException("stat file to dump should be a file")
            }
            needDumpStat = true
            logger.info("we'll dump resolution statistics to $statFile with csv header: $STAT_HEADER")
        }
        // whether dumpStat enabled or not, we do the counting internal
        ResolutionStrategy.values().forEach { strategy ->
            statMap[strategy.literal] = 0
        }

        if (mssFile != null && msscli.isNotEmpty()) {
            throw ParameterException("cannot specify merge scenario file and cli option simultaneously")
        }

        if (mssFile != null && mssFile.exists() && mssFile.isFile) {
            mssFile.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    val commitIdList =
                        GitService.getCompleteObjectId(line, projectPath.absolutePath).orElseThrow {
                            ParameterException("$line in $mssFile is not valid commit id in project $projectPath")
                        }
                    if (commitIdList.isEmpty()) {
                        throw ParameterException("$line doesn't exist in project $projectPath")
                    }
                    if (commitIdList.size != 1) {
                        throw ParameterException("$line is not unique in project $projectPath")
                    }
                    mss.add(commitIdList.first())
                }
            }
            logger.info("we'll use merge scenarios collected from $mssFile")
        } else {
            msscli.map { ms ->
                val commitIdList =
                    GitService.getCompleteObjectId(ms, projectPath.absolutePath).orElseThrow {
                        ParameterException("$ms is not valid commit id in project $projectPath")
                    }
                if (commitIdList.isEmpty()) {
                    throw ParameterException("$ms doesn't exist in project $projectPath")
                }
                if (commitIdList.size != 1) {
                    throw ParameterException("$ms is not unique in project $projectPath")
                }
                commitIdList.first()
            }
            mss.clear()
            mss.addAll(msscli)
            logger.info("we'll use merge scenarios parsed from cli")
        }
    }

    override fun execute(): Optional<List<String>> {
        mss.forEach { handleMergeScenario(it) }
        val data = dumpResolutionStats()
        printResolutionStats()
        return Optional.of(data)
    }

    private fun printResolutionStats() {
        logger.info("there are $conflictBlockCnt conflict blocks in this extraction, the statistics are as follows:")

        val ourCnt = statMap[ResolutionStrategy.OUR.literal]!!
        val theirCnt = statMap[ResolutionStrategy.THEIR.literal]!!
        val baseCnt = statMap[ResolutionStrategy.BASE.literal]!!
        val concatCnt = statMap[ResolutionStrategy.CONCAT.literal]!!
        val deletionCnt = statMap[ResolutionStrategy.DELETION.literal]!!
        val interleaveCnt = statMap[ResolutionStrategy.INTERLEAVE.literal]!!
        val newcodeCnt = statMap[ResolutionStrategy.NEWCODE.literal]!!
        val unresolvedCnt = statMap[ResolutionStrategy.UNRESOLVED.literal]!!
        val unknownCnt = statMap[ResolutionStrategy.UNKNOWN.literal]!!

        val col2 = listOf(
            projectPath.absolutePath,
            Utils.toPercentage(ourCnt, conflictBlockCnt),
            Utils.toPercentage(theirCnt, conflictBlockCnt),
            Utils.toPercentage(baseCnt, conflictBlockCnt),
            Utils.toPercentage(concatCnt, conflictBlockCnt),
            Utils.toPercentage(deletionCnt, conflictBlockCnt),
            Utils.toPercentage(interleaveCnt, conflictBlockCnt),
            Utils.toPercentage(newcodeCnt, conflictBlockCnt),
            Utils.toPercentage(unresolvedCnt, conflictBlockCnt),
            Utils.toPercentage(unknownCnt, conflictBlockCnt),
            Utils.toPercentage(conflictBlockCnt, conflictBlockCnt),
            conflictBlockCnt.toString()
        )
        val header = ArrayList(STAT_HEADER)
        header.add("count")
        val col1Width = STAT_HEADER.maxOfOrNull { it.length } ?: 0
        val col2Width = col2.maxOfOrNull { it.length } ?: 0
        val printHeader = header.map { it.padEnd(col1Width) }
        val data = printHeader.zip(col2).map {
            listOf(it.first.padEnd(col1Width), it.second.padStart(col2Width))
        }

        BufferedWriter(OutputStreamWriter(System.out)).use { bw ->
            writeProjStat(bw, data, "\t")
        }
    }

    private fun dumpResolutionStats(): List<String> {
        if (statFile == null) return emptyList()
        val ourCnt = statMap[ResolutionStrategy.OUR.literal]!!
        val theirCnt = statMap[ResolutionStrategy.THEIR.literal]!!
        val baseCnt = statMap[ResolutionStrategy.BASE.literal]!!
        val concatCnt = statMap[ResolutionStrategy.CONCAT.literal]!!
        val deletionCnt = statMap[ResolutionStrategy.DELETION.literal]!!
        val interleaveCnt = statMap[ResolutionStrategy.INTERLEAVE.literal]!!
        val newcodeCnt = statMap[ResolutionStrategy.NEWCODE.literal]!!
        val unresolvedCnt = statMap[ResolutionStrategy.UNRESOLVED.literal]!!
        val unknownCnt = statMap[ResolutionStrategy.UNKNOWN.literal]!!

        val data = listOf(
            projectPath.absolutePath,
            ourCnt.toString(),
            theirCnt.toString(),
            baseCnt.toString(),
            concatCnt.toString(),
            deletionCnt.toString(),
            interleaveCnt.toString(),
            newcodeCnt.toString(),
            unresolvedCnt.toString(),
            unknownCnt.toString(),
            conflictBlockCnt.toString()
        )

        if (statFile.exists()) { // append data
            BufferedWriter(FileWriter(statFile.absolutePath, true)).use { bw ->
                writeProjStat(bw, listOf(data))
            }
        } else {
            BufferedWriter(FileWriter(statFile.absolutePath)).use { bw ->
                bw.write(STAT_HEADER.joinToString(","))
                bw.newLine()
                writeProjStat(bw, listOf(data))
            }
        }

        return data
    }

    private fun writeProjStat(
        bw: BufferedWriter,
        data: List<List<String>>,
        separator: String = ",",
    ) {
        data.forEach { row ->
            bw.write(row.joinToString(separator))
            bw.newLine()
        }
        bw.flush()
    }

    private fun handleMergeScenario(ms: String) {
        logger.info("we're handling merge scenario $ms")
        val mergedHash =
            GitService.getCompleteObjectId(ms, projectPath.absolutePath).get().first()
        RevWalk(repo).use { walk ->
            val mergedCommit = walk.parseCommit(ObjectId.fromString(mergedHash))
            if (mergedCommit.parents.size < 2) {
                logger.error(
                    "the number of parent nodes of $mergedHash is less than 2, " +
                        "obviously it's not a merge node, we'll skip it"
                )
                return
            }

            val projectPathStr = projectPath.absolutePath

            val ourCommit = mergedCommit.parents[0]
            val theirCommit = mergedCommit.parents[1]
            val baseCommit = GitService.getMergeBaseCommit(projectPathStr, ourCommit, theirCommit)

            val msDumpPath = dumpPath.resolve(projectName(projectPathStr))
                .resolve(mergeScenarioName(mergedCommit)).absolutePath
            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true)
            if (!merger.merge(ourCommit, theirCommit)) {
                conflictsMSs++
                val rMerger = merger as RecursiveMerger
                rMerger.mergeResults.forEach { (file: String, result: MergeResult<out Sequence?>) ->
                    if (result.containsConflicts()) {
                        val fileF = File(file)
                        val filename = fileF.name
                        // dir name in following format: '[db-]db_impl.cc'
                        val dirName = if (fileF.parent == null) {
                            filename
                        } else {
                            "[${
                                (fileF.parent + File.separator).replace(
                                    File.separator,
                                    "-"
                                )
                            }]${filename}"
                        }
                        val fileDir = File(msDumpPath).resolve(dirName)
                        FileUtils.prepareDirectories(fileDir.absolutePath)

                        dumpMergeScenarioToLocal(
                            projectPathStr, file, fileDir,
                            mergedCommit, ourCommit, theirCommit, baseCommit,
                        )
                        merge(file, fileDir)
                    }
                }
                extractAndJudge(msDumpPath, projectPathStr)
            } else {
                logger.error("merge of parent commit nodes of $mergedHash succeeded, which is weird, we'll skip this one")
                return
            }
        }
    }

    private fun dumpMergeScenarioToLocal(
        projectPath: String,
        file: String,
        fileDir: File,
        mergedCommit: RevCommit,
        ourCommit: RevCommit,
        theirCommit: RevCommit,
        baseCommit: RevCommit?
    ) {
        val ms = mergedCommit.id.name.substring(0, 8)
        logger.info("about to dump $file of $projectPath at $ms to local...")
        val fileExt = File(file).name.substringAfterLast(".")
        val ourFile = fileDir.resolve(Side.OUR.literal + ".$fileExt")
        val baseFile = fileDir.resolve(Side.BASE.literal + ".$fileExt")
        val theirFile = fileDir.resolve(Side.THEIR.literal + ".$fileExt")
        val mergedFile = fileDir.resolve(Side.MERGED.literal + ".$fileExt")

        GitService.readFileContent(projectPath, file, ourCommit).ifPresent { content ->
            FileUtils.writeFile(ourFile.absolutePath, content)
        }
        if (baseCommit != null) {
            GitService.readFileContent(projectPath, file, baseCommit).ifPresent { content ->
                FileUtils.writeFile(baseFile.absolutePath, content)
            }
        }
        GitService.readFileContent(projectPath, file, theirCommit).ifPresent { content ->
            FileUtils.writeFile(theirFile.absolutePath, content)
        }
        GitService.readFileContent(projectPath, file, mergedCommit).ifPresent { content ->
            FileUtils.writeFile(mergedFile.absolutePath, content)
        }
    }

    private fun merge(
        file: String,
        fileDir: File,
    ) {
        val fileExt = File(file).name.substringAfterLast(".")

        val ourFile = fileDir.resolve(Side.OUR.literal + ".$fileExt")
        val baseFile = fileDir.resolve(Side.BASE.literal + ".$fileExt")
        val theirFile = fileDir.resolve(Side.THEIR.literal + ".$fileExt")
        val conflictFile = fileDir.resolve(Side.CONFLICT.literal + ".$fileExt")

        if (!ourFile.exists() || !baseFile.exists() || !theirFile.exists()) return

        logger.info("about to merge ${ourFile.absolutePath}, ${baseFile.absolutePath} and ${theirFile.absolutePath}")
        val processBuilder = ProcessBuilder(
            "git",
            "merge-file",
            "-p",
            "--diff3",
            ourFile.absolutePath,
            baseFile.absolutePath,
            theirFile.absolutePath
        )
        processBuilder.directory(fileDir)

        val command =
            "git merge-file -p --diff3 ${ourFile.absolutePath} ${baseFile.absolutePath} ${theirFile.absolutePath}"
        val process: Process
        try {
            process = processBuilder.start()
        } catch (ex: IOException) {
            logger.error("failed to run $command, exception: ${ex.message}")
            return
        }

        logger.info("${ourFile.absolutePath} and ${theirFile.absolutePath} merged, output is ${conflictFile.absolutePath}")
        BufferedReader(InputStreamReader(process.inputStream)).use { br ->
            BufferedWriter(FileWriter(conflictFile)).use { writer ->
                br.forEachLine { line ->
                    writer.write(line)
                    writer.newLine()
                }
            }
        }

        try {
            val exited = process.waitFor(10, TimeUnit.MINUTES)
            if (!exited) {
                logger.error("$command didn't finish within 10 minutes, exiting...")
                Runtime.getRuntime().exit(1)
            }
        } catch (ex: IOException) {
            logger.error("failed to run $command, exception: ${ex.message}")
        } catch (ex: InterruptedIOException) {
            logger.error("$command had not finished when the thread waiting for it was interrupted, exiting...")
            Runtime.getRuntime().exit(1)
        }
    }

    private fun extractAndJudge(msDumpPath: String, projectPath: String) {
        logger.info("extracting conflict blocks of $msDumpPath at $projectPath")
        if (!File(msDumpPath).exists()) return
        Files.walk(Path(msDumpPath), 1).forEach { path ->
            val craftedFile = path.toFile().name  // [db-]db_impl.cc
            val filename = path.toFile().name.substringAfterLast("]")  // db_impl.cc
            val fileExt = filename.substringAfterLast(".")
            if (craftedFile.length == 26 && craftedFile.count { it == '-' } == 2) return@forEach

            logger.info("extracting for $craftedFile")
            val mergedFile = path.toFile().resolve("${Side.MERGED.literal}.$fileExt")
            val confFile = path.toFile().resolve("${Side.CONFLICT.literal}.$fileExt")
            if (!mergedFile.exists() || !confFile.exists()) return@forEach
            val mergedLines = FileUtils.readFileAsLines(mergedFile.absolutePath)
            val confLines = FileUtils.readFileAsLines(confFile.absolutePath)

            val conflictBlocks = mutableListOf<ConflictBlock>()
            var indexOfFile = 0
            confLines.withIndex()
                .filter { it.value.startsWith(ConflictMark.OURS.mark) }
                .forEach { indexedValue ->
                    val index = indexedValue.index
                    val lineno = index + 1
                    val cb = ConflictBlock(++indexOfFile)
                    conflictBlockCnt++
                    var j = index
                    var k = index
                    cb.startLine = index
                    while (j + 1 < confLines.size && !confLines[++j].startsWith(ConflictMark.BASES.mark));
                    cb.ours = getCodeSnippets(confLines, k, j)
                    k = j
                    while (j + 1 < confLines.size && !confLines[++j].startsWith(ConflictMark.THEIRS.mark));
                    cb.bases = getCodeSnippets(confLines, k, j)
                    k = j
                    while (j + 1 < confLines.size && !confLines[++j].startsWith(ConflictMark.END.mark));
                    cb.theirs = getCodeSnippets(confLines, k, j)
                    cb.endLine = j
                    conflictBlocks.add(cb)
                }

            logger.info("judging resolution strategy of conflict blocks in file $craftedFile")
            val judge = ResolutionStrategyJudge()
            var startIndex = 0
            conflictBlocks.forEach { cb ->
                val prefix = getCodeSnippets(confLines, -1, cb.startLine)
                val suffix = getCodeSnippets(confLines, cb.endLine, confLines.size)
                val startLine =
                    alignLineScan(prefix, mergedLines, false, startIndex)    // can still be wrong
                startIndex = startLine
                val endLine = alignLineScan(suffix, mergedLines, true, startIndex)
                startIndex = endLine
                cb.merged = getCodeSnippets(mergedLines, startLine, endLine)

                cb.strategy = judge.judge(cb)
                val key = cb.strategy.literal
                var cnt = statMap.getOrDefault(key, 0)
                statMap[key] = ++cnt
            }

            logger.info("dumping resolution stat of file $craftedFile")
            dumpResolutionStrategy(
                projectPath,
                path.toString(),
                File(msDumpPath).resolve(path.toString()),
                conflictBlocks
            )
        }
    }

    private fun alignLineScan(
        anchor: List<String>,
        merged: List<String>,
        isSuffix: Boolean,
        startIndex: Int,
    ): Int {
        val pivot = mutableListOf<String>()
        val source = mutableListOf<String>()
        if (!isSuffix) {
            pivot.addAll(anchor.reversed())
            source.addAll(merged.subList(startIndex, merged.size).reversed())
        } else {
            pivot.addAll(anchor)
            source.addAll(merged.subList(startIndex + 1, merged.size))
        }

        if (pivot.size == 0 && isSuffix) return merged.size
        else if (pivot.size == 0) return -1

        var maxAlign = -1
        var loc = 0
        for (i in source.indices) {
            if (maxAlign > 5) break
            if (pivot[0] == source[i]) {
                var j = i
                var k = 0
                while (++k < pivot.size && ++j < source.size && source[j] == pivot[k]);
                // the closer, the better
                if (k >= maxAlign && !isSuffix) {
                    maxAlign = k
                    loc = i
                } else if (k > maxAlign) {
                    maxAlign = k
                    loc = i
                }
            }
        }

        return if (!isSuffix) merged.size - loc - 1 else startIndex + loc + 1
    }

    private fun dumpResolutionStrategy(
        projectPath: String,
        file: String,
        fileDir: File,
        conflictBlocks: List<ConflictBlock>
    ) {
        val filename = file.substringAfterLast("]")
        val parentDir = "\\[(.*?)\\]".toRegex().find(file)?.groupValues?.getOrElse(1) {
            file.replace(filename, "").replace("[", "").replace("]", "")
        }?.replace("-", File.separator) ?: ""
        val fileF = File(parentDir).resolve(filename)
        val fileResolutions = FileResolution(projectPath, fileF.toString(), conflictBlocks)
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting()
            .disableHtmlEscaping().create()
        val json = gson.toJson(fileResolutions)
        BufferedWriter(FileWriter(fileDir.resolve("resolution-stat.json"))).use { bw ->
            bw.write(json)
        }
    }

    private fun alignLine(
        prefix: List<String>,
        merged: List<String>,
        reverse: Boolean,
        startLine: Int = 0
    ): Int {
        if (prefix.isEmpty()) {
            return if (reverse) {
                -1
            } else {
                merged.size
            }
        }
        val snippet: MutableList<String> = ArrayList()
        val source: MutableList<String> = ArrayList()
        if (reverse) {
            for (i in prefix.indices.reversed()) snippet.add(prefix[i])
            for (i in merged.indices.reversed()) source.add(merged[i])
        } else {
            snippet.addAll(prefix)
            source.addAll(merged)
        }
        var ret = 0
        var maxAlign = -1
        val indices = if (reverse) {
            source.indices
        } else {  // align suffix, start from prefix line
            startLine until source.size
        }
        for (i in indices) {
            if (maxAlign > 5) break
            if (source[i] == snippet[0]) {
                var j = i
                var k = 0
                while (++j < source.size && ++k < snippet.size && source[j] == snippet[k]);
                if (k > maxAlign) {
                    maxAlign = k
                    ret = i
                }
            }
        }
        return if (reverse) {
            source.size - ret - 1
        } else ret
    }

    private fun getCodeSnippets(source: List<String>, start: Int, end: Int): List<String> {
        return if (start >= end) ArrayList() else source.subList(start + 1, end)
    }

    private fun projectName(projectPath: String) = File(projectPath).name

    private fun mergeScenarioName(mergedCommit: RevCommit): String {
        val p1 = mergedCommit.parents[0]
        val p2 = mergedCommit.parents[1]
        return "${p1.id.name.substring(0, 8)}-" +
            "${p2.id.name.substring(0, 8)}-${mergedCommit.id.name.substring(0, 8)}"
    }

    companion object {
        private val STAT_HEADER =
            listOf(
                "project_path",
                "our",
                "their",
                "base",
                "concat",
                "deletion",
                "interleave",
                "newcode",
                "unresolved",
                "unknown",
                "total"
            )
    }
}

class ResolutionStrategyJudge {
    fun judge(cb: ConflictBlock): ResolutionStrategy {
        if (isUnresolved(cb.merged)) return ResolutionStrategy.UNRESOLVED
        if (acceptOurs(cb)) return ResolutionStrategy.OUR
        if (acceptTheirs(cb)) return ResolutionStrategy.THEIR
        if (acceptBases(cb)) return ResolutionStrategy.BASE
        if (deleteAllRevisions(cb)) return ResolutionStrategy.DELETION

        val deflatedOurs = deflatedLines(cb.ours)
        val deflatedTheirs = deflatedLines(cb.theirs)
        val deflatedBases = deflatedLines(cb.bases)
        val deflatedMerged = deflatedLines(cb.merged)
        if (concatOfTwoRevisions(deflatedOurs, deflatedTheirs, deflatedBases, deflatedMerged)) {
            return ResolutionStrategy.CONCAT
        }
        if (interleaveOfRevisions(deflatedOurs, deflatedTheirs, deflatedBases, deflatedMerged)) {
            return ResolutionStrategy.INTERLEAVE
        }
        if (newcodeIntroduced(deflatedOurs, deflatedTheirs, deflatedBases, deflatedMerged)) {
            return ResolutionStrategy.NEWCODE
        }
        return ResolutionStrategy.UNKNOWN
    }


    companion object {
        private fun newcodeIntroduced(
            deflatedOurs: List<String>,
            deflatedTheirs: List<String>,
            deflatedBases: List<String>,
            deflatedMerged: List<String>
        ): Boolean {
            val allSet = HashSet<String>()
            allSet.addAll(deflatedOurs)
            allSet.addAll(deflatedBases)
            allSet.addAll(deflatedTheirs)
            return deflatedMerged.any { line ->
                line !in allSet
            }
        }

        private fun interleaveOfRevisions(
            deflatedOurs: List<String>,
            deflatedTheirs: List<String>,
            deflatedBases: List<String>,
            deflatedMerged: List<String>
        ): Boolean {
            val allSet = HashSet<String>()
            allSet.addAll(deflatedOurs)
            allSet.addAll(deflatedBases)
            allSet.addAll(deflatedTheirs)
            return deflatedMerged.all { line ->
                line in allSet
            }
        }

        private fun concatOfTwoRevisions(
            deflatedOurs: List<String>,
            deflatedTheirs: List<String>,
            deflatedBases: List<String>,
            deflatedMerged: List<String>
        ): Boolean {
            if (deflatedOurs.isEmpty() || deflatedTheirs.isEmpty()) return false
            if ((deflatedOurs.size + deflatedTheirs.size != deflatedMerged.size) &&
                (deflatedOurs.size + deflatedBases.size != deflatedMerged.size) &&
                (deflatedTheirs.size + deflatedBases.size != deflatedMerged.size)
            ) {
                return false
            }
            return (deflatedOurs.plus(deflatedTheirs) == deflatedMerged) ||
                (deflatedTheirs.plus(deflatedOurs) == deflatedMerged) ||
                (deflatedOurs.plus(deflatedBases) == deflatedMerged) ||
                (deflatedBases.plus(deflatedOurs) == deflatedMerged) ||
                (deflatedTheirs.plus(deflatedBases) == deflatedMerged) ||
                (deflatedBases.plus(deflatedTheirs) == deflatedMerged)
        }

        private fun deleteAllRevisions(cb: ConflictBlock): Boolean {
            return (cb.ours.isNotEmpty() || cb.bases.isNotEmpty() || cb.theirs.isNotEmpty()) &&
                cb.merged.isEmpty()
        }

        private fun acceptOurs(cb: ConflictBlock) = cb.ours == cb.merged
        private fun acceptBases(cb: ConflictBlock) = cb.bases == cb.merged
        private fun acceptTheirs(cb: ConflictBlock) = cb.theirs == cb.merged
        private fun deflatedLines(lines: List<String>): List<String> =
            lines.filter { it.isNotEmpty() }

        private fun isUnresolved(merged: List<String>): Boolean {
            return merged.any { line ->
                line.startsWith(ConflictMark.OURS.mark) || line.startsWith(ConflictMark.BASES.mark)
                    || line.startsWith(ConflictMark.THEIRS.mark) || line.startsWith(ConflictMark.END.mark)
            }
        }
    }
}
