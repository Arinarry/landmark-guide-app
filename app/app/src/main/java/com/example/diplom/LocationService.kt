package com.example.diplom

import android.util.Log
import com.example.diplom.model.Landmark
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getLandmarks(): List<Landmark> {
        return try {
            val url = "https://implacably-enticed-pekingese.cloudpub.ru/landmarks"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }

                response.body?.string()?.let { responseBody ->
                    parseLandmarks(responseBody)
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Error fetching landmarks", e)
            emptyList()
        }
    }

    private fun parseLandmarks(jsonString: String): List<Landmark> {
        val jsonObject = JSONObject(jsonString)
        val landmarksArray = jsonObject.getJSONArray("landmarks")

        return (0 until landmarksArray.length()).map { i ->
            val landmarkObject = landmarksArray.getJSONObject(i)
            Landmark(
                landmarkObject.getInt("id"),
                landmarkObject.getString("name"),
                landmarkObject.getString("description"),
                landmarkObject.getString("photo_url"),
                landmarkObject.getString("location"),
                landmarkObject.getString("coordinates"),
                landmarkObject.getString("tag")
            )
        }
    }
}
