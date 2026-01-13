package org.potiguaras.supabased.helpers

import com.google.appinventor.components.common.OptionList

enum class RPCFunctionReturnType(private val value: Int) : OptionList<Int> {
    Void(0),
    Record(1),
    Text(2),
    Varchar(3),
    Int4(4), //  'Integer/Int' alias
    Int8(5), // 'Bigint' alias
    Boo(6), // 'Boolean/bool' alias
    Numeric(7), // 'Decimal' alias
    Float4(8), // 'Real' alias
    Float8(9), // 'Float/Double precision' alias
    Date(10),
    Time(11),
    Timestamp(12),
    Timestampz(13),
    Timetz(14),
    Json(15),
    Jsonb(16),
    UUID(17),
    Bytea(18);
    override fun toUnderlyingValue(): Int {
        return value
    }

    companion object {
        private val lookup: Map<Int, RPCFunctionReturnType> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }
        @Suppress("unused")
        fun fromUnderlyingValue(value: Int): RPCFunctionReturnType? = lookup[value]
    }

}