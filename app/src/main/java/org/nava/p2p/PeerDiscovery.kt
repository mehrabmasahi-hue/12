package org.nava.p2p

import android.content.Context
import android.net.wifi.WifiManager
import java.net.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * MVP transport: UDP broadcast discovery + TCP message exchange on the same IP network.
 * Wi-Fi Direct and Bluetooth transports are separate adapters in the planned architecture.
 */
class PeerDiscovery(private val context: Context, private val listener: Listener) {
    data class Peer(val id: String, val name: String, val host: String, val port: Int, val lastSeen: Long)
    interface Listener {
        fun onPeerFound(peer: Peer)
        fun onStatus(message: String)
    }

    companion object {
        private const val DISCOVERY_PORT = 45872
        private const val TCP_PORT = 45873
        private const val MAGIC = "NAVA_P2P_V1"
    }

    private val running = AtomicBoolean(false)
    private val nodeId = UUID.randomUUID().toString()
    private val nodeName = android.os.Build.MODEL ?: "Android"
    private var udp: DatagramSocket? = null
    private var tcp: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acquireMulticastLock()
        thread(name = "nava-tcp") { tcpLoop() }
        thread(name = "nava-announce") { announceLoop() }
        thread(name = "nava-listen") { listenLoop() }
        listener.onStatus("سرور محلی فعال است؛ در حال کشف همتاها…")
    }

    private fun acquireMulticastLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wm.createMulticastLock("nava-discovery").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
    }

    private fun tcpLoop() {
        try {
            tcp = ServerSocket(TCP_PORT)
            while (running.get()) {
                val socket = tcp!!.accept()
                thread {
                    socket.use { s ->
                        s.soTimeout = 10000
                        val input = s.getInputStream().bufferedReader()
                        val line = input.readLine() ?: return@thread
                        listener.onStatus("پیام دریافت شد: $line")
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) listener.onStatus("خطای TCP: ${e.message}")
        }
    }

    private fun announceLoop() {
        while (running.get()) {
            try {
                val payload = "$MAGIC|$nodeId|$nodeName|$TCP_PORT".toByteArray()
                DatagramSocket().use { s ->
                    s.broadcast = true
                    s.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
                }
            } catch (_: Exception) {}
            try { Thread.sleep(2500) } catch (_: InterruptedException) { return }
        }
    }

    private fun listenLoop() {
        try {
            udp = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
                soTimeout = 2000
            }
            val buf = ByteArray(2048)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    udp?.receive(packet)
                    val parts = String(packet.data, 0, packet.length).split("|")
                    if (parts.size >= 4 && parts[0] == MAGIC && parts[1] != nodeId) {
                        val peer = Peer(parts[1], parts[2], packet.address.hostAddress ?: continue,
                            parts[3].toIntOrNull() ?: TCP_PORT, System.currentTimeMillis())
                        listener.onPeerFound(peer)
                    }
                } catch (_: SocketTimeoutException) {}
            }
        } catch (e: Exception) {
            if (running.get()) listener.onStatus("کشف شبکه ناموفق بود: ${e.message}")
        }
    }

    fun send(peer: Peer, message: String): String = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(peer.host, peer.port), 4000)
            s.getOutputStream().bufferedWriter().apply { write(message.replace("\n", " ")); newLine(); flush() }
        }
        "ارسال به ${peer.name} انجام شد (آزمایشی؛ رمزنگاری هنوز اضافه نشده است)."
    } catch (e: Exception) { "ارسال ناموفق: ${e.message}" }

    fun stop() {
        running.set(false)
        try { udp?.close() } catch (_: Exception) {}
        try { tcp?.close() } catch (_: Exception) {}
        try { multicastLock?.release() } catch (_: Exception) {}
    }
}
