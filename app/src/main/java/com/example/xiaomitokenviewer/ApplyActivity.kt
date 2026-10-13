package com.example.xiaomitokenviewer

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApplyActivity : AppCompatActivity() {

    private lateinit var etToken1: EditText
    private lateinit var etToken2: EditText
    private lateinit var btnPaste1: Button
    private lateinit var btnPaste2: Button
    private lateinit var btnRun: Button
    private lateinit var tvSlot1: TextView
    private lateinit var tvSlot2: TextView
    private lateinit var tvSlot3: TextView
    private lateinit var tvSlot4: TextView

    private val isRunning = AtomicBoolean(false)
    private val jobs = mutableListOf<Job>()

    private val SKIP_TIMING = false
    private val MANUAL_FIRE_HOUR = 19
    private val MANUAL_FIRE_MIN = 37
    private val MANUAL_FIRE_SEC = 0
    private val OFFSET_MS = 120L
    private val BURST_INTERVAL_MS = 50L

    private val URL_STATUS = "https://sgp-api.buy.mi.com/bbs/api/global/user/bl-switch/state"
    private val URL_APPLY = "https://sgp-api.buy.mi.com/bbs/api/global/apply/bl-auth"
    private val UA = "okhttp/4.12.0"
    private val NTP_SERVERS = listOf("ntp0.ntp-servers.net", "ntp1.ntp-servers.net", "ntp2.ntp-servers.net")

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val beijingTz = TimeZone.getTimeZone("Asia/Shanghai")
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply {
        timeZone = beijingTz
    }

    companion object {
        const val EXTRA_TOKEN1 = "token1"
        const val EXTRA_TOKEN2 = "token2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply)

        etToken1 = findViewById(R.id.etToken1)
        etToken2 = findViewById(R.id.etToken2)
        btnPaste1 = findViewById(R.id.btnPaste1)
        btnPaste2 = findViewById(R.id.btnPaste2)
        btnRun = findViewById(R.id.btnRun)
        tvSlot1 = findViewById(R.id.tvSlot1)
        tvSlot2 = findViewById(R.id.tvSlot2)
        tvSlot3 = findViewById(R.id.tvSlot3)
        tvSlot4 = findViewById(R.id.tvSlot4)

        // Auto-fill tokens from main screen
        val t1 = intent.getStringExtra(EXTRA_TOKEN1).orEmpty()
        val t2 = intent.getStringExtra(EXTRA_TOKEN2).orEmpty()
        if (t1.isNotBlank()) etToken1.setText(t1)
        if (t2.isNotBlank()) etToken2.setText(t2)

        btnPaste1.setOnClickListener { pasteTo(etToken1) }
        btnPaste2.setOnClickListener { pasteTo(etToken2) }

        btnRun.setOnClickListener {
            if (isRunning.get()) stopAll() else startAll()
        }

        val btnTelegram = findViewById<Button>(R.id.btnTelegram)
        btnTelegram.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/error444_fahim"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Telegram not found", Toast.LENGTH_SHORT).show()
            }
        }

        val tvMadeBy = findViewById<TextView>(R.id.tvMadeBy)
        val pulse = AlphaAnimation(0.55f, 1.0f).apply {
            duration = 1200
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        val scale = ScaleAnimation(
            0.97f, 1.03f, 0.97f, 1.03f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1200
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        val set = AnimationSet(true).apply {
            addAnimation(pulse)
            addAnimation(scale)
        }
        tvMadeBy.startAnimation(set)
    }

    private fun pasteTo(et: EditText) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            et.setText(clip.getItemAt(0).text)
            Toast.makeText(this, "Pasted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAll() {
        val token1 = etToken1.text.toString().trim()
        val token2 = etToken2.text.toString().trim()
        if (token1.isEmpty() || token2.isEmpty()) {
            Toast.makeText(this, "Both tokens required!", Toast.LENGTH_LONG).show()
            return
        }

        isRunning.set(true)
        btnRun.text = getString(R.string.stop)
        clearLogs()

        val tokens = listOf(token1, token2, token1, token2)
        val tvs = listOf(tvSlot1, tvSlot2, tvSlot3, tvSlot4)

        jobs.clear()
        for (i in 0..3) {
            val job = lifecycleScope.launch(Dispatchers.IO) {
                runSlot(i + 1, tokens[i], tvs[i])
            }
            jobs.add(job)
        }
    }

    private fun stopAll() {
        isRunning.set(false)
        jobs.forEach { it.cancel() }
        jobs.clear()
        runOnUiThread { btnRun.text = getString(R.string.run) }
    }

    private fun clearLogs() {
        listOf(tvSlot1, tvSlot2, tvSlot3, tvSlot4).forEach { it.text = "" }
    }

    private suspend fun log(tv: TextView, msg: String) {
        withContext(Dispatchers.Main) {
            tv.append("$msg\n")
            (tv.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private suspend fun runSlot(slot: Int, token: String, tv: TextView) {
        try {
            log(tv, "[Slot $slot] Starting...")
            log(tv, "[BEIJING] ${timeFormat.format(Date())}")

            val deviceId = genDeviceId()
            log(tv, "DeviceId: ${deviceId.take(12)}...")

            log(tv, "🔍 Checking status...")
            if (!checkStatus(token, deviceId, tv)) {
                log(tv, "❌ Status check failed / not ready")
                return
            }

            val startBt = getNtpBeijing(tv)
            val startTs = System.currentTimeMillis()
            val target = getTarget(startBt, tv)

            val fireTarget = Date(target.time - OFFSET_MS)
            waitTarget(startBt, startTs, fireTarget, tv)

            log(tv, "🚀 PRECISE 10x BURST STARTS NOW!")

            var success = false
            for (i in 0 until 10) {
                if (!isRunning.get()) {
                    log(tv, "[!] Interrupted")
                    break
                }

                val shotTarget = Date(target.time + i * BURST_INTERVAL_MS)
                while (true) {
                    val now = syncedTime(startBt, startTs)
                    val diff = shotTarget.time - now.time
                    if (diff <= 0) break
                    delay((diff * 0.8).toLong().coerceAtLeast(1))
                }

                val nowBeijing = timeFormat.format(Date())
                log(tv, "[${i + 1}/10] @$nowBeijing")

                val resp = fire(token, deviceId)
                if (resp != null) {
                    if (handleResp(resp, tv)) {
                        success = true
                        log(tv, "🎉 SUCCESS on shot ${i + 1}!")
                        break
                    }
                } else {
                    log(tv, "HTTP fail")
                }
            }

            if (!success) {
                log(tv, "❌ Burst complete - No approval")
            }
            log(tv, "[*] Next quota: 00:00 Beijing tomorrow")

        } catch (e: CancellationException) {
            log(tv, "[!] Cancelled")
        } catch (e: Exception) {
            log(tv, "Error: ${e.message}")
        }
    }

    private fun genDeviceId(): String {
        val raw = "${Math.random()}-${System.currentTimeMillis()}"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(raw.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    private suspend fun getNtpBeijing(tv: TextView): Date {
        for (server in NTP_SERVERS) {
            try {
                val time = withContext(Dispatchers.IO) { queryNtp(server) }
                if (time != null) {
                    log(tv, "[NTP] ${timeFormat.format(time)}")
                    return time
                }
            } catch (_: Exception) {}
        }
        val now = Date()
        log(tv, "[SYS] ${timeFormat.format(now)}")
        return now
    }

    private fun queryNtp(server: String): Date? {
        val socket = DatagramSocket()
        socket.soTimeout = 3000
        try {
            val address = InetAddress.getByName(server)
            val buf = ByteArray(48)
            buf[0] = 0x1B.toByte()
            val request = DatagramPacket(buf, buf.size, address, 123)
            socket.send(request)

            val response = DatagramPacket(ByteArray(48), 48)
            socket.receive(response)

            val data = response.data
            val seconds = ByteBuffer.wrap(data, 40, 4).int.toLong() and 0xFFFFFFFFL
            val fraction = ByteBuffer.wrap(data, 44, 4).int.toLong() and 0xFFFFFFFFL
            val unixMs = ((seconds - 2208988800L) * 1000) + (fraction * 1000 / 0x100000000L)
            return Date(unixMs)
        } finally {
            socket.close()
        }
    }

    private fun syncedTime(startBt: Date, startTs: Long): Date {
        val elapsed = System.currentTimeMillis() - startTs
        return Date(startBt.time + elapsed)
    }

    private suspend fun getTarget(startBt: Date, tv: TextView): Date {
        val cal = Calendar.getInstance(beijingTz)
        cal.time = startBt
        if (SKIP_TIMING) {
            cal.set(Calendar.HOUR_OF_DAY, MANUAL_FIRE_HOUR)
            cal.set(Calendar.MINUTE, MANUAL_FIRE_MIN)
            cal.set(Calendar.SECOND, MANUAL_FIRE_SEC)
            cal.set(Calendar.MILLISECOND, 0)
            log(tv, "[MANUAL] ${timeFormat.format(cal.time)}")
        } else {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            log(tv, "[AUTO] Midnight target: ${timeFormat.format(cal.time)}")
        }
        return cal.time
    }

    private suspend fun waitTarget(startBt: Date, startTs: Long, target: Date, tv: TextView) {
        log(tv, "Waiting for exact target...")
        var lastShow = 0L
        while (isRunning.get()) {
            val now = syncedTime(startBt, startTs)
            val diff = target.time - now.time
            if (System.currentTimeMillis() - lastShow > 1000) {
                log(tv, "[BEIJING] ${timeFormat.format(now)}")
                lastShow = System.currentTimeMillis()
            }
            if (diff > 10) {
                delay(minOf(diff - 5, 100))
            } else if (diff > 0) {
                delay(1)
            } else {
                break
            }
        }
    }

    private suspend fun checkStatus(token: String, deviceId: String, tv: TextView): Boolean {
        val cookie = "new_bbs_serviceToken=$token;versionCode=500411;versionName=5.4.11;deviceId=$deviceId;"
        val request = Request.Builder()
            .url(URL_STATUS)
            .header("Cookie", cookie)
            .header("User-Agent", UA)
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use false
                    val data = JSONObject(body)
                    if (data.optInt("code") == 100004) {
                        log(tv, "Token expired!")
                        return@use false
                    }
                    val info = data.optJSONObject("data") ?: return@use false
                    val isPass = info.optInt("is_pass")
                    val button = info.optInt("button_state")
                    when {
                        isPass == 4 && button == 1 -> {
                            log(tv, "[Status]: READY!")
                            true
                        }
                        isPass == 1 -> {
                            log(tv, "[Status]: Already approved!")
                            false
                        }
                        else -> {
                            log(tv, "[Status]: Not ready (is_pass=$isPass)")
                            false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log(tv, "Status err: ${e.message}")
            false
        }
    }

    private fun fire(token: String, deviceId: String): JSONObject? {
        val cookie = "new_bbs_serviceToken=$token;versionCode=500411;versionName=5.4.11;deviceId=$deviceId;"
        val body = """{"is_retry":true}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(URL_APPLY)
            .header("Cookie", cookie)
            .header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: return null
                JSONObject(str)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun handleResp(resp: JSONObject, tv: TextView): Boolean {
        if (resp.optInt("code") != 0) {
            log(tv, "❌ API ERR")
            return false
        }
        val data = resp.optJSONObject("data") ?: return false
        val result = data.optInt("apply_result")
        val deadline = data.optString("deadline_format", "")
        return when (result) {
            1 -> {
                log(tv, "✅ APPROVED! 🎉")
                true
            }
            3 -> {
                log(tv, "⏳ QUOTA: $deadline")
                false
            }
            4 -> {
                log(tv, "⛔ BLOCKED: $deadline")
                false
            }
            else -> {
                log(tv, "❓ $resp")
                false
            }
        }
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}
