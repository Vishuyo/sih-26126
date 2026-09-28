package com.example.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Manages local persistence of UGV stream endpoints using SharedPreferences.
 * Supports saving new IPs, retrieving recent endpoints, and deleting old ones.
 */
class StreamUrlStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ugv_stream_endpoints_prefs"
        private const val KEY_LAST_URL = "last_used_stream_url"
        private const val KEY_SAVED_URLS = "saved_stream_urls_json"
        const val DEFAULT_STREAM_URL = "192.168.0.105:8080/stream"
        private const val MAX_SAVED_URLS = 10
    }

    /**
     * Returns the list of locally saved stream URLs in order of recency.
     */
    fun getSavedUrls(): List<String> {
        val jsonString = prefs.getString(KEY_SAVED_URLS, null)
        if (jsonString.isNullOrEmpty()) {
            return listOf(DEFAULT_STREAM_URL)
        }
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)?.trim()
                if (!item.isNullOrEmpty() && !list.contains(item)) {
                    list.add(item)
                }
            }
            if (list.isEmpty()) listOf(DEFAULT_STREAM_URL) else list
        } catch (e: Exception) {
            listOf(DEFAULT_STREAM_URL)
        }
    }

    /**
     * Retrieves the most recently used stream URL.
     */
    fun getLastUsedUrl(): String {
        return prefs.getString(KEY_LAST_URL, DEFAULT_STREAM_URL) ?: DEFAULT_STREAM_URL
    }

    /**
     * Saves a stream URL locally. Moves it to the front of recent endpoints.
     * Capped to [MAX_SAVED_URLS].
     */
    fun saveUrl(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return getSavedUrls()

        val currentList = getSavedUrls().toMutableList()
        currentList.remove(trimmed)
        currentList.add(0, trimmed) // Place most recent at the start

        val truncatedList = currentList.take(MAX_SAVED_URLS)
        val jsonArray = JSONArray()
        truncatedList.forEach { jsonArray.put(it) }

        prefs.edit()
            .putString(KEY_SAVED_URLS, jsonArray.toString())
            .putString(KEY_LAST_URL, trimmed)
            .apply()

        return truncatedList
    }

    /**
     * Deletes a stream URL from local storage.
     */
    fun deleteUrl(url: String): List<String> {
        val trimmed = url.trim()
        val currentList = getSavedUrls().toMutableList()
        currentList.remove(trimmed)

        val jsonArray = JSONArray()
        currentList.forEach { jsonArray.put(it) }

        val editor = prefs.edit().putString(KEY_SAVED_URLS, jsonArray.toString())
        if (getLastUsedUrl() == trimmed) {
            val nextLast = currentList.firstOrNull() ?: DEFAULT_STREAM_URL
            editor.putString(KEY_LAST_URL, nextLast)
        }
        editor.apply()

        return currentList
    }
}
