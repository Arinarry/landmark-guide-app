package com.example.diplom

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.diplom.model.Landmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LandmarkDetailActivity : AppCompatActivity() {
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentInput: EditText
    private val commentsList = mutableListOf<Comment>()
    private lateinit var sharedPref: SharedPreferences
    private lateinit var apiService: ApiService
    private lateinit var commentsAdapter: CommentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landmark_detail)

        commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)
        commentsAdapter = CommentsAdapter(commentsList)

        commentsRecyclerView.adapter = commentsAdapter
        apiService = ApiService(this)
        sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)

        val landmark: Landmark? = intent.getParcelableExtra("landmark")

        if (landmark != null) {
            val favoriteButton = findViewById<ImageButton>(R.id.favorite_button)

            val nameTextView: TextView = findViewById(R.id.name)
            val descriptionTextView: TextView = findViewById(R.id.description)
            val photoImageView: ImageView = findViewById(R.id.photo)
            val locationTextView: TextView = findViewById(R.id.landmark_location)

            nameTextView.text = landmark.name
            descriptionTextView.text = landmark.description
            Glide.with(this).load(landmark.imageUrl).error(R.drawable.placeholder).into(photoImageView)
            locationTextView.text = landmark.location

            loadComments(landmark.id)

            val landmarkId = landmark.id

            if (FavoritesManager.isFavorite(landmarkId)) {
                favoriteButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black))
            } else {
                favoriteButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.LightGray))
            }

            favoriteButton.setOnClickListener {
                lifecycleScope.launch {
                    val userId = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        .getInt("user_id", -1)

                    val isCurrentlyFavorite = FavoritesManager.isFavorite(landmarkId)

                    try {
                        if (!isCurrentlyFavorite) {
                            FavoritesManager.addFavorite(userId, landmarkId)
                            withContext(Dispatchers.Main) {
                                favoriteButton.imageTintList = ColorStateList.valueOf(
                                    ContextCompat.getColor(this@LandmarkDetailActivity, R.color.black)
                                )
                                Toast.makeText(
                                    this@LandmarkDetailActivity,
                                    "Добавлено в избранное",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            FavoritesManager.removeFavorite(userId, landmarkId)
                            withContext(Dispatchers.Main) {
                                favoriteButton.imageTintList = ColorStateList.valueOf(
                                    ContextCompat.getColor(this@LandmarkDetailActivity, R.color.LightGray)
                                )
                                Toast.makeText(
                                    this@LandmarkDetailActivity,
                                    "Удалено из избранного",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@LandmarkDetailActivity,
                                "Ошибка: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(this, "Ошибка при загрузке данных достопримечательности", Toast.LENGTH_SHORT).show()
        }

        commentInput = findViewById(R.id.comment_input)
        val sendCommentButton = findViewById<ImageButton>(R.id.send_comment_button)

        sendCommentButton.setOnClickListener {
            if (isUserLoggedIn(this)) {
                sendComment()
            } else {
                Toast.makeText(this, "Пожалуйста, войдите в систему для отправки комментариев", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, UserActivity::class.java)
                startActivity(intent)
            }
        }

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val fromSearch = intent.getBooleanExtra("from_search", false)
            val fromMain = intent.getBooleanExtra("from_main", false)
            val fromComm = intent.getBooleanExtra("from_comment", false)

            if (fromSearch) {
                val intent = Intent(this, SearchActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else if (fromMain) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else if (fromComm) {
                val userEmail = sharedPref.getString("user_email", "") ?: ""
                val intent = Intent(this, UserReviewsActivity::class.java)
                intent.putExtra("user_email", userEmail)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }else {
                val intent = Intent(this, FavoriteActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        val goToMap = findViewById<Button>(R.id.goTo)
        goToMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            if (landmark != null) {
                val fromSearch = this.intent.getBooleanExtra("from_search", false)
                val fromMain = this.intent.getBooleanExtra("from_main", false)
                val fromComm = this.intent.getBooleanExtra("from_comment", false)
                if (fromSearch) {
                    intent.putExtra("from_search", true)
                }
                else if (fromMain){
                    intent.putExtra("from_main", true)
                }
                else if (fromComm){
                    intent.putExtra("from_comment", true)
                }
                intent.putExtra("landmark", landmark)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    private fun loadComments(landmarkId: Int) {
        lifecycleScope.launch {
            try {
                val comments = apiService.getCommentsByLandmark(landmarkId)
                commentsList.clear()
                commentsList.addAll(comments)
                commentsAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(
                    this@LandmarkDetailActivity,
                    "Ошибка загрузки комментариев: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("LandmarkDetail", "Error loading comments", e)
            }
        }
    }

    fun isUserLoggedIn(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("is_logged_in", false)
    }

    private fun getCurrentUserEmail(): String {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("user_email", "") ?: ""
    }

    private fun sendComment() {
        val commentText = commentInput.text.toString()

        if (commentText.isNotEmpty()) {
            val userEmail = getCurrentUserEmail()
            val userId = getSharedPreferences("user_prefs", MODE_PRIVATE)
                .getInt("user_id", -1)
            val landmark: Landmark? = intent.getParcelableExtra("landmark")
            val landmarkId = landmark?.id

            lifecycleScope.launch {
                try {
                    val user = apiService.getUser(userEmail)

                    if (user != null) {
                        val success =
                            landmarkId?.let { apiService.addComment(userId, commentText, it) }

                        if (success == true) {
                            val currentDate =
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(
                                    Date()
                                )

                            val userProfileImage: Any = when {
                                user.avatarUri.isNullOrEmpty() -> R.drawable.avatar2
                                user.avatarUri.startsWith("http") -> user.avatarUri
                                user.avatarUri.startsWith("/avatars/") -> "${ApiService.BASE_URL}${user.avatarUri}"
                                else -> try {
                                    Uri.parse(user.avatarUri)
                                } catch (e: Exception) {
                                    R.drawable.avatar2
                                }
                            }

                            val newComment = Comment(
                                userProfileImage,
                                user.name,
                                commentText,
                                currentDate
                            )

                            runOnUiThread {
                                commentsList.add(0, newComment)
                                commentsAdapter.notifyItemInserted(0)
                                commentInput.text.clear()
                                Toast.makeText(
                                    this@LandmarkDetailActivity,
                                    "Комментарий отправлен",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            loadComments(landmarkId)
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@LandmarkDetailActivity,
                                    "Ошибка при отправке комментария",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@LandmarkDetailActivity,
                                "Ошибка: пользователь не найден",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this@LandmarkDetailActivity,
                            "Ошибка сети: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("LandmarkDetail", "Error sending comment", e)
                    }
                }
            }
        } else {
            Toast.makeText(
                this,
                "Комментарий не может быть пустым",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
