package com.tae.printbridge

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class PrintServer(
    port: Int,
    private val onPrint: (JSONObject) -> Boolean
) : NanoHTTPD("0.0.0.0", port) {

    override fun serve(session: IHTTPSession): Response {
        // CORS
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse("").applyCors()
        }

        if (session.uri != "/print" || session.method != Method.POST) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"ok":false,"error":"not_found"}"""
            ).applyCors()
        }

        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: "{}"
            val json = JSONObject(body)

            val ok = onPrint(json)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                if (ok) """{"ok":true}""" else """{"ok":false,"error":"print_failed"}"""
            ).applyCors()
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"ok":false,"error":"bad_request","detail":"${e.message}"}"""
            ).applyCors()
        }
    }

    private fun Response.applyCors(): Response {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Content-Type")
        return this
    }
}
