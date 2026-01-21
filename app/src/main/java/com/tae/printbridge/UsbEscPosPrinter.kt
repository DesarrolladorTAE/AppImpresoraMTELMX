package com.tae.printbridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.util.Log
import java.nio.charset.Charset

class UsbEscPosPrinter(
    private val context: Context,
    private val permissionAction: String
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        private const val TAG = "UsbEscPosPrinter"
        private const val TIMEOUT_MS = 4000
    }

    /**
     * ✅ NUEVO: Imprime bytes ESC/POS crudos (soporta: texto + imagen + QR + cajón + corte)
     */
    fun printRaw(bytes: ByteArray): Boolean {
        val device = findCandidateDevice() ?: run {
            Log.e(TAG, "No hay impresora USB conectada")
            return false
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            Log.e(TAG, "Sin permiso USB. Se solicitó permiso, reintenta la impresión.")
            return false
        }

        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "No se pudo abrir conexión USB")
            return false
        }

        try {
            val intf = pickInterface(device) ?: run {
                Log.e(TAG, "No se encontró interfaz USB imprimible")
                return false
            }

            if (!conn.claimInterface(intf, true)) {
                Log.e(TAG, "No se pudo reclamar la interfaz USB")
                return false
            }

            val epOut = pickOutEndpoint(intf) ?: run {
                Log.e(TAG, "No se encontró endpoint OUT")
                return false
            }

            val sent = conn.bulkTransfer(epOut, bytes, bytes.size, TIMEOUT_MS)
            Log.i(TAG, "bulkTransfer RAW sent=$sent bytes=${bytes.size}")
            return sent > 0

        } catch (e: Exception) {
            Log.e(TAG, "Error imprimiendo RAW: ${e.message}", e)
            return false
        } finally {
            try { conn.close() } catch (_: Exception) {}
        }
    }

    /**
     * (Opcional) Para compatibilidad: arma ESC/POS básico desde texto
     */
    fun printText(text: String, cut: Boolean = true): Boolean {
        val payload = buildEscPos(text, cut)
        return printRaw(payload)
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(permissionAction),
            PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, intent)
    }

    private fun findCandidateDevice(): UsbDevice? {
        val devices = usbManager.deviceList.values.toList()
        if (devices.isEmpty()) return null

        // Prioriza el primero que tenga endpoint OUT (típico de impresoras)
        return devices.firstOrNull { dev ->
            (0 until dev.interfaceCount).any { idx ->
                val intf = dev.getInterface(idx)
                (0 until intf.endpointCount).any { ep ->
                    val endpoint = intf.getEndpoint(ep)
                    endpoint.direction == UsbConstants.USB_DIR_OUT
                }
            }
        } ?: devices.first()
    }

    private fun pickInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT) return intf
            }
        }
        return null
    }

    private fun pickOutEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.direction == UsbConstants.USB_DIR_OUT) return ep
        }
        return null
    }

    private fun buildEscPos(text: String, cut: Boolean): ByteArray {
        val clean = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val init = byteArrayOf(0x1B, 0x40)  // ESC @ reset
        val body = clean.toByteArray(Charset.forName("windows-1252"))
        val feed = "\n\n".toByteArray()

        val cutCmd = if (cut) byteArrayOf(0x1D, 0x56, 0x00) else byteArrayOf()
        val lf = "\n".toByteArray()

        return init + body + feed + cutCmd + lf
    }
}
