package edu.whu.gitool.utility

object Utils {
    fun toPercentage(cnt: Int, total: Int, efficient: Int = 2): String {
        val percentage = (cnt.toDouble() / total * 100).toString()
        return "%.${efficient}f%%".format(percentage.toDouble())
    }
}
