package com.example.remind_ai.stage1

import com.example.remind_ai.model.QuranAyahResult
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object QuranAyahService {

    fun fetchAyah(
        reference: String,
        onSuccess: (QuranAyahResult) -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val url = URL("https://api.alquran.cloud/v1/ayah/$reference/editions/quran-uthmani,en.asad")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val data = json.getJSONArray("data")

                val arabicObj = findEdition(data, "quran-uthmani")
                val englishObj = findEdition(data, "en.asad")

                onSuccess(
                    QuranAyahResult(
                        reference = reference,
                        arabic = arabicObj?.optString("text").orEmpty(),
                        translation = englishObj?.optString("text").orEmpty()
                    )
                )
            } catch (e: Exception) {
                onError(e.message ?: "Failed to fetch ayah")
            }
        }
    }

    private fun findEdition(array: JSONArray, identifier: String): JSONObject? {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val edition = item.getJSONObject("edition")
            if (edition.optString("identifier") == identifier) {
                return item
            }
        }
        return null
    }
}
