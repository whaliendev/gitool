package edu.whu.gitool.subcommand

import java.util.*

interface Task<T : Any> {
    fun execute(): Optional<T>
}
