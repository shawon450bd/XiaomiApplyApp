package com.example.xiaomitokenviewer

import android.content.Context
import android.webkit.CookieManager

/**
 * Persists two independent cookie sessions + tokens.
 * CookieManager is a singleton, so we save/restore cookie strings per slot.
 */
object SessionStore {

    private const val PREFS = "dual_session_prefs"
    private const val KEY_COOKIES_1 = "cookies_slot_1"
    private const val KEY_COOKIES_2 = "cookies_slot_2"
    private const val KEY_TOKEN_1 = "token_slot_1"
    private const val KEY_TOKEN_2 = "token_slot_2"
    private const val KEY_ACTIVE = "active_slot"

    val COOKIE_URLS = listOf(
        "https://new-ams.c.mi.com",
        "https://c.mi.com",
        "https://account.xiaomi.com",
        "https://web.account.xiaomi.com",
        "https://new.c.mi.com",
        "https://www.mi.com",
        "https://api.account.xiaomi.com"
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getToken(ctx: Context, slot: Int): String =
        prefs(ctx).getString(if (slot == 1) KEY_TOKEN_1 else KEY_TOKEN_2, "") ?: ""

    fun setToken(ctx: Context, slot: Int, token: String) {
        prefs(ctx).edit()
            .putString(if (slot == 1) KEY_TOKEN_1 else KEY_TOKEN_2, token)
            .apply()
    }

    fun getActiveSlot(ctx: Context): Int =
        prefs(ctx).getInt(KEY_ACTIVE, 1)

    fun setActiveSlot(ctx: Context, slot: Int) {
        prefs(ctx).edit().putInt(KEY_ACTIVE, slot).apply()
    }

    /** Snapshot all cookies for relevant domains into one multi-line string. */
    fun captureCurrentCookies(): String {
        val cm = CookieManager.getInstance()
        cm.flush()
        val parts = mutableListOf<String>()
        for (url in COOKIE_URLS) {
            val c = cm.getCookie(url) ?: continue
            if (c.isNotBlank()) {
                parts.add("$url|||$c")
            }
        }
        return parts.joinToString("\n")
    }

    fun saveCookiesForSlot(ctx: Context, slot: Int) {
        val data = captureCurrentCookies()
        prefs(ctx).edit()
            .putString(if (slot == 1) KEY_COOKIES_1 else KEY_COOKIES_2, data)
            .apply()
    }

    fun getSavedCookies(ctx: Context, slot: Int): String =
        prefs(ctx).getString(if (slot == 1) KEY_COOKIES_1 else KEY_COOKIES_2, "") ?: ""

    /** Clear CookieManager then restore a slot's cookies. */
    fun restoreSlot(ctx: Context, slot: Int) {
        val cm = CookieManager.getInstance()
        // Clear all first so the other slot doesn't leak in
        cm.removeAllCookies(null)
        cm.flush()

        val data = getSavedCookies(ctx, slot)
        if (data.isBlank()) {
            cm.flush()
            setActiveSlot(ctx, slot)
            return
        }

        for (line in data.split("\n")) {
            if (!line.contains("|||")) continue
            val (url, cookieHeader) = line.split("|||", limit = 2)
            // cookieHeader is "name=value; name2=value2"
            for (pair in cookieHeader.split(";")) {
                val trimmed = pair.trim()
                if (trimmed.contains("=")) {
                    cm.setCookie(url, trimmed)
                }
            }
        }
        cm.flush()
        setActiveSlot(ctx, slot)
    }

    /** Clear BOTH slots + live cookies + tokens. */
    fun clearAll(ctx: Context) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        prefs(ctx).edit().clear().apply()
    }
}
