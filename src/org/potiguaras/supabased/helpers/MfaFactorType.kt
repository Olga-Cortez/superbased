package org.potiguaras.supabased.helpers
import com.google.appinventor.components.common.OptionList

enum class MfaFactorTypeOptions(private val value: Int) : OptionList<Int> {
    TOTP(0),
    PHONE(1);

    override fun toUnderlyingValue(): Int = value
    companion object {
        private val lookup: Map<Int, MfaFactorType> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }
        fun fromUnderlyingValue(value: Int): MfaFactorType? = lookup[value]
    }
}