package me.him188.ani.app.utils

import me.him188.ani.utils.platform.format2f
import me.him188.ani.utils.serialization.BigNum

fun Float.formatSpeedValue(): String = String.format2f(this).let {
    it.padEnd(it.indexOf('.') + 3, '0')
}

fun Int.fixToString(length: Int, prefix: Char = '0'): String {
    val str = this.toString()
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}

fun Long.fixToString(length: Int, prefix: Char = '0'): String {
    val str = this.toString()
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}

fun Float.fixToString(length: Int, prefix: Char = '0'): String {
    val str = this.toString()
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}

fun String.fixToString(length: Int, prefix: Char): String {
    val str = this
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}

fun BigNum.fixToString(length: Int, prefix: Char): String {
    val str = this.toString()
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}
