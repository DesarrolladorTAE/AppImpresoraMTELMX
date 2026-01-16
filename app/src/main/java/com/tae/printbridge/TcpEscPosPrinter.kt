package com.tae.printbridge

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

class TcpEscPosPrinter {
    companion object { private const val TAG = "TcpEscPosPrinter" }

    fun print(ip: String, port: Int, data: ByteArray, timeoutMs: Int = 3000): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                s.getOutputStream().use { out ->
                    out.write(data)
                    out.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "TCP print failed: ${e.message}", e)
            false
        }
    }
}
