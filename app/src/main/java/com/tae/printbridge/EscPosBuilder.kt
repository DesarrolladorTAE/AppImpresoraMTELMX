package com.tae.printbridge

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import kotlin.math.max

object EscPosBuilder {

    private val ESC = 0x1B.toByte()
    private val GS  = 0x1D.toByte()

    // ✅ Para 80mm: 48 columnas (Font A) típico
    const val COLS_80MM = 48

    // ✅ Para 80mm: 576 dots de ancho típico
    const val DOTS_80MM = 576

    // =========================
    // Básicos
    // =========================
    fun init(): ByteArray = byteArrayOf(ESC, 0x40) // ESC @ reset

    fun lf(lines: Int = 1): ByteArray = "\n".repeat(max(1, lines)).toByteArray()

    fun cutFull(): ByteArray = byteArrayOf(GS, 0x56, 0x00)              // GS V 0
    fun cutPartial(): ByteArray = byteArrayOf(GS, 0x56, 0x42, 0x00)      // GS V B 0

    fun alignLeft(): ByteArray = byteArrayOf(ESC, 'a'.code.toByte(), 0x00)
    fun alignCenter(): ByteArray = byteArrayOf(ESC, 'a'.code.toByte(), 0x01)
    fun alignRight(): ByteArray = byteArrayOf(ESC, 'a'.code.toByte(), 0x02)

    fun bold(on: Boolean): ByteArray = byteArrayOf(ESC, 'E'.code.toByte(), if (on) 0x01 else 0x00)

    // =========================
    // ✅ Abrir cajón (Cash drawer)
    // ESC p m t1 t2
    // m=0 (pin2) / m=1 (pin5)
    // =========================
    fun openDrawer(m: Int = 0, t1: Int = 25, t2: Int = 250): ByteArray {
        return byteArrayOf(
            ESC, 'p'.code.toByte(),
            (m and 0xFF).toByte(),
            (t1 and 0xFF).toByte(),
            (t2 and 0xFF).toByte()
        )
    }

    // =========================
    // Texto (Windows-1252)
    // =========================
    fun text(text: String): ByteArray {
        val clean = text.replace("\r\n", "\n").replace("\r", "\n")
        return clean.toByteArray(Charset.forName("windows-1252"))
    }

    // =========================
    // Helpers para 80mm (48 cols)
    // =========================
    fun hr(cols: Int = COLS_80MM, ch: Char = '-'): ByteArray {
        return (ch.toString().repeat(cols) + "\n").toByteArray(Charset.forName("windows-1252"))
    }

    fun lineLR(left: String, right: String, cols: Int = COLS_80MM): ByteArray {
        val l = left.take(cols)
        val r = right.take(cols)
        val spaces = (cols - l.length - r.length).coerceAtLeast(1)
        return (l + " ".repeat(spaces) + r + "\n").toByteArray(Charset.forName("windows-1252"))
    }

    // =========================
    // ✅ Imagen (dataURL/base64) -> ESC/POS raster GS v 0
    // =========================
    fun imageFromBase64(base64OrDataUrl: String, maxDotsWidth: Int = DOTS_80MM): ByteArray {
        val clean = base64OrDataUrl.substringAfter("base64,", base64OrDataUrl)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ByteArray(0)
        val scaled = scaleToWidth(bmp, maxDotsWidth)
        return bitmapToRasterEscPos(scaled)
    }

    private fun scaleToWidth(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width.toFloat()
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxWidth, h, true)
    }

    private fun bitmapToRasterEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        val bytesPerRow = (width + 7) / 8
        val imageBytes = ByteArray(bytesPerRow * height)

        var idx = 0
        for (y in 0 until height) {
            for (xByte in 0 until bytesPerRow) {
                var b = 0
                for (bit in 0..7) {
                    val x = xByte * 8 + bit
                    val pixelOn = if (x < width) isBlack(bitmap.getPixel(x, y)) else false
                    if (pixelOn) b = b or (0x80 shr bit)
                }
                imageBytes[idx++] = b.toByte()
            }
        }

        val xL = (bytesPerRow and 0xFF).toByte()
        val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        val header = byteArrayOf(GS, 'v'.code.toByte(), '0'.code.toByte(), 0x00, xL, xH, yL, yH)
        return header + imageBytes + "\n".toByteArray()
    }

    private fun isBlack(colorInt: Int): Boolean {
        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)
        val lum = (0.299 * r + 0.587 * g + 0.114 * b)
        return lum < 160
    }

    // =========================
    // ✅ QR ESC/POS (GS ( k) - modelo 2
    // =========================

    private fun gsK(pL: Int, pH: Int, cn: Int, fn: Int, m: Int, data: ByteArray? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(GS.toInt())
        out.write('('.code)
        out.write('k'.code)
        out.write(pL)
        out.write(pH)
        out.write(cn)
        out.write(fn)
        out.write(m)
        if (data != null) out.write(data)
        return out.toByteArray()
    }

    /**
     * Imprime QR nativo (si la impresora lo soporta).
     *
     * @param content Texto a meter en QR (url, folio, etc.)
     * @param size 1..16 (recomendado 6-10 para 80mm)
     * @param ecc  48(L),49(M),50(Q),51(H)  -> default M(49)
     */
    fun qr(content: String, size: Int = 8, ecc: Int = 49): ByteArray {
        val data = content.toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream()

        // 1) Select model: 2
        // GS ( k 4 0 49 65 50 0
        out.write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // 2) Set module size
        val s = size.coerceIn(1, 16)
        // GS ( k 3 0 49 67 n
        out.write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 0x03, 0x00, 0x31, 0x43, s.toByte()))

        // 3) Set error correction level
        val e = ecc.coerceIn(48, 51)
        // GS ( k 3 0 49 69 n
        out.write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 0x03, 0x00, 0x31, 0x45, e.toByte()))

        // 4) Store data
        val len = data.size + 3
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()
        // GS ( k pL pH 49 80 48 [data]
        out.write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), pL, pH, 0x31, 0x50, 0x30))
        out.write(data)

        // 5) Print QR
        // GS ( k 3 0 49 81 48
        out.write(byteArrayOf(GS, '('.code.toByte(), 'k'.code.toByte(), 0x03, 0x00, 0x31, 0x51, 0x30))

        out.write("\n".toByteArray())
        return out.toByteArray()
    }

    // =========================
    // ✅ Arma un ticket completo (texto + opcional imagen + opcional QR + cajón + corte)
    // =========================
    fun build(
        text: String,
        cut: Boolean = true,
        openDrawer: Boolean = false,
        drawerPin: Int = 0,
        imageBase64: String? = null,
        qrText: String? = null,          // ✅ NUEVO
        qrSize: Int = 8,                 // ✅ NUEVO
        qrEcc: Int = 49,                 // ✅ NUEVO (M)
        dotsWidth: Int = DOTS_80MM,
        cols: Int = COLS_80MM
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(init())

        if (openDrawer) out.write(openDrawer(m = drawerPin))

        // Logo/imagen
        if (!imageBase64.isNullOrBlank()) {
            out.write(alignCenter())
            out.write(imageFromBase64(imageBase64, maxDotsWidth = dotsWidth))
            out.write(lf(1))
            out.write(alignLeft())
        }

        // Texto del ticket
        out.write(text(text))
        out.write(lf(1))

        // ✅ QR (nativo)
        if (!qrText.isNullOrBlank()) {
            out.write(alignCenter())
            out.write(qr(qrText, size = qrSize, ecc = qrEcc))
            out.write(lf(1))
            out.write(alignLeft())
        }

        out.write(lf(2))

        if (cut) out.write(cutPartial())

        return out.toByteArray()
    }
}
