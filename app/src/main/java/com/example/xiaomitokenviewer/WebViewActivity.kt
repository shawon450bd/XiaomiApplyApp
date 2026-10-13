package com.example.xiaomitokenviewer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.xiaomitokenviewer.databinding.ActivityWebviewBinding

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private var slot: Int = 1

    companion object {
        const val EXTRA_SLOT = "slot"
        const val TARGET_URL = "https://new-ams.c.mi.com/global/forum-type/Redmi%20Phone"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        slot = intent.getIntExtra(EXTRA_SLOT, 1)

        // Before opening: restore THIS slot's cookies (other slot stays saved in prefs)
        SessionStore.restoreSlot(this, slot)

        setupToolbar()
        setupWebView()
        binding.webView.loadUrl(TARGET_URL)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finishWithSave() }
        binding.btnDone.setOnClickListener { finishWithSave() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.webProgress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.webProgress.visibility = View.GONE
                CookieManager.getInstance().flush()
                // Continuously keep this slot's cookies saved while browsing
                SessionStore.saveCookiesForSlot(this@WebViewActivity, slot)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.webProgress.progress = newProgress
                binding.webProgress.visibility =
                    if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun finishWithSave() {
        CookieManager.getInstance().flush()
        SessionStore.saveCookiesForSlot(this, slot)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finishWithSave()
        }
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        SessionStore.saveCookiesForSlot(this, slot)
        super.onPause()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
