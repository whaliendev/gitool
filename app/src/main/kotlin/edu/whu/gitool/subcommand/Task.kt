package edu.whu.gitool.subcommand

fun interface Task<out T> {
    fun run(): Result<T>
}
