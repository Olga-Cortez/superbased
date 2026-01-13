package org.potiguaras.supabased.helpers
import com.google.appinventor.components.common.OptionList

enum class OTPType(private val value: Int) : OptionList<Int> {
    INVITE(1),
    RECOVERY(2),
    EMAIL_CHANGE(3),
    EMAIL(4),
    SMS(5),
    PHONE_CHANGE(6);

    override fun toUnderlyingValue(): Int = value
    companion object {
        private val lookup: Map<Int, OTPType> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }
        fun fromUnderlyingValue(value: Int): OTPType? = lookup[value]
    }
}
