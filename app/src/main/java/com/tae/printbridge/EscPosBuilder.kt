package com.tae.printbridge

import java.nio.charset.Charset

object EscPosBuilder {
    fun text(text: String, cut: Boolean): ByteArray {
        val clean = text.replace("\r\n", "\n").replace("\r", "\n")

        val init = byteArrayOf(0x1B, 0x40) // ESC @ reset
        val feed = "\n\n".toByteArray()
        val lf = "\n".toByteArray()

        val body = clean.toByteArray(Charset.forName("windows-1252"))

        val cutCmd = if (cut) byteArrayOf(0x1D, 0x56, 0x00) else byteArrayOf()

        return init + body + feed + cutCmd + lf
    }
}
