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
     * window.AndroidPrintBridge.print('{"text":"hola","cut":true}')
     */
    @JavascriptInterface
    fun print(payloadJson: String) {
        try {
            Log.i(TAG, "print() payload=$payloadJson")
            val payload = JSONObject(payloadJson)

            val text = payload.optString("text", "")
            val cut = payload.optBoolean("cut", true)

            val ok = usbPrinter.printText(text, cut)
            Log.i(TAG, "USB print ok=$ok")

        } catch (e: Exception) {
            Log.e(TAG, "Error printing", e)
        }
    }
}
