package com.tae.printbridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

class AndroidPrintBridge(private val context: Context) {

    companion object {
        private const val TAG = "AndroidPrintBridge"
        private const val ACTION_USB_PERMISSION = "com.tae.printbridge.USB_PERMISSION"
    }

    private val usbPrinter = UsbEscPosPrinter(
        context = context,
        permissionAction = ACTION_USB_PERMISSION
    )

    /**
     * Lo llama el WebView desde JS:
     * window.AndroidPrintBridge.print(JSON.stringify(payload))
     */
    @JavascriptInterface
    fun print(payloadJson: String) {
        try {
            Log.i(TAG, "print() payload recibido")

            val payload = JSONObject(payloadJson)

            val textBeforeQr = payload.optString("textBeforeQr", "")
            val textAfterQr = payload.optString("textAfterQr", "")
            val cut = payload.optBoolean("cut", true)
            val openDrawer = payload.optBoolean("openDrawer", false)
            val drawerPin = payload.optInt("drawerPin", 0)

            val imageBase64 = when {
                payload.has("logo") && payload.optString("logo").isNotBlank() ->
                    payload.optString("logo")
                payload.has("imageBase64") && payload.optString("imageBase64").isNotBlank() ->
                    payload.optString("imageBase64")
                else -> null
            }

            val logoMaxWidth = payload.optInt("logoMaxWidth", 160)

            val qrText = payload.optString("qrText", null)
                ?.takeIf { it.isNotBlank() }

            val qrSize = payload.optInt("qrSize", 8)
            val qrEcc = payload.optInt("qrEcc", 49)

            Log.i(
                TAG,
                "print() beforeLen=${textBeforeQr.length} afterLen=${textAfterQr.length} " +
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

            val ok = usbPrinter.printRaw(data)
            Log.i(TAG, "USB RAW print ok=$ok")

        } catch (e: Exception) {
            Log.e(TAG, "Error printing", e)
        }
    }
}