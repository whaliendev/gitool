package edu.whu.gitool.model

import edu.whu.gitool.utility.GitService.Companion.SHA1_ID_LENGTH
import edu.whu.gitool.utility.GitService.Companion.isHexString
import java.io.File
import java.nio.file.InvalidPathException

/**
 * a project on host machine
 */
data class Project(val name: String, val path: String) {
    init {
        val projectPath = File(path)
        if (!projectPath.isDirectory) {
            throw InvalidPathException(path, "project path should be a valid project directory")
        }
    }
}

/**
 * Represent a [MergeScenario] with our and their pair
 */
data class MergeScenario(
    val project: Project?,
    val our: String,
    val their: String
) {
    init {
        if (our.length > SHA1_ID_LENGTH || their.length > SHA1_ID_LENGTH || !isHexString(our) ||
            !isHexString(their)
        ) {
            throw IllegalArgumentException("commit id of our and their should be a valid SHA-1 abstraction")
        }
    }

    constructor(our: String, their: String) : this(null, our, their)

    constructor(name: String, path: String, our: String, their: String) : this(
        Project(name, path), our, their
    )
}

/**
 * Represent a [MergeScenario] with our, base, their triple
 */
data class MergeTriple(
    val mergeScenario: MergeScenario,
    val base: String,
) {
    init {
        if (base.length > SHA1_ID_LENGTH || !isHexString(base)) {
            throw IllegalArgumentException("commit id of base should be a valid SHA-1 abstraction")
        }
    }

    constructor(our: String, base: String, their: String) : this(MergeScenario(our, their), base)

    constructor(name: String, path: String, our: String, base: String, their: String) : this(
        MergeScenario(name, path, our, their),
        base
    )

    val our: String
        get() = mergeScenario.our
    val their: String
        get() = mergeScenario.their
    val project: Project?
        get() = mergeScenario.project
}

/**
 * Represent a [MergeScenario] with our, base, their, merged quadruple
 */
data class MergeQuadruple(
    val mergeTriple: MergeTriple,
    val merged: String
) {
    init {
        if (merged.length > SHA1_ID_LENGTH || !isHexString(merged)) {
            throw IllegalArgumentException("commit id of merged should be a valid SHA-1 abstraction")
        }
    }

    constructor(merged: String, our: String, base: String, their: String) : this(
        MergeTriple(
            MergeScenario(our, their), base
        ), merged
    )

    constructor(
        name: String, path: String, merged: String, our: String, base: String, their:
        String
    ) : this(MergeTriple(name, path, our, base, their), merged)

    val our: String
        get() = mergeTriple.our
    val their: String
        get() = mergeTriple.their
    val base: String
        get() = mergeTriple.base
    val project: Project?
        get() = mergeTriple.project
}
