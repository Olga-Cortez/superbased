package org.potiguaras.supabased.helpers

import com.google.appinventor.components.common.OptionList

enum class FilterOperator(private val value: Int) : OptionList<Int> {
    EQ(0),      // Equals
    NEQ(1),     // Not equals
    GT(2),      // Greater than
    GTE(3),     // Greater than or equal
    LT(4),      // Less than
    LTE(5),     // Less than or equal
    LIKE(6),    // Like (case-sensitive)
    ILIKE(7),   // Like (case-insensitive)
    IS(8),      // IS (for null)
    IN(9),      // In array
    CS(10),     // Contains (for arrays)
    CD(11);     // Contained by (for arrays)

    override fun toUnderlyingValue(): Int = value

    companion object {
        private val lookup: Map<Int, FilterOperator> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }

        fun fromUnderlyingValue(value: Int): FilterOperator? = lookup[value]
    }
}