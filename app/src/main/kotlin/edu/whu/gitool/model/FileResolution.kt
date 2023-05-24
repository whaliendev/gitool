package edu.whu.gitool.model

import com.google.gson.annotations.Expose

data class BlockResolution(
    val index: Int,
    val strategy: String,
    val code: String
)

data class FileResolution(
    @Expose val projectPath: String,
    @Expose val file: String,
    @Expose val resolutions: List<ConflictBlock>
)
