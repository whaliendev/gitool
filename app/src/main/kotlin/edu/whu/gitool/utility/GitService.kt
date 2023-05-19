package edu.whu.gitool.utility

import edu.whu.gitool.model.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.Sequence
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeResult
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.RecursiveMerger
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.util.*

class GitService {
    companion object {
        private const val GIT_DIR_NAME = ".git"
        const val SHA1_ID_LENGTH = 40

        /**
         * check if every char of [objectId] is hex digit
         */
        fun isHexString(objectId: String): Boolean {
            var res = true
            for (ch in objectId) {
                if (ch in '0'..'9' || ch in 'a'..'f')
                    continue
                res = false
                break
            }
            return res
        }

        /**
         * check if a directory is a valid git repo
         */
        fun isGitRepo(dirPath: String): Boolean {
            val repoDir = File(File(dirPath).absolutePath, GIT_DIR_NAME)
            if (!repoDir.exists() || !repoDir.isDirectory) {
                return false
            }
            try {
                Git.open(repoDir)
            } catch (e: Throwable) {
                return false
            }
            return true
        }

        /**
         * check if a [Project] is a valid git repo
         */
        fun isGitProject(project: Project) = isGitRepo(project.path)

        /**
         * check if [objectId] conform to the basic requirement of SHA-1 abstraction
         */
        fun precheckObjectId(objectId: String): Boolean {
            if (objectId.length > SHA1_ID_LENGTH || !isHexString(objectId)) {
                return false
            }
            return !(objectId.length == SHA1_ID_LENGTH && !ObjectId.isId(objectId))
        }


        /**
         * open a git repo. if failed, return optional of empty; otherwise return the corresponding
         * optional of [Repository]
         */
        fun openGitRepo(path: String): Optional<Repository> {
            if (!isGitRepo(path)) return Optional.empty()
            val repo = FileRepositoryBuilder().apply {
                gitDir = File(path, GIT_DIR_NAME)
            }.build()
            return Optional.of(repo)
        }

        /**
         * get complete object id in a git repo, return Optional.empty() if either [objectIdStr] or [path]
         * is invalid, else Optional of list of complete object id
         * you can judge concrete scenario based on the size of List<String>. if the size is 0, the
         * short commit id doesn't exist in this repo; if the size is 1, the short commit id is unique
         * in this repo; otherwise, the short commit id is not unique
         *
         */
        fun getCompleteObjectId(objectIdStr: String, path: String): Optional<List<String>> {
            if (!precheckObjectId(objectIdStr)) return Optional.empty()
            if (!isGitRepo(path)) return Optional.empty()
            val repoOpt = openGitRepo(path)
            if (repoOpt.isEmpty) return Optional.empty()
            repoOpt.get().use { repo ->
                repo.newObjectReader().use { or ->
                    val abbreviatedObjectId = AbbreviatedObjectId.fromString(objectIdStr)
                    return Optional.of(or.resolve(abbreviatedObjectId).map { it.name })
                }
            }
        }

        /**
         * ensure [projectPath] is a valid git repo and [ourCommit] and [theirCommit] is valid git commit id
         */
        fun getMergeBase(
            projectPath: String,
            ourCommit: RevCommit,
            theirCommit: RevCommit
        ): String {
            openGitRepo(projectPath).get().use { repo ->
                RevWalk(repo).apply {
                    revFilter = RevFilter.MERGE_BASE
                }.use { walk ->
                    // backup before use for a thread-safe RevCommit
                    walk.markStart(walk.parseCommit(ourCommit))
                    walk.markStart(walk.parseCommit(theirCommit))

                    val mergeBase = walk.next()
                    return if (mergeBase != null) {
                        mergeBase.name
                    } else {
                        ""
                    }
                }
            }

        }

        class MergeConflictsFilter(
            private val repo: FileRepository,
            private val fileExts: Set<String> = setOf()
        ) : RevFilter() {

            /**
             * Determine if the supplied commit should be included in results.
             *
             * @param walker
             * the active walker this filter is being invoked from within.
             * @param cmit
             * the commit currently being tested. The commit has been parsed
             * and its body is available for inspection only if the filter
             * returns true from [.requiresCommitBody].
             * @return true to include this commit in the results; false to have this
             * commit be omitted entirely from the results.
             * @throws org.eclipse.jgit.errors.StopWalkException
             * the filter knows for certain that no additional commits can
             * ever match, and the current commit doesn't match either. The
             * walk is halted and no more results are provided.
             * @throws org.eclipse.jgit.errors.MissingObjectException
             * an object the filter needs to consult to determine its answer
             * does not exist in the Git repository the walker is operating
             * on. Filtering this commit is impossible without the object.
             * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
             * an object the filter needed to consult was not of the
             * expected object type. This usually indicates a corrupt
             * repository, as an object link is referencing the wrong type.
             * @throws java.io.IOException
             * a loose object or pack file could not be read to obtain data
             * necessary for the filter to make its decision.
             */
            override fun include(walker: RevWalk?, cmit: RevCommit?): Boolean {
                if (cmit == null) return false
                if (cmit.parents.size < 2) return false
                val p1 = cmit.parents[0]
                val p2 = cmit.parents[1]
                val merger = MergeStrategy.RECURSIVE.newMerger(repo, true)
                if (!merger.merge(p1, p2)) {
                    if (fileExts.isEmpty()) return true
                    val rMerger = merger as RecursiveMerger
                    rMerger.mergeResults.forEach { (file: String, result: MergeResult<out Sequence?>) ->
                        if (file.substringAfterLast(".") in fileExts) {
                            return true
                        }
                    }
                    return false
                }
                // successfully merge
                return false
            }

            /**
             * {@inheritDoc}
             *
             *
             * Clone this revision filter, including its parameters.
             *
             *
             * This is a deep clone. If this filter embeds objects or other filters it
             * must also clone those, to ensure the instances do not share mutable data.
             */
            override fun clone(): RevFilter {
                return this
            }

            override fun requiresCommitBody() = false

            override fun toString() = "MERGE_CONFLICTS with FILE_EXTENSION_FILTER $fileExts"
        }
    }
}
