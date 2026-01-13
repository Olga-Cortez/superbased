package org.potiguaras.supabased.helpers

import com.google.appinventor.components.common.OptionList

enum class PhoneChannelOptions(private val value: Int) : OptionList<Int> {
    Whatsapp(0),
    Sms(1);

    override fun toUnderlyingValue(): Int = value

    companion object {
        private val lookup: Map<Int, PhoneChannelOptions> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }
        fun fromUnderlyingValue(value: Int): PhoneChannelOptions? = lookup[value]
    }
}