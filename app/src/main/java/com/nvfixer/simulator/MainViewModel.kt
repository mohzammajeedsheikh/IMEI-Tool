package com.nvfixer.simulator

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private data class Step(val pct: Int, val text: String, val kind: LogKind)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("nv", 0)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var job: Job? = null
    private var connectJob: Job? = null

    // inputs
    var profileIdx by mutableIntStateOf(0)
    var chip by mutableStateOf("MTK")
    var imei1 by mutableStateOf("")
    var imei2 by mutableStateOf("")
    var optBackup by mutableStateOf(true)
    var optSound by mutableStateOf(false)
    var optChaos by mutableStateOf(false)
    var darkOverride by mutableStateOf<Boolean?>(null)

    // status panel
    var usb by mutableStateOf("DISCONNECTED"); private set
    var usbTone by mutableStateOf(Tone.BAD); private set
    var baseband by mutableStateOf("UNKNOWN"); private set
    var bbTone by mutableStateOf(Tone.BAD); private set
    var shown1 by mutableStateOf(NULL_IMEI); private set
    var shown2 by mutableStateOf(NULL_IMEI); private set
    var imeiTone by mutableStateOf(Tone.BAD); private set
    var network by mutableStateOf("No Service"); private set
    var netTone by mutableStateOf(Tone.BAD); private set
    var signal by mutableIntStateOf(0); private set

    var progress by mutableFloatStateOf(0f); private set
    var running by mutableStateOf(false); private set
    private var connected = false

    val log = mutableStateListOf(
        LogLine("[SYS] Ready. Click Connect or enter parameters...", LogKind.N)
    )
    val history = mutableStateListOf<String>()
    val info = mutableStateListOf<Pair<String, String>>()

    init {
        history.addAll(loadHist())
        refreshInfo()
    }

    private fun now() = timeFmt.format(Date())

    private fun beep(f: Int = 600, ms: Int = 80) {
        if (optSound) Sound.beep(f, ms)
    }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        try {
            val v = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(ms)
        } catch (e: Exception) {
        }
    }

    fun addLog(t: String, k: LogKind = LogKind.N) {
        log.add(LogLine("[${now()}] $t", k))
    }

    fun logText(): String = log.joinToString("\n") { it.text }

    private fun loadHist(): List<String> =
        prefs.getString("hist", "")!!.split("\n").filter { it.isNotBlank() }

    private fun saveHist(x: String) {
        val list = (loadHist() + x).takeLast(20)
        prefs.edit().putString("hist", list.joinToString("\n")).apply()
        history.clear()
        history.addAll(list)
    }

    // ---- input handlers ----
    fun onProfile(i: Int) {
        profileIdx = i
        chip = PROFILES[i].chip
        addLog("[PROFILE] Loaded ${PROFILES[i].name}", LogKind.I)
    }

    fun onImei1(v: String) { imei1 = v.filter { it.isDigit() }.take(15) }
    fun onImei2(v: String) { imei2 = v.filter { it.isDigit() }.take(15) }

    private fun prefix() = PROFILES[profileIdx].prefix + "4000"

    fun randomImei1() { imei1 = makeImei(prefix()); beep(800) }
    fun randomImei2() { imei2 = makeImei(prefix()); beep(800) }

    fun toggleTheme(isDarkNow: Boolean) { darkOverride = !isDarkNow }

    // ---- actions ----
    /** Real, read-only scan of USB devices attached to the phone (OTG). */
    fun connect() {
        usb = "SCANNING..."; usbTone = Tone.WARN
        addLog("[USB] Scanning ports...", LogKind.I)
        beep(500)
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            delay(600)
            val devs = try {
                val um = getApplication<Application>().getSystemService(Context.USB_SERVICE) as UsbManager
                um.deviceList.values.toList()
            } catch (e: Exception) {
                emptyList()
            }
            if (devs.isEmpty()) {
                connected = false
                usb = "NO DEVICE"; usbTone = Tone.WARN
                addLog("[USB] No USB device found. Attach one with an OTG cable and try again.", LogKind.W)
                beep(300)
            } else {
                connected = true
                usb = "${devs.size} DEVICE(S) FOUND"; usbTone = Tone.OK
                devs.forEach {
                    addLog("[USB] " + String.format("%04X:%04X", it.vendorId, it.productId) + "  " + it.deviceName, LogKind.S)
                }
                beep(900)
            }
        }
    }

    /** Real, read-only device and network information. */
    fun refreshInfo() {
        val app = getApplication<Application>()
        val rows = mutableListOf<Pair<String, String>>()
        rows += "Device" to "${Build.MANUFACTURER} ${Build.MODEL}"
        rows += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        rows += "Chipset" to Build.HARDWARE
        rows += "CPU ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        rows += "Link" to networkLink(app)
        val tm = try {
            app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            null
        }
        val op = try { tm?.networkOperatorName } catch (e: Exception) { null }
        rows += "Operator" to (if (op.isNullOrBlank()) "none" else op)
        rows += "SIM" to simState(tm)
        info.clear()
        info.addAll(rows)
    }

    private fun networkLink(app: Application): String = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
    } catch (e: Exception) {
        "Unknown"
    }

    private fun simState(tm: TelephonyManager?): String = try {
        when (tm?.simState) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
            else -> "Unknown"
        }
    } catch (e: Exception) {
        "Unknown"
    }

    fun reset() {
        job?.cancel()
        connectJob?.cancel()
        progress = 0f
        imei1 = ""; imei2 = ""
        baseband = "UNKNOWN"; bbTone = Tone.BAD
        shown1 = NULL_IMEI; shown2 = NULL_IMEI; imeiTone = Tone.BAD
        network = "No Service"; netTone = Tone.BAD; signal = 0
        usb = "DISCONNECTED"; usbTone = Tone.BAD
        connected = false
        log.clear()
        log.add(LogLine("[SYS] Device state reset to Null IMEI.", LogKind.N))
        running = false
    }

    fun start() {
        var a = imei1.trim()
        var b = imei2.trim()
        if (a.isEmpty()) { a = makeImei(prefix()); imei1 = a }
        if (b.isEmpty()) { b = makeImei(prefix()); imei2 = b }

        if (a.length != 15 || b.length != 15 || !a.all { it.isDigit() } || !b.all { it.isDigit() }) {
            log.clear()
            addLog("[ERR] Both IMEI fields must be 15 digits.", LogKind.E)
            beep(200, 300)
            return
        }
        if (!luhnOk(a) || !luhnOk(b)) {
            log.clear()
            addLog("[ERR] Invalid checksum. Use a valid-format number.", LogKind.E)
            beep(200, 300)
            return
        }

        val chaosNow = optChaos && Random.nextDouble() < 0.35
        running = true
        log.clear()
        progress = 0f

        val steps = mutableListOf<Step>()
        steps += Step(8, "[INIT] Handshaking with $chip radio interface...", LogKind.I)
        if (optBackup) steps += Step(20, "[BACKUP] Saving NVRAM to nvram_backup.bin (2.1 MB)...", LogKind.N)
        steps += Step(35, "[FS] Mounting /nvdata and /efs (RW)...", LogKind.N)
        steps += Step(50, "[MODEM] Restoring corrupted baseband firmware...", LogKind.W)
        if (chaosNow) {
            steps += Step(-1, "[ERR] Timeout 0x8007: radio did not respond. (chaos mode)", LogKind.E)
        } else {
            steps += Step(70, "[AT+EGMR] Writing IMEI 1: $a", LogKind.I)
            steps += Step(85, "[AT+EGMR] Writing IMEI 2: $b", LogKind.I)
            steps += Step(95, "[VERIFY] Reading back values... match OK", LogKind.S)
            steps += Step(100, "[DONE] NVRAM synced! Rebooting radio service...", LogKind.S)
        }

        job?.cancel()
        job = viewModelScope.launch {
            for ((i, s) in steps.withIndex()) {
                delay(650)
                if (s.pct >= 0) progress = s.pct / 100f
                addLog(s.text, s.kind)
                beep(400 + i * 80)

                if (s.text.contains("Restoring")) {
                    baseband = "MOLY.LR13.R1.MP.V88"; bbTone = Tone.OK
                }
                if (s.pct == -1) {
                    beep(180, 400)
                    buzz(300)
                    running = false
                    saveHist("✗ FAILED  ${now()}")
                    addLog("[TIP] Turn off chaos mode and try again.", LogKind.W)
                }
                if (s.pct == 100) {
                    shown1 = a; shown2 = b; imeiTone = Tone.OK
                    beep(1000, 200)
                    buzz(120)
                    network = "Searching..."; netTone = Tone.WARN
                    saveHist("✓ $a  ${now()}")
                    running = false
                    launch {
                        delay(1200)
                        network = "Registered (LTE)"; netTone = Tone.OK
                        signal = 4
                        addLog("[NET] Registered on network. Signal 4/4.", LogKind.S)
                    }
                }
            }
        }
    }

    companion object {
        const val NULL_IMEI = "NULL / 000000000000000"
    }
}
