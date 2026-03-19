package com.tae.printbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class PrintBridgeService : Service() {

    companion object {
        private const val TAG = "TaePrintBridge"
        private const val ACTION_USB_PERMISSION = "com.tae.printbridge.USB_PERMISSION"
        private const val CHANNEL_ID = "tae_print_bridge"
        private const val NOTIF_ID = 1001
    }

    private var server: PrintServer? = null
    private lateinit var usbPrinter: UsbEscPosPrinter
    private val tcpPrinter = TcpEscPosPrinter()

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return

            val device: UsbDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.i(TAG, "USB permission result: device=${device?.deviceName} granted=$granted")
        }
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(NOTIF_ID, buildNotification())

        usbPrinter = UsbEscPosPrinter(
            context = this,
            permissionAction = ACTION_USB_PERMISSION
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        server = PrintServer(
            port = 9100,
            onPrint = { payload: JSONObject ->

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

                val transport = payload.optString("transport", "usb")

                Log.i(
                    TAG,
                    "PRINT request: transport=$transport cut=$cut openDrawer=$openDrawer drawerPin=$drawerPin " +
                            "img=${imageBase64 != null} qr=${qrText != null} " +
                            "beforeLen=${textBeforeQr.length} afterLen=${textAfterQr.length} logoMaxWidth=$logoMaxWidth"
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

                when (transport) {
                    "tcp" -> {
                        val ip = payload.optString("ip", "")
                        val port = payload.optInt("port", 9100)

                        if (ip.isBlank()) {
                            Log.e(TAG, "Falta ip para imprimir por tcp")
                            false
                        } else {
                            tcpPrinter.print(ip, port, data)
                        }
                    }

                    else -> {
                        usbPrinter.printRaw(data)
                    }
                }
            }
        )

        server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Servicio listo en http://0.0.0.0:9100/print (usa la IP de la tablet)")
    }

    override fun onDestroy() {
        try { unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TaePrintBridge",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TaePrintBridge activo")
            .setContentText("Escuchando en 0.0.0.0:9100")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
    }
}