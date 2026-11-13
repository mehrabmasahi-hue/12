package org.nava.p2p

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.text.InputType
import android.app.AlertDialog
import android.widget.EditText
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity(), PeerDiscovery.Listener, WifiDirectController.Listener {
    private lateinit var status: TextView
    private lateinit var peersView: LinearLayout
    private lateinit var logView: TextView
    private lateinit var discovery: PeerDiscovery
    private lateinit var wifiDirect: WifiDirectController
    private lateinit var wifiDirectView: LinearLayout
    private var directHost: String? = null
    private val peers = linkedMapOf<String, PeerDiscovery.Peer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xff10251e.toInt()
        window.navigationBarColor = 0xff10251e.toInt()
        buildUi()
        discovery = PeerDiscovery(this, this)
        wifiDirect = WifiDirectController(this, this)
        requestNeededPermissions()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 20)
            setBackgroundColor(0xfff4f7f5.toInt())
        }
        val heading = TextView(this).apply {
            text = "نوا | پیام‌رسان محلی"
            textSize = 25f
            setTextColor(0xff153b2d.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "در انتظار مجوزها…"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 18)
        }
        val scan = Button(this).apply {
            text = "جست‌وجوی دستگاه‌های اطراف"
            setOnClickListener { discovery.start(); status.text = "در حال جست‌وجوی شبکه محلی…" }
        }
        val directScan = Button(this).apply {
            text = "پیدا کردن گوشی‌ها با Wi-Fi Direct"
            setOnClickListener { wifiDirect.start() }
        }
        val directTitle = TextView(this).apply {
            text = "دستگاه‌های Wi-Fi Direct"
            textSize = 18f
            setTextColor(0xff153b2d.toInt())
            setPadding(0, 18, 0, 8)
        }
        wifiDirectView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val peersTitle = TextView(this).apply {
            text = "دستگاه‌های پیدا شده"
            textSize = 18f
            setTextColor(0xff153b2d.toInt())
            setPadding(0, 20, 0, 8)
        }
        peersView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logView = TextView(this).apply {
            text = "برای شروع، هر دو گوشی را به یک Wi-Fi وصل کنید یا یک شبکه محلی ایجاد کنید."
            textSize = 13f
            setTextColor(0xff52645b.toInt())
            setPadding(0, 20, 0, 0)
        }
        val adminButton = Button(this).apply {
            text = "بخش مدیریت"
            setOnClickListener { showAdminLogin() }
        }
        page.addView(heading)
        page.addView(adminButton)
        page.addView(status)
        page.addView(scan)
        page.addView(directScan)
        page.addView(directTitle)
        page.addView(wifiDirectView)
        page.addView(peersTitle)
        page.addView(peersView)
        page.addView(logView)
        setContentView(ScrollView(this).apply { addView(page) })
    }


    private fun showAdminLogin() {
        val passwordInput = EditText(this).apply {
            hint = "رمز مدیریت"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textDirection = android.view.View.TEXT_DIRECTION_LTR
        }
        AlertDialog.Builder(this)
            .setTitle("ورود به مدیریت")
            .setMessage("برای ورود، رمز مدیریت را وارد کنید.")
            .setView(passwordInput)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ورود", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val attempt = passwordInput.text.toString().toCharArray()
                        passwordInput.text?.clear()
                        status.text = "در حال بررسی رمز مدیریت…"
                        Thread {
                            val allowed = AdminAccess.verify(attempt)
                            runOnUiThread {
                                if (allowed) {
                                    dialog.dismiss()
                                    AlertDialog.Builder(this)
                                        .setTitle("بخش مدیریت")
                                        .setMessage("دسترسی تأیید شد. رمز مدیریت ثابت است و از داخل برنامه قابل تغییر نیست.\n\nتنظیمات مدیریتی در نسخه‌های بعدی اضافه می‌شوند.")
                                        .setPositiveButton("متوجه شدم", null)
                                        .show()
                                    status.text = "ورود مدیر تأیید شد"
                                } else {
                                    passwordInput.error = "رمز نادرست است"
                                    status.text = "رمز مدیریت صحیح نیست"
                                }
                            }
                        }.start()
                    }
                }
                dialog.show()
            }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.POST_NOTIFICATIONS).forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) needed.add(it)
            }
        } else if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION).forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) needed.add(it)
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 40)
        else {
            discovery.start()
            wifiDirect.start()
            status.text = "آماده؛ در حال جست‌وجوی همتاها…"
        }
    }

    override fun onPeerFound(peer: PeerDiscovery.Peer) {
        runOnUiThread {
            peers[peer.id] = peer
            peersView.removeAllViews()
            peers.values.forEach { p ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 8, 8, 8)
                }
                val name = TextView(this).apply {
                    text = "${p.name}\n${p.host}:${p.port}"
                    textSize = 15f
                    setTextColor(0xff173c2e.toInt())
                }
                val send = Button(this).apply {
                    text = "پیام آزمایشی"
                    setOnClickListener {
                        Thread {
                            val result = discovery.send(p, "سلام! پیام آزمایشی از نوا")
                            runOnUiThread { logView.text = result }
                        }.start()
                    }
                }
                row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(send)
                peersView.addView(row)
            }
            status.text = "همتاهای پیدا شده: ${peers.size}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            discovery.start()
            wifiDirect.start()
            status.text = "مجوزها تأیید شدند؛ جست‌وجوی دستگاه‌ها آغاز شد"
        } else if (requestCode == 40) {
            status.text = "برای کشف دستگاه‌های نزدیک باید مجوزهای لازم را بدهی"
        }
    }

    override fun onStatus(message: String) { runOnUiThread { status.text = message } }

    override fun onDevices(devices: List<WifiP2pDevice>) {
        runOnUiThread {
            wifiDirectView.removeAllViews()
            if (devices.isEmpty()) {
                wifiDirectView.addView(TextView(this).apply { text = "هنوز دستگاهی پیدا نشده است"; setTextColor(0xff52645b.toInt()) })
            }
            devices.forEach { device ->
                val button = Button(this).apply {
                    text = "اتصال به ${device.deviceName.ifBlank { "دستگاه ناشناس" }}"
                    isEnabled = device.status != WifiP2pDevice.CONNECTED
                    setOnClickListener { wifiDirect.connect(device) }
                }
                wifiDirectView.addView(button)
            }
        }
    }

    override fun onConnected(isGroupOwner: Boolean, host: String) {
        directHost = host
        runOnUiThread {
            status.text = if (isGroupOwner) "گروه Wi-Fi Direct ایجاد شد؛ آدرس: $host" else "به گروه Wi-Fi Direct متصل شدی؛ میزبان: $host"
            val peer = PeerDiscovery.Peer("wifi-direct", "Wi-Fi Direct", host, 45873, System.currentTimeMillis())
            val send = Button(this).apply {
                text = "ارسال پیام آزمایشی از Wi-Fi Direct"
                setOnClickListener {
                    Thread {
                        val result = discovery.send(peer, "سلام از ارتباط مستقیم Wi-Fi Direct")
                        runOnUiThread { logView.text = result }
                    }.start()
                }
            }
            wifiDirectView.addView(send)
        }
    }

    override fun onState(message: String) { runOnUiThread { status.text = message } }

    override fun onDestroy() {
        if (::discovery.isInitialized) discovery.stop()
        if (::wifiDirect.isInitialized) wifiDirect.stop()
        super.onDestroy()
    }
}
