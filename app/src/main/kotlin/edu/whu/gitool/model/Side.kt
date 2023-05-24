package edu.whu.gitool.model

enum class Side(val literal: String) {
    OUR("ours"),
    BASE("bases"),
    THEIR("theirs"),
    CONFLICT("conflict"),   // two-way merge conflict marks
    MERGED("merged")    // no conflict marks
}
