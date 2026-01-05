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

        // Receiver para permisos USB (compatible API 24+ y target 33+)
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
                val text = payload.optString("text", "")
                val cut = payload.optBoolean("cut", true)

                Log.i(TAG, "PRINT request: cut=$cut textLen=${text.length}")
                usbPrinter.printText(text, cut)
            }
        )

        server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Servicio listo en http://127.0.0.1:9100/print")
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
            .setContentText("Escuchando en 127.0.0.1:9100")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
    }
}
