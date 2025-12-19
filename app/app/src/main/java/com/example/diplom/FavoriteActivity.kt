package com.example.diplom

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.diplom.model.Landmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoriteActivity : AppCompatActivity() {
    private lateinit var navManager: BottomNavManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoriteAdapter
    private lateinit var emptyFavoritesText: TextView
    private lateinit var userEmail: String
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite)

        ActivityHistoryManager.addActivity(this::class.java)
        initViews()
        setupNavigation()
        loadUserEmail()
        setupRecyclerView()
    }

    private fun initViews() {
        emptyFavoritesText = findViewById(R.id.emptyFavoritesText)
        recyclerView = findViewById(R.id.favoritesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadUserEmail() {
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        userEmail = sharedPref.getString("user_email", null).toString()
        userId = sharedPref.getInt("user_id", -1)
    }

    private fun setupNavigation() {
        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener { handleBackNavigation() }

        val menuButtons: List<Button> = listOf(
            findViewById(R.id.home),
            findViewById(R.id.search),
            findViewById(R.id.like),
            findViewById(R.id.photo),
            findViewById(R.id.user)
        )

        navManager = BottomNavManager(menuButtons).apply {
                setSelected(findViewById(R.id.like))
            }

        menuButtons.forEach { button ->
                button.setOnClickListener {
                    navManager.setSelected(button)
                    handleNavigation(button.id)
                }
            }
    }

    private fun setupRecyclerView() {
        adapter = FavoriteAdapter(emptyList()) { landmark, isFavorite ->
            lifecycleScope.launch {
                handleFavoriteClick(landmark, isFavorite)
            }
        }
        recyclerView.adapter = adapter
    }

    private suspend fun handleFavoriteClick(landmark: Landmark, isFavorite: Boolean) {
        try {
            if (isFavorite) {
                FavoritesManager.addFavorite(userId, landmark.id)
                showToast("Добавлено в избранное")
            } else {
                FavoritesManager.removeFavorite(userId, landmark.id)
                showToast("Удалено из избранного")
            }
        } catch (e: Exception) {
            showToast("Ошибка при обновлении избранного")
        }
    }

    private suspend fun refreshFavorites() {
        val updatedLandmarks = withContext(Dispatchers.IO) {
            getLandmarksFromFavorites()
        }
        withContext(Dispatchers.Main) {
            adapter.updateItems(updatedLandmarks)
            showEmptyState(updatedLandmarks.isEmpty())
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@FavoriteActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleBackNavigation() {
        val previousActivity = ActivityHistoryManager.getPreviousActivity()
        val targetActivity = when {
            previousActivity != null && previousActivity != this::class.java -> {
                ActivityHistoryManager.removeLastActivity()
                previousActivity
            }
            else -> MainActivity::class.java
        }

        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun handleNavigation(buttonId: Int) {
        val targetActivity = when (buttonId) {
            R.id.home -> MainActivity::class.java
            R.id.search -> SearchActivity::class.java
            R.id.photo -> PhotoActivity::class.java
            R.id.like -> FavoriteActivity::class.java
            R.id.user -> UserActivity::class.java
            else -> return
        }

        startActivity(Intent(this, targetActivity))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun getLandmarksFromFavorites(): List<Landmark> {
        val favoriteIds = FavoritesManager.getFavorites()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
        return LocationService().getLandmarks().filter { it.id in favoriteIds }
    }


    private fun showEmptyState(show: Boolean) {
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyFavoritesText.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            userId?.let { FavoritesManager.syncFavorites(it) }
            refreshFavorites()
        }
    }
}