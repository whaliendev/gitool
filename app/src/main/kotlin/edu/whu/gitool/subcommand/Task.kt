package edu.whu.gitool.subcommand

import java.util.*

interface Task<T : Any> {
    var result: Optional<T>
    fun run(): Result<Boolean>
}
