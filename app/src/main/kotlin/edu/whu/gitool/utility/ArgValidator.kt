package edu.whu.gitool.utility

import com.beust.jcommander.ParameterException
import edu.whu.gitool.utility.GitService.Companion.getCompleteObjectId
import edu.whu.gitool.utility.GitService.Companion.isGitRepo
import edu.whu.gitool.utility.GitService.Companion.precheckObjectId

object ArgValidator {
    fun checkObjectId(objectId: String, projectPath: String): String {
        if (!precheckObjectId(objectId)) {
            throw ParameterException("object id $objectId is illegal")
        }
        if (!isGitRepo(projectPath)) {
            throw ParameterException("$projectPath is not a valid git repo")
        }
        val optResolutions = getCompleteObjectId(objectId, projectPath)
        if (optResolutions.isEmpty) throw ParameterException("object id $objectId or $projectPath is illegal")
        val size = optResolutions.get().size
        if (size == 0) {
            throw ParameterException("object id $objectId doesn't exist in $projectPath")
        } else if (size > 1) {
            throw ParameterException("object id $objectId is not unique in $projectPath")
        }
        return optResolutions.get().first()
    }

    fun checkGitRepo(projectPath: String): Boolean {
        if (!isGitRepo(projectPath)) throw ParameterException("$projectPath is not a valid git repo")
        return true
    }
}
