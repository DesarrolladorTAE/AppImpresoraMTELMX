package com.tae.printbridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val i = Intent(this, PrintBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)

        // Opcional: cierra la UI para que parezca "servicio"
        finish()
    }
}
