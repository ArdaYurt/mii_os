package com.miios.launcher.tv

import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * A one-page web form on the home network, so the long playlist address can be pasted on a phone
 * instead of typed with the remote. It only runs while the TV setup screen is open.
 * [onReceived] is called on a background thread.
 */
class PhoneLink(private val onReceived: (playlist: String, guide: String) -> Unit) {
    private var server: ServerSocket? = null

    /** Starts the page and returns its address, or null if the box isn't on a local network. */
    fun start(): String? {
        server?.let { return address(it) }
        val ip = lanAddress() ?: return null
        val socket = try {
            ServerSocket(PORT, 4, ip)
        } catch (e: IOException) {
            try { ServerSocket(0, 4, ip) } catch (e: IOException) { return null }
        }
        server = socket
        thread(isDaemon = true, name = "phone-link") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                // A bad request from the network must never take the page down.
                try { handle(client) } catch (e: Exception) { client.close() }
            }
        }
        return address(socket)
    }

    fun stop() {
        server?.close()
        server = null
    }

    private fun address(socket: ServerSocket) = "http://${socket.inetAddress.hostAddress}:${socket.localPort}"

    private fun handle(client: Socket) {
        client.use { s -> serve(s) }
    }

    private fun serve(s: Socket) {
        s.soTimeout = 10_000
        val input = s.getInputStream().buffered()
        val request = readLine(input) ?: return
        var length = 0
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        var saved = false
        if (request.startsWith("POST")) {
            val body = ByteArray(length.coerceIn(0, 64 * 1024))
            var read = 0
            while (read < body.size) {
                val n = input.read(body, read, body.size - read)
                if (n < 0) break
                read += n
            }
            val form = String(body, 0, read, Charsets.UTF_8).split('&').associate {
                val (k, v) = (it.split('=', limit = 2) + "").take(2)
                k to URLDecoder.decode(v, "UTF-8")
            }
            val playlist = form["playlist"].orEmpty().trim()
            if (playlist.isNotEmpty()) {
                onReceived(playlist, form["guide"].orEmpty().trim())
                saved = true
            }
        }
        val html = page(saved).toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(html)
            flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (line.isEmpty()) null else line.toString()
            if (b == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length < 8192) line.append(b.toChar())
        }
    }

    private fun lanAddress(): InetAddress? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    } catch (e: IOException) {
        null
    }

    private fun page(saved: Boolean) = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Mii_OS TV</title><style>
        body{font-family:system-ui,sans-serif;background:linear-gradient(#fff,#edeff1);color:#55595e;margin:0;padding:24px}
        h1{font-size:28px;margin:0 0 4px}p{color:#9a9ea3;margin:0 0 20px}
        label{display:block;font-weight:600;margin:16px 0 6px}
        input{width:100%;box-sizing:border-box;font-size:16px;padding:12px;border:2px solid #cdd1d5;border-radius:14px}
        input:focus{outline:none;border-color:#34beed}
        button{margin-top:24px;width:100%;font-size:18px;font-weight:700;padding:14px;border:0;border-radius:28px;background:#34beed;color:#fff}
        .ok{background:#fff;border:2px solid #34beed;border-radius:14px;padding:12px;color:#55595e}
        </style></head><body>
        <h1>Mii_OS TV</h1><p>Paste your IPTV playlist address. It goes straight to your TV.</p>
        ${if (saved) "<div class=\"ok\">Sent. Your TV is loading the channels now.</div>" else ""}
        <form method="post">
        <label for="p">Playlist address (M3U)</label><input id="p" name="playlist" type="url" placeholder="http://…" required>
        <label for="g">Guide address (XMLTV, optional)</label><input id="g" name="guide" type="url" placeholder="http://…">
        <button type="submit">Send to TV</button></form></body></html>
    """.trimIndent()

    companion object {
        private const val PORT = 8080
    }
}
