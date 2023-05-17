package edu.whu.gitool.utility

import edu.whu.gitool.model.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
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
        fun checkObjectId(objectId: String): Boolean {
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
            if (!checkObjectId(objectIdStr)) return Optional.empty()
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
    }
}
