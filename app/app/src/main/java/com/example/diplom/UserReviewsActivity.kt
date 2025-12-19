package com.example.diplom

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserReviewsActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService
    private lateinit var emptyFavoritesText: TextView
    private lateinit var deleteButton: ImageButton
    private lateinit var email: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReviewAdapter
    private var reviews = mutableListOf<Review>()
    private lateinit var locationService: LocationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_reviews)

        apiService = ApiService(this)
        locationService = LocationService()

        emptyFavoritesText = findViewById(R.id.emptyReviewsText)
        deleteButton = findViewById(R.id.delete_comment)
        recyclerView = findViewById(R.id.reviewsRecyclerView)
        email = intent.getStringExtra("user_email") ?: ""

        loadReviews()

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun loadReviews() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = apiService.getUser(email) ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@UserReviewsActivity,
                            "Пользователь не найден",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val reviewsList = apiService.getUserReviews(user.id)
                val landmarks = locationService.getLandmarks()
                val fullReviews = reviewsList.map { review ->
                    val landmarkName = landmarks.find { it.id == review.landmarkId }?.name ?: "Unknown"
                    Review(
                        landmarkId = review.landmarkId,
                        content = review.content,
                        date = review.date,
                        landmarkName = landmarkName
                    )
                }

                withContext(Dispatchers.Main) {
                    reviews = fullReviews.toMutableList()
                    if (reviews.isNotEmpty()) {
                        emptyFavoritesText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        recyclerView.layoutManager = LinearLayoutManager(this@UserReviewsActivity)

                        adapter = ReviewAdapter(
                            reviews,
                            { position -> showDeleteDialog(position) },
                            { position -> openLandmarkDetail(position) }
                        )
                        recyclerView.adapter = adapter
                    } else {
                        emptyFavoritesText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UserReviewsActivity,
                        "Ошибка при загрузке отзывов",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("UserReviewsActivity", "Error loading reviews", e)
                }
            }
        }
    }

    private fun openLandmarkDetail(position: Int) {
        val review = reviews[position]
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val landmarks = locationService.getLandmarks()
                val landmark = landmarks.find { it.id == review.landmarkId }

                withContext(Dispatchers.Main) {
                    if (landmark != null) {
                        val intent = Intent(this@UserReviewsActivity, LandmarkDetailActivity::class.java).apply {
                            putExtra("landmark", landmark)
                            putExtra("from_comment", true)
                        }
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    } else {
                        Toast.makeText(
                            this@UserReviewsActivity,
                            "Достопримечательность не найдена",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UserReviewsActivity,
                        "Ошибка при загрузке данных",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("UserReviewsActivity", "Error loading landmark", e)
                }
            }
        }
    }

    private fun showDeleteDialog(position: Int) {
        val review = reviews[position]
        val dialog = AlertDialog.Builder(this)
            .setTitle("Удаление отзыва")
            .setMessage("Вы уверены, что хотите удалить этот отзыв?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteReview(position)
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(resources.getColor(android.R.color.black))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(resources.getColor(android.R.color.black))
    }

    private fun deleteReview(position: Int) {
        val review = reviews[position]
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userId = sharedPref.getInt("user_id", -1)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = apiService.deleteComment(
                    userId,
                    review.landmarkId,
                    review.content
                )

                withContext(Dispatchers.Main) {
                    if (success == true) {
                        reviews.removeAt(position)
                        adapter.notifyItemRemoved(position)

                        if (reviews.isEmpty()) {
                            emptyFavoritesText.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(
                            this@UserReviewsActivity,
                            "Ошибка при удалении отзыва",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UserReviewsActivity,
                        "Ошибка при удалении отзыва",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("UserReviewsActivity", "Error deleting review", e)
                }
            }
        }
    }
}
