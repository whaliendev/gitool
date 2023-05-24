package edu.whu.gitool.model

enum class ConflictMark(val mark: String) {
    OURS("<<<<<<<"),  // begin of our side code
    BASES("|||||||"), // begin of base side code
    THEIRS("======="), // begin of their side code
    END(">>>>>>>")  // end of conflict marks
}
