package com.ufuq.dream

class SettingsDraft(initial: DisplaySettings) {
    var saved = initial
        private set
    var current = initial
        private set
    val dirty get() = current != saved
    fun restore(value: DisplaySettings) { current = value }
    fun markSaved() { saved = current }
    fun revert() { current = saved }
    fun update(value: DisplaySettings) { current = value }
}
