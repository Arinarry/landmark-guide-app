package com.example.diplom

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import android.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserActivity : AppCompatActivity() {
    private lateinit var navManager: BottomNavManager
    private lateinit var apiService: ApiService
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        ActivityHistoryManager.addActivity(this::class.java)

        sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        if (isUserLoggedIn()) {
            navigateTo(ProfileActivity::class.java)
            finish()
            return
        }
        initViews()
    }

    private fun isUserLoggedIn(): Boolean {
        return sharedPref.getBoolean("is_logged_in", false)
    }

    private fun initViews() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val loginForm = findViewById<LinearLayout>(R.id.loginForm)
        val registerForm = findViewById<LinearLayout>(R.id.registerForm)
        apiService = ApiService(this)
        findViewById<Button>(R.id.forgotPasswordButton).setOnClickListener {
            showPasswordResetDialog()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        loginForm.visibility = View.VISIBLE
                        registerForm.visibility = View.GONE
                    }
                    1 -> {
                        loginForm.visibility = View.GONE
                        registerForm.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}

        })

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val previousActivity = ActivityHistoryManager.getPreviousActivity()

            if (previousActivity != null && previousActivity != this::class.java && previousActivity != ProfileActivity::class.java) {
                ActivityHistoryManager.removeLastActivity()
                val intent = Intent(this, previousActivity)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }else if (previousActivity == ProfileActivity::class.java) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        // Навигация через нижнее меню
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

        findViewById<Button>(R.id.registerButton).setOnClickListener {
            registerUser()
        }

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            loginUser()
        }
    }

    private fun showPasswordResetDialog() {
        val context = this

        val emailInput = EditText(context).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(57, 20, 50, 20)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Восстановление пароля")
            .setMessage("Введите email для восстановления пароля")
            .setView(emailInput)
            .setPositiveButton("Отправить") { _, _ ->
                val email = emailInput.text.toString()
                if (isValidEmail(email)) {
                    resetPassword(email)
                } else {
                    Toast.makeText(context, "Введите корректный email", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(resources.getColor(android.R.color.black))
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(resources.getColor(android.R.color.black))
    }

    private fun showCodeVerificationDialog(email: String, userId: Int) {
        val codeInput = EditText(this).apply {
            hint = "Код подтверждения"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(57, 20, 50, 20)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Подтверждение сброса")
            .setMessage("Введите код, отправленный на $email")
            .setView(codeInput)
            .setPositiveButton("Подтвердить") { _, _ ->
                val code = codeInput.text.toString()
                if (code.isNotEmpty()) {
                    verifyResetCode(email, code, userId)
                } else {
                    Toast.makeText(this, "Введите код подтверждения", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
    }

    private fun verifyResetCode(email: String, code: String, userId: Int) {
        lifecycleScope.launch {
            try {
                val verified = withContext(Dispatchers.IO) {
                    apiService.verifyResetCode(email, code) != null
                }

                runOnUiThread {
                    if (verified) {
                        showNewPasswordDialog(userId)
                    } else {
                        Toast.makeText(
                            this@UserActivity,
                            "Неверный код подтверждения",
                            Toast.LENGTH_SHORT
                        ).show()
                        showCodeVerificationDialog(email, userId)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@UserActivity,
                        "Ошибка проверки кода: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun showNewPasswordDialog(userId: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null) as LinearLayout

        dialogView.findViewById<TextView>(R.id.titleText).apply {
            text = "Сброс пароля"
        }

        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText1)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            val newPassword = newPasswordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            when {
                newPassword.isEmpty() || confirmPassword.isEmpty() -> {
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                }
                newPassword.length < 8 -> {
                    Toast.makeText(this, "Пароль должен содержать не менее 8 символов", Toast.LENGTH_SHORT).show()
                }
                newPassword != confirmPassword -> {
                    Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    lifecycleScope.launch {
                        try {
                            val success = apiService.completePasswordReset(
                                userId,
                                newPassword
                            )
                            if (success == true) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@UserActivity,
                                        "Пароль успешно изменен",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    dialog.dismiss()
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@UserActivity,
                                        "Ошибка изменения пароля",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@UserActivity,
                                    "Ошибка сети: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, android.R.color.black))
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, android.R.color.black))
    }

    private fun resetPassword(email: String) {
        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    apiService.getUser(email)
                }
                if (user != null) {
                    val userId = user.id

                    val initiated = withContext(Dispatchers.IO) {
                        apiService.initiatePasswordReset(email)
                    }

                    runOnUiThread {
                        if (initiated == true) {
                            showCodeVerificationDialog(email, userId)
                        } else {
                            Toast.makeText(
                                this@UserActivity,
                                "Не удалось отправить код. Попробуйте позже.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@UserActivity,
                            "Пользователь с таким email не найден",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@UserActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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

    private fun registerUser() {
        val name = findViewById<EditText>(R.id.registerName).text.toString()
        val email = findViewById<EditText>(R.id.registerEmail).text.toString()
        val password = findViewById<EditText>(R.id.registerPassword).text.toString()
        val confirmPassword = findViewById<EditText>(R.id.registerConfirmPassword).text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this, "Введите корректный email", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 8) {
            Toast.makeText(this, "Пароль должен содержать минимум 8 символов", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Пароли не совпадают!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val isRegistered = apiService.registerUser(name, email, password)

                withContext(Dispatchers.Main) {
                    if (isRegistered == true) {
                        Toast.makeText(
                            this@UserActivity,
                            "Регистрация успешна!",
                            Toast.LENGTH_SHORT
                        ).show()
                        findViewById<TabLayout>(R.id.tabLayout).getTabAt(0)?.select()
                    } else {
                        Toast.makeText(
                            this@UserActivity,
                            "Ошибка регистрации (возможно, email занят)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Registration", "Network error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UserActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loginUser() {
        val email = findViewById<EditText>(R.id.loginEmail).text.toString()
        val password = findViewById<EditText>(R.id.loginPassword).text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = apiService.loginUser(email, password)

                withContext(Dispatchers.Main) {
                    if (user != null) {
                        sharedPref.edit().apply {
                            putBoolean("is_logged_in", true)
                            putString("user_email", email)
                            putInt("user_id", user.id)
                            apply()
                        }
                        Toast.makeText(
                            this@UserActivity,
                            "Вход выполнен!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateTo(ProfileActivity::class.java)
                        finish()
                    } else {
                        Toast.makeText(
                            this@UserActivity,
                            "Неверный email или пароль",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UserActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }


    private fun navigateTo(cls: Class<*>) {
        startActivity(Intent(this, cls))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}