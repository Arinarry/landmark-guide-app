package com.example.diplom

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ApiService(val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://implacably-enticed-pekingese.cloudpub.ru" //http://192.168.1.96:8000
    }

    private val baseUrl = "https://implacably-enticed-pekingese.cloudpub.ru"
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun registerUser(name: String, email: String, password: String): Boolean? {
        val json = JSONObject().apply {
            put("name", name)
            put("email", email)
            put("password", password)
        }

        val request = Request.Builder()
            .url("$baseUrl/users/register")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { true }
    }

    suspend fun addFavorite(userId: Int, landmarkId: Int): Boolean? {
        val json = JSONObject().apply {
            put("user_id", userId)
            put("landmark_id", landmarkId)
        }

        val request = Request.Builder()
            .url("$baseUrl/favorites")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { true }
    }

    suspend fun removeFavorite(userId: Int, landmarkId: Int): Boolean? {
        val request = Request.Builder()
            .url("$BASE_URL/favorites?user_id=$userId&landmark_id=$landmarkId")
            .delete()
            .build()

        return executeRequest(request) { true }
    }

    suspend fun getFavorites(userId: Int): List<Int> {
        val request = Request.Builder()
            .url("$BASE_URL/favorites/$userId")
            .get()
            .build()

        return executeRequest(request) { response ->
            val json = JSONObject(response.body?.string() ?: "")
            val favoritesArray = json.getJSONArray("favorites")
            val favorites = mutableListOf<Int>()

            for (i in 0 until favoritesArray.length()) {
                favorites.add(favoritesArray.getInt(i))
            }
            favorites
        } ?: emptyList()
    }

    suspend fun loginUser(email: String, password: String): User? {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/users/login")
            .post(formBody)
            .build()

        return executeRequest(request) { response ->
            val json = JSONObject(response.body?.string() ?: "")
            User(
                id = json.getInt("id"),
                name = json.getString("name"),
                email = json.getString("email"),
                password = "",
                avatarUri = json.optString("avatar_uri", "@drawable/avatar2")
            )
        }
    }

    suspend fun getUser(userEmail: String): User? {
        val request = Request.Builder()
            .url("$BASE_URL/users/$userEmail")
            .get()
            .build()

        return executeRequest(request) { response ->
            val json = JSONObject(response.body?.string() ?: "")
            User(
                id = json.getInt("id"),
                name = json.getString("name"),
                email = json.getString("email"),
                password = "",
                avatarUri = json.optString("avatar_uri", "@drawable/avatar2")
            )
        }
    }

    suspend fun updateUser(userId: Int, name: String?, avatarUri: String?): Boolean? {
        val json = JSONObject().apply {
            if (name != null) put("name", name)
            if (avatarUri != null) put("avatar_uri", avatarUri)
        }

        val request = Request.Builder()
            .url("$BASE_URL/users/$userId")
            .put(json.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { true }
    }

    suspend fun updatePassword(userId: Int, oldPassword: String, newPassword: String): Boolean? {
        val formBody = FormBody.Builder()
            .add("old_password", oldPassword)
            .add("new_password", newPassword)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/users/$userId/password")
            .put(formBody)
            .build()

        return executeRequest(request) { true }
    }

    suspend fun initiatePasswordReset(email: String): Boolean? {
        val formBody = FormBody.Builder()
            .add("email", email)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/password-reset/initiate")
            .post(formBody)
            .build()

        return executeRequest(request) { true }
    }

    suspend fun verifyResetCode(email: String, code: String): Int? {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("code", code)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/password-reset/verify")
            .post(formBody)
            .build()

        return executeRequest(request) { response ->
            val json = JSONObject(response.body?.string() ?: "")
            if (json.getBoolean("verified")) {
                json.getInt("user_id")
            } else {
                null
            }
        }
    }

    suspend fun completePasswordReset(userId: Int, newPassword: String): Boolean {
        val formBody = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("new_password", newPassword)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/password-reset/complete")
            .post(formBody)
            .build()

        return executeRequest(request) {
            it.isSuccessful
        } ?: false
    }

    suspend fun deleteUser(userId: Int): Boolean? {
        val request = Request.Builder()
            .url("$BASE_URL/users/$userId")
            .delete()
            .build()

        return executeRequest(request) { true }
    }

    suspend fun addComment(userId: Int, commentText: String, landmarkId: Int): Boolean? {
        val currentDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val comment = JSONObject().apply {
            put("user_id", userId)
            put("comment", commentText)
            put("landmark_id", landmarkId)
            put("date", currentDate)
        }

        val request = Request.Builder()
            .url("$baseUrl/comments")
            .post(comment.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { true }
    }

    suspend fun getCommentsByLandmark(landmarkId: Int): List<Comment> {
        val request = Request.Builder()
            .url("$baseUrl/comments/landmark/$landmarkId")
            .get()
            .build()

        return executeRequest(request) { response ->
            val json = JSONObject(response.body?.string() ?: "")
            val commentsArray = json.getJSONArray("comments")
            val comments = mutableListOf<Comment>()

            for (i in 0 until commentsArray.length()) {
                val item = commentsArray.getJSONObject(i)
                val avatarUri = item.optString("avatar_uri", "")

                comments.add(Comment(
                    userProfileImage = when {
                        avatarUri.isEmpty() -> R.drawable.avatar2
                        avatarUri.startsWith("http") -> avatarUri
                        avatarUri.startsWith("/avatars/") -> "${ApiService.BASE_URL}${avatarUri}"
                        else -> try {
                            Uri.parse(avatarUri)
                        } catch (e: Exception) {
                            R.drawable.avatar2
                        }
                    },
                    userName = item.getString("user_name"),
                    commentText = item.getString("comment"),
                    commentDate = item.getString("date")
                ))
            }
            comments
        } ?: emptyList()
    }

    suspend fun getUserReviews(userId: Int): List<Review> {
        val request = Request.Builder()
            .url("$BASE_URL/comments/user/$userId")
            .get()
            .build()

        return executeRequest(request) { response ->
            val json = JSONObject(response.body?.string() ?: "")
            val reviewsArray = json.getJSONArray("reviews")
            val reviews = mutableListOf<Review>()

            for (i in 0 until reviewsArray.length()) {
                val item = reviewsArray.getJSONObject(i)
                reviews.add(Review(
                    landmarkId = item.getInt("landmark_id"),
                    landmarkName = item.getString("landmark_name"),
                    content = item.getString("comment"),
                    date = item.getString("date")
                ))
            }
            reviews
        } ?: emptyList()
    }

    suspend fun uploadAvatar(userId: Int, imageUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val fileName = when {
                        imageUri.scheme == "file" -> File(imageUri.path).name
                        else -> {
                            contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1 && cursor.moveToFirst()) {
                                    cursor.getString(nameIndex)
                                } else {
                                    "avatar_${System.currentTimeMillis()}.jpg"
                                }
                            } ?: "avatar_${System.currentTimeMillis()}.jpg"
                        }
                    }

                    // Получаем MIME-тип
                    val mimeType = contentResolver.getType(imageUri) ?: "image/*"

                    // Создаем RequestBody
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "file",
                            fileName,
                            inputStream.readBytes().toRequestBody(mimeType.toMediaTypeOrNull() ?: "image/*".toMediaType())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("$BASE_URL/users/$userId/avatar")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        json.getString("avatar_uri")
                    } else {
                        null
                    }
                } ?: null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun deleteComment(userId: Int, landmarkId: Int, commentText: String): Boolean? {
        val request = Request.Builder()
            .url("$BASE_URL/comments?user_id=$userId&landmark_id=$landmarkId&comment=${commentText.encodeUrl()}")
            .delete()
            .build()

        return executeRequest(request) { true }
    }

    private suspend fun <T> executeRequest(request: Request, handler: (Response) -> T): T? {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    handler(response)
                } else {
                    null
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun String.encodeUrl(): String {
        return this.replace(" ", "%20")
            .replace("\"", "%22")
            .replace("'", "%27")
    }
}