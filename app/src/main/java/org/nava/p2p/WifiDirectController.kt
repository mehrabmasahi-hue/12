package org.nava.p2p

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Wi-Fi Direct discovery/connection adapter. Payload transport uses the app TCP listener. */
class WifiDirectController(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onDevices(devices: List<WifiP2pDevice>)
        fun onConnected(isGroupOwner: Boolean, host: String)
        fun onState(message: String)
    }

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, context.mainLooper, null)
    private var registered = false
    private var receiver: BroadcastReceiver? = null
    private var devices: List<WifiP2pDevice> = emptyList()

    @Suppress("DEPRECATION")
    fun start() {
        if (!hasPermission()) {
            listener.onState("برای Wi-Fi Direct مجوز دستگاه‌های نزدیک لازم است")
            return
        }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                            listener.onState(if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "Wi-Fi Direct روشن است" else "Wi-Fi Direct خاموش است")
                        }
                        WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                        WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
        }
        discover()
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun discover() {
        if (!hasPermission()) return
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { listener.onState("جست‌وجوی Wi-Fi Direct آغاز شد") }
            override fun onFailure(reason: Int) { listener.onState("شروع جست‌وجو ناموفق بود: ${reasonText(reason)}") }
        })
    }

    @Suppress("MissingPermission")
    private fun requestPeers() {
        if (!hasPermission()) return
        manager.requestPeers(channel) { list: WifiP2pDeviceList ->
            devices = list.deviceList.toList()
            listener.onDevices(devices)
        }
    }

    @Suppress("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        if (!hasPermission()) return
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { listener.onState("درخواست اتصال به ${device.deviceName} ارسال شد") }
            override fun onFailure(reason: Int) { listener.onState("اتصال ناموفق بود: ${reasonText(reason)}") }
        })
    }

    @Suppress("MissingPermission")
    private fun requestConnectionInfo() {
        if (!hasPermission()) return
        manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
            if (info.groupFormed && info.groupOwnerAddress != null) {
                listener.onConnected(info.isGroupOwner, info.groupOwnerAddress.hostAddress ?: "")
            } else listener.onState("اتصال Wi-Fi Direct برقرار نیست")
        }
    }

    fun stop() {
        if (registered) {
            try { receiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
            registered = false
        }
        receiver = null
    }

    private fun reasonText(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "این گوشی Wi-Fi Direct را پشتیبانی نمی‌کند"
        WifiP2pManager.BUSY -> "سرویس Wi-Fi Direct مشغول است"
        WifiP2pManager.ERROR -> "خطای داخلی Wi-Fi Direct"
        else -> "کد $reason"
    }
}
