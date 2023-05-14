package edu.whu.gitool.model

import java.io.File
import java.nio.file.InvalidPathException
import java.security.InvalidParameterException

/**
 * a project on the machine
 */
data class Project(val name: String, val path: String) {
    init {
        val projectPath = File(path)
        if (!projectPath.exists() || !projectPath.isDirectory) {
            throw InvalidPathException(path, "project path should be a valid project directory")
        }
    }
}

/**
 * Represent a [MergeScenario] with our and their pair
 */
data class MergeScenario(
    val project: Project?,
    val our: CharArray,
    val their: CharArray
) {
    init {
        if (our.size > 40 || their.size > 40) {
            throw InvalidParameterException("commit id of our and their should be valid")
        }
    }

    constructor(project: Project, our: String, their: String) : this(
        project,
        our.toCharArray(),
        their.toCharArray()
    )

    constructor(our: String, their: String) : this(null, our.toCharArray(), their.toCharArray())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MergeScenario

        if (project != other.project) return false
        if (!our.contentEquals(other.our)) return false
        return their.contentEquals(other.their)
    }

    override fun hashCode(): Int {
        var result = project?.hashCode() ?: 0
        result = 31 * result + our.contentHashCode()
        result = 31 * result + their.contentHashCode()
        return result
    }
}

/**
 * Represent a [MergeScenario] with our, base, their triple
 */
data class MergeTriple(
    val mergeScenario: MergeScenario,
  val base: CharArray,
) {
    val our: CharArray
        get() = mergeScenario.our
    val their: CharArray
        get() = mergeScenario.their
    val project: Project?
        get() = mergeScenario.project

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MergeTriple

        if (mergeScenario != other.mergeScenario) return false
        return base.contentEquals(other.base)
    }

    override fun hashCode(): Int {
        var result = mergeScenario.hashCode()
        result = 31 * result + base.contentHashCode()
        return result
    }

}

/**
 * Represent a [MergeScenario] with our, base, their, merged quadruple
 */
data class MergeQuadruple(
    val mergeTriple: MergeTriple,
    val merged: CharArray
) {
    val our: CharArray
        get() = mergeTriple.our
    val their: CharArray
        get() = mergeTriple.their
    val base: CharArray
        get() = mergeTriple.base
    val project: Project?
        get() = mergeTriple.project

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MergeQuadruple

        if (mergeTriple != other.mergeTriple) return false
        return merged.contentEquals(other.merged)
    }

    override fun hashCode(): Int {
        var result = mergeTriple.hashCode()
        result = 31 * result + merged.contentHashCode()
        return result
    }
}
