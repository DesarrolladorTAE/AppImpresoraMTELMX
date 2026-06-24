package com.tae.printbridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

class AndroidPrintBridge(private val context: Context) {

    companion object {
        private const val TAG = "AndroidPrintBridge"
        private const val ACTION_USB_PERMISSION = "com.tae.printbridge.USB_PERMISSION"
        private const val PREFS_NAME = "tae_print_config"
    }

    private val usbPrinter = UsbEscPosPrinter(
        context = context,
        permissionAction = ACTION_USB_PERMISSION
    )

    private val tcpPrinter = TcpEscPosPrinter()

    @JavascriptInterface
    fun print(payloadJson: String): String {
        return try {
            Log.i(TAG, "print() payload recibido")

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val savedTransport = prefs.getString("print_mode", "usb") ?: "usb"
            val savedIp = prefs.getString("printer_ip", "") ?: ""
            val savedPort = prefs.getInt("printer_port", 9100)
            val savedCut = prefs.getBoolean("cut", true)
            val savedOpenDrawer = prefs.getBoolean("open_drawer", false)
            val savedLogo = prefs.getString("logo_base64", "") ?: ""
            val savedLogoMaxWidth = prefs.getInt("logo_max_width", 160)

            val payload = JSONObject(payloadJson)

            val textBeforeQr = payload.optString("textBeforeQr", "")
            val textAfterQr = payload.optString("textAfterQr", "")

            val cut = if (payload.has("cut")) payload.optBoolean("cut", savedCut) else savedCut
            val openDrawer = if (payload.has("openDrawer")) payload.optBoolean("openDrawer", savedOpenDrawer) else savedOpenDrawer
            val drawerPin = payload.optInt("drawerPin", 0)

            val imageBase64 = when {
                payload.has("logo") && payload.optString("logo").isNotBlank() ->
                    payload.optString("logo")

                payload.has("imageBase64") && payload.optString("imageBase64").isNotBlank() ->
                    payload.optString("imageBase64")

                savedLogo.isNotBlank() ->
                    savedLogo

                else -> null
            }

            val logoMaxWidth = payload.optInt("logoMaxWidth", savedLogoMaxWidth)

            val qrText = payload.optString("qrText", null)
                ?.takeIf { it.isNotBlank() }

            val qrSize = payload.optInt("qrSize", 8)
            val qrEcc = payload.optInt("qrEcc", 49)

            val transport = savedTransport
                .lowercase()
                .replace("tcp/ip", "tcp")
                .trim()

            Log.i(
                TAG,
                "print() transport=$transport ip=$savedIp port=$savedPort " +
                    "cut=$cut openDrawer=$openDrawer drawerPin=$drawerPin " +
                    "img=${imageBase64 != null} qr=${qrText != null} logoMaxWidth=$logoMaxWidth"
            )

            val data = EscPosBuilder.build(
                textBeforeQr = textBeforeQr,
                textAfterQr = textAfterQr,
                cut = cut,
                shouldOpenDrawer = openDrawer,
                drawerPin = drawerPin,
                imageBase64 = imageBase64,
                qrText = qrText,
                qrSize = qrSize,
                qrEcc = qrEcc,
                logoMaxWidth = logoMaxWidth
            )

            val ok = when (transport) {
                "tcp" -> {
                    if (savedIp.isBlank()) {
                        Log.e(TAG, "Falta IP para imprimir por TCP/IP")
                        false
                    } else {
                        tcpPrinter.print(savedIp, savedPort, data)
                    }
                }

                else -> {
                    usbPrinter.printRaw(data)
                }
            }

            Log.i(TAG, "print ok=$ok")

            if (ok) {
                "true"
            } else {
                "No se pudo imprimir. Revisa modo, IP, puerto, red WiFi o conexión USB."
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error printing", e)
            e.message ?: "Error desconocido al imprimir"
        }
    }
}
