// CountOptions.kt
package org.potiguaras.supabased.helpers

import com.google.appinventor.components.common.OptionList

enum class CountOptions(private val value: Int) : OptionList<Int> {
    NONE(0),
    EXACT(1),
    PLANNED(2),
    ESTIMATED(3);

    override fun toUnderlyingValue(): Int = value

    companion object {
        private val lookup: Map<Int, CountOptions> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }

        fun fromUnderlyingValue(value: Int): CountOptions? = lookup[value]
    }
}