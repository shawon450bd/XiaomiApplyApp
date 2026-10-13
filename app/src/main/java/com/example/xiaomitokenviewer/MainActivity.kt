package com.example.xiaomitokenviewer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.xiaomitokenviewer.databinding.ActivityMainBinding
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TOKEN_NAME = "new_bbs_serviceToken"
        private const val TELEGRAM_URL = "https://t.me/sadi_977"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMadeByEffect()
        setupClickListeners()
        restoreTokenUi()
    }

    override fun onResume() {
        super.onResume()
        restoreTokenUi()
    }

    private fun restoreTokenUi() {
        val t1 = SessionStore.getToken(this, 1)
        val t2 = SessionStore.getToken(this, 2)
        binding.etToken1.setText(t1)
        binding.etToken2.setText(t2)
        binding.btnCopy1.isEnabled = t1.isNotBlank()
        binding.btnCopy2.isEnabled = t2.isNotBlank()
    }

    private fun setupMadeByEffect() {
        ObjectAnimator.ofFloat(binding.tvMadeBy, "alpha", 0.45f, 1f).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun setupClickListeners() {
        binding.btnOpenPage1.setOnClickListener { openWeb(1) }
        binding.btnOpenPage2.setOnClickListener { openWeb(2) }

        binding.btnGetToken1.setOnClickListener { retrieveToken(1) }
        binding.btnGetToken2.setOnClickListener { retrieveToken(2) }

        binding.btnCopy1.setOnClickListener { copyToken(1) }
        binding.btnCopy2.setOnClickListener { copyToken(2) }

        binding.btnTelegram.setOnClickListener { openTelegram() }
        binding.btnClearData.setOnClickListener { confirmClearData() }

        binding.btnApply.setOnClickListener { openApply() }
    }

    private fun openApply() {
        val t1 = binding.etToken1.text?.toString()?.trim().orEmpty()
        val t2 = binding.etToken2.text?.toString()?.trim().orEmpty()
        if (t1.isBlank() || t2.isBlank()) {
            Toast.makeText(this, R.string.need_tokens, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, ApplyActivity::class.java)
        intent.putExtra(ApplyActivity.EXTRA_TOKEN1, t1)
        intent.putExtra(ApplyActivity.EXTRA_TOKEN2, t2)
        startActivity(intent)
    }

    private fun openWeb(slot: Int) {
        val active = SessionStore.getActiveSlot(this)
        if (active != slot) {
            SessionStore.saveCookiesForSlot(this, active)
        }
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra(WebViewActivity.EXTRA_SLOT, slot)
        startActivity(intent)
    }

    private fun retrieveToken(slot: Int) {
        val statusView = if (slot == 1) binding.tvStatus1 else binding.tvStatus2
        val tokenField = if (slot == 1) binding.etToken1 else binding.etToken2
        val copyBtn = if (slot == 1) binding.btnCopy1 else binding.btnCopy2
        val getBtn = if (slot == 1) binding.btnGetToken1 else binding.btnGetToken2

        statusView.visibility = View.VISIBLE
        statusView.setTextColor(getColor(R.color.text_secondary))
        statusView.text = getString(R.string.loading)
        getBtn.isEnabled = false
        tokenField.setText("")
        copyBtn.isEnabled = false

        handler.postDelayed({
            SessionStore.restoreSlot(this, slot)

            val token = extractTokenFromCookies()
            getBtn.isEnabled = true

            if (token.isNullOrBlank()) {
                statusView.setTextColor(getColor(R.color.error))
                statusView.text = getString(R.string.not_logged_in)
                Toast.makeText(this, R.string.cookie_unavailable, Toast.LENGTH_LONG).show()
            } else {
                SessionStore.setToken(this, slot, token)
                SessionStore.saveCookiesForSlot(this, slot)
                tokenField.setText(token)
                copyBtn.isEnabled = true
                statusView.setTextColor(getColor(R.color.success))
                statusView.text = "Token $slot saved"
            }
        }, 350)
    }

    private fun extractTokenFromCookies(): String? {
        val cm = CookieManager.getInstance()
        cm.flush()
        for (url in SessionStore.COOKIE_URLS) {
            val header = cm.getCookie(url) ?: continue
            val token = header.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("$TOKEN_NAME=") }
                ?.substringAfter("=")
                ?.takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
    }

    private fun copyToken(slot: Int) {
        val field: TextInputEditText = if (slot == 1) binding.etToken1 else binding.etToken2
        val token = field.text?.toString()?.trim()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, R.string.not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("token_$slot", token))
        Toast.makeText(this, R.string.token_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openTelegram() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)).apply {
                    setPackage("org.telegram.messenger")
                }
            )
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
        }
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_data)
            .setMessage(R.string.clear_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                SessionStore.clearAll(this)
                WebStorage.getInstance().deleteAllData()
                binding.etToken1.setText("")
                binding.etToken2.setText("")
                binding.btnCopy1.isEnabled = false
                binding.btnCopy2.isEnabled = false
                binding.tvStatus1.visibility = View.GONE
                binding.tvStatus2.visibility = View.GONE
                Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
