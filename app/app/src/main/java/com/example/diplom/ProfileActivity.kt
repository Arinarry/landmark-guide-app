package com.example.diplom

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity: AppCompatActivity() {
    private lateinit var navManager: BottomNavManager
    private lateinit var sharedPref: SharedPreferences
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        ActivityHistoryManager.addActivity(this::class.java)

        sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        apiService = ApiService(this)

        val userEmail = sharedPref.getString("user_email", "") ?: ""

        loadUserData(userEmail)

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            sharedPref.edit().clear().apply()
            startActivity(Intent(this, UserActivity::class.java))
            finishAffinity()
        }

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val previousActivity = ActivityHistoryManager.getPreviousActivity()

            if (previousActivity != null && previousActivity != this::class.java && previousActivity != UserActivity::class.java) {
                ActivityHistoryManager.removeLastActivity()
                val intent = Intent(this, previousActivity)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else if (previousActivity == UserActivity::class.java) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        val likeButton = findViewById<Button>(R.id.likeButton)
        likeButton.setOnClickListener {
            val intent = Intent(this, FavoriteActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val editButton = findViewById<Button>(R.id.editButton)
        editButton.setOnClickListener {
            val userEmail = sharedPref.getString("user_email", "") ?: ""
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("user_email", userEmail)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }


        val menuButtons: List<Button> = listOf(
            findViewById(R.id.home),
            findViewById(R.id.search),
            findViewById(R.id.like),
            findViewById(R.id.photo),
            findViewById(R.id.user)
        )

        navManager = BottomNavManager(menuButtons)

        menuButtons.forEach { button ->
            button.setOnClickListener {
                navManager.setSelected(button)
                handleNavigation(button.id)
            }
        }

        navManager.setSelected(findViewById(R.id.user))

        val noteButton = findViewById<Button>(R.id.noteButton)
        noteButton.setOnClickListener {
            val userEmail = sharedPref.getString("user_email", "") ?: ""
            val intent = Intent(this, UserReviewsActivity::class.java)
            intent.putExtra("user_email", userEmail)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

    }

    override fun onResume() {
        super.onResume()
        val userEmail = sharedPref.getString("user_email", "") ?: ""
        loadUserData(userEmail)
    }

    private fun loadUserData(email: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = apiService.getUser(email)
                withContext(Dispatchers.Main) {
                    if (user != null) {
                        findViewById<TextView>(R.id.profileName).text = user.name
                        findViewById<TextView>(R.id.emailName).text = user.email

                        Log.d("ProfileActivity", "Avatar URI: ${user.avatarUri}") // Добавим лог для отладки

                        when {
                            user.avatarUri.isNullOrEmpty() -> {
                                Glide.with(this@ProfileActivity)
                                    .load(R.drawable.avatar2)
                                    .circleCrop()
                                    .into(findViewById(R.id.profileImage))
                            }
                            user.avatarUri.startsWith("http") || user.avatarUri.startsWith("/avatars/") -> {
                                val avatarUrl = if (user.avatarUri.startsWith("/avatars/")) {
                                    "${ApiService.BASE_URL}${user.avatarUri}"
                                } else {
                                    user.avatarUri
                                }

                                Glide.with(this@ProfileActivity)
                                    .load(avatarUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.avatar2)
                                    .error(R.drawable.avatar2)
                                    .into(findViewById(R.id.profileImage))
                            }
                            else -> {
                                try {
                                    Glide.with(this@ProfileActivity)
                                        .load(Uri.parse(user.avatarUri))
                                        .circleCrop()
                                        .placeholder(R.drawable.avatar2)
                                        .error(R.drawable.avatar2)
                                        .into(findViewById(R.id.profileImage))
                                } catch (e: Exception) {
                                    Log.e("ProfileActivity", "Error loading avatar", e)
                                    Glide.with(this@ProfileActivity)
                                        .load(R.drawable.avatar2)
                                        .circleCrop()
                                        .into(findViewById(R.id.profileImage))
                                }
                            }
                        }
                    } else {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Данные пользователя не найдены",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Ошибка при загрузке данных пользователя: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("ProfileActivity", "Error loading user data", e)
                }
            }
        }
    }

    private fun handleNavigation(buttonId: Int) {
        when (buttonId) {
            R.id.home -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            R.id.search -> {
                val intent = Intent(this, SearchActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            R.id.photo -> {
                val intent = Intent(this, PhotoActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            R.id.like -> {
                val intent = Intent(this, FavoriteActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            R.id.user -> {
                val intent = Intent(this, UserActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }
}