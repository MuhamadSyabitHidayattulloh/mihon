package eu.kanade.presentation.more.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference

fun Preference<Int>.asString(): Preference<String> {
    val intPref = this
    return object : Preference<String> {
        override fun key(): String = intPref.key()
        override fun defaultValue(): String = String.format("%08X", intPref.defaultValue())
        override fun get(): String = String.format("%08X", intPref.get())
        override fun isSet(): Boolean = intPref.isSet()
        override suspend fun await(): String = String.format("%08X", intPref.await())
        override fun changes(): Flow<String> = intPref.changes().map { String.format("%08X", it) }
        override fun set(value: String) {
            intPref.set(value.toLongOrNull(16)?.toInt() ?: intPref.defaultValue())
        }
    }
}
