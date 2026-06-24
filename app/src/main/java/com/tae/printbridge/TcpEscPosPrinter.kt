package com.tae.printbridge

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

class TcpEscPosPrinter {

    companion object {
        private const val TAG = "TcpEscPosPrinter"
        private const val CHUNK_SIZE = 4096
    }

    fun print(ip: String, port: Int, data: ByteArray, timeoutMs: Int = 8000): Boolean {
        val cleanIp = ip.trim()

        if (cleanIp.isBlank()) {
            Log.e(TAG, "IP vacía")
            return false
        }

        if (port <= 0) {
            Log.e(TAG, "Puerto inválido: $port")
            return false
        }

        return try {
            Log.i(TAG, "Conectando TCP a $cleanIp:$port bytes=${data.size}")

            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(cleanIp, port), timeoutMs)

                Log.i(TAG, "Conectado TCP a $cleanIp:$port")

                val output = socket.getOutputStream()

                var offset = 0
                while (offset < data.size) {
                    val len = minOf(CHUNK_SIZE, data.size - offset)
                    output.write(data, offset, len)
                    output.flush()

                    Log.d(TAG, "Chunk TCP enviado offset=$offset len=$len")

                    offset += len
                }

                try {
                    socket.shutdownOutput()
                } catch (_: Exception) {
                }

                Thread.sleep(250)
            }

            Log.i(TAG, "Impresión TCP completada")
            true

        } catch (e: Exception) {
            Log.e(TAG, "TCP print failed a $cleanIp:$port -> ${e.message}", e)
            false
        }
    }
}
