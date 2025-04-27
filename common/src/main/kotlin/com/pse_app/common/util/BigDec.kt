package com.pse_app.common.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Wrapper for [BigDecimal].
 * @param value The [BigDecimal] to wrap.
 */
@Serializable(with = BigDecSerializer::class)
class BigDec(val value: BigDecimal) : Number() {
    constructor(value: Long): this(BigDecimal.valueOf(value))
    constructor(value: String): this(BigDecimal(value))
    constructor(unscaledVal: BigInteger?, scale: Int): this(BigDecimal(unscaledVal, scale))
    // Hello you! If you are here wondering if you could add a constructor that accepts a double:
    // DO NOT DO THAT. Take a look at this short example to see why this is cursed:
    // new BigDecimal(0.1) == 0.1000000000000000055511151231257827021181583404541015625
    // but
    // BigDecimal.valueOf(0.1) == 0.1 != new BigDecimal(0.1)
    // Explicit is better than implicit and due to the nature of doubles, there is no correct way to
    // convert one to a BigDecimal, there are just more and less wrong ways.
    // If you need fractional numbers, use the String constructor, the less cursed BigDecimal.valueOf
    // does that anyway and the possible performance gains are not worth the time you'll spend looking
    // for the causes of your bugs later. Thank you!

    override fun equals(other: Any?): Boolean = when (other) {
        is BigDec -> this.value.compareTo(other.value) == 0
        else -> false
    }

    override fun hashCode(): Int = value.toDouble().hashCode()
    override fun toString(): String = this.value.stripTrailingZeros().toString()

    override fun toDouble(): Double = value.toDouble()
    override fun toByte(): Byte = value.toByte()
    override fun toFloat(): Float = value.toFloat()
    override fun toInt(): Int = value.toInt()
    override fun toLong(): Long = value.toLong()
    override fun toShort(): Short = value.toShort()

    fun abs() = BigDec(value.abs())

    operator fun plus(other: BigDec): BigDec {
        return BigDec(value + other.value)
    }

    operator fun minus(other: BigDec): BigDec {
        return BigDec(value - other.value)
    }

    operator fun times(other: BigDec): BigDec {
        return BigDec(value * other.value)
    }

    operator fun compareTo(other: BigDec): Int {
        return value.compareTo(other.value)
    }

    operator fun unaryMinus(): BigDec {
        return BigDec(-value)
    }

    companion object {
        val ZERO : BigDec = BigDec(BigDecimal.ZERO)
    }
}

/**
 * [KSerializer] for [BigDec].
 */
object BigDecSerializer : KSerializer<BigDec> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDec", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDec) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): BigDec = BigDec(decoder.decodeString())
}
