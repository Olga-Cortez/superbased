package org.potiguaras.supabased.helpers

import com.google.appinventor.components.common.OptionList

enum class OrderOptions(private val value: Int) : OptionList<Int> {
    ASC(0),
    DESC(1);

    override fun toUnderlyingValue(): Int = value

    companion object {
        private val lookup: Map<Int, OrderOptions> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }

        fun fromUnderlyingValue(value: Int): OrderOptions? = lookup[value]
    }
}