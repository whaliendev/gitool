package edu.whu.gitool.exception

class GitoolBaseException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    constructor(cause: Throwable) : this(null, cause)
}
