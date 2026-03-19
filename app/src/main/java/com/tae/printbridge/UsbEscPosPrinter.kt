package com.tae.printbridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.nio.charset.Charset

class UsbEscPosPrinter(
    private val context: Context,
    private val permissionAction: String
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        private const val TAG = "UsbEscPosPrinter"
        private const val TRANSFER_TIMEOUT_MS = 8000
        private const val CHUNK_SIZE = 4096
    }

    /**
     * Imprime bytes ESC/POS crudos (texto + imagen + QR + cajón + corte)
     */
    fun printRaw(bytes: ByteArray): Boolean {
        val device = findCandidateDevice() ?: run {
            Log.e(TAG, "No hay impresora USB conectada")
            return false
        }

        Log.i(
            TAG,
            "Dispositivo detectado: name=${device.deviceName}, vendorId=${device.vendorId}, productId=${device.productId}"
        )

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            Log.e(TAG, "Sin permiso USB. Se solicitó permiso, reintenta la impresión.")
            return false
        }

        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "No se pudo abrir conexión USB")
            return false
        }

        var claimed = false
        var intf: UsbInterface? = null

        try {
            intf = pickInterface(device) ?: run {
                Log.e(TAG, "No se encontró interfaz USB imprimible")
                return false
            }

            if (!conn.claimInterface(intf, true)) {
                Log.e(TAG, "No se pudo reclamar la interfaz USB")
                return false
            }
            claimed = true

            val epOut = pickOutEndpoint(intf) ?: run {
                Log.e(TAG, "No se encontró endpoint OUT")
                return false
            }

            Log.i(
                TAG,
                "Imprimiendo RAW: totalBytes=${bytes.size}, endpoint=${epOut.address}, chunkSize=$CHUNK_SIZE"
            )

            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(CHUNK_SIZE, bytes.size - offset)
                val chunk = bytes.copyOfRange(offset, offset + len)

                val sent = conn.bulkTransfer(epOut, chunk, chunk.size, TRANSFER_TIMEOUT_MS)

                if (sent <= 0) {
                    Log.e(TAG, "Falló bulkTransfer: offset=$offset len=$len sent=$sent")
                    return false
                }

                Log.d(TAG, "Chunk enviado: offset=$offset len=$len sent=$sent")
                offset += sent
            }

            Log.i(TAG, "Impresión RAW completada. bytes=${bytes.size}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error imprimiendo RAW: ${e.message}", e)
            return false
        } finally {
            try {
                if (claimed && intf != null) {
                    conn.releaseInterface(intf)
                }
            } catch (_: Exception) {
            }

            try {
                conn.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Compatibilidad: arma ESC/POS básico desde texto
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

        return devices.firstOrNull { dev ->
            (0 until dev.interfaceCount).any { idx ->
                val intf = dev.getInterface(idx)
                (0 until intf.endpointCount).any { epIndex ->
                    val endpoint = intf.getEndpoint(epIndex)
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
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    return intf
                }
            }
        }
        return null
    }

    private fun pickOutEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                return ep
            }
        }
        return null
    }

    private fun buildEscPos(text: String, cut: Boolean): ByteArray {
        val clean = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val init = byteArrayOf(0x1B, 0x40)
        val body = clean.toByteArray(Charset.forName("windows-1252"))
        val feed = "\n\n".toByteArray(Charset.forName("windows-1252"))
        val cutCmd = if (cut) byteArrayOf(0x1D, 0x56, 0x00) else byteArrayOf()
        val lf = "\n".toByteArray(Charset.forName("windows-1252"))

        return init + body + feed + cutCmd + lf
    }
}