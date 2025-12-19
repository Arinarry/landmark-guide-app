package com.example.diplom

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {
    private lateinit var sharedPref: SharedPreferences
    private lateinit var userEmail: String
    private var userId: Int = -1
    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        userEmail = sharedPref.getString("user_email", "") ?: ""
        userId = sharedPref.getInt("user_id", -1)
        apiService = ApiService(this)

        loadUserProfile()

        findViewById<Button>(R.id.chooseImageButton).setOnClickListener {
            openImageChooser()
        }

        findViewById<Button>(R.id.changeButton).setOnClickListener {
            showChangePasswordDialog()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveProfileChanges()
        }

        findViewById<Button>(R.id.deleteButton).setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val user = apiService.getUser(userEmail)
                user?.let {
                    runOnUiThread {
                        findViewById<TextView>(R.id.editName).hint = it.name ?: ""
                        findViewById<TextView>(R.id.editName).text = it.name?.let { name ->
                            SpannableStringBuilder(name)
                        }

                        Log.d("EditProfile", "Avatar URI: ${it.avatarUri}")

                        when {
                            it.avatarUri.isNullOrEmpty() -> {
                                Glide.with(this@EditProfileActivity)
                                    .load(R.drawable.avatar2)
                                    .circleCrop()
                                    .into(findViewById(R.id.profileImage))
                            }
                            it.avatarUri.startsWith("http") || it.avatarUri.startsWith("/avatars/") -> {
                                val avatarUrl = if (it.avatarUri.startsWith("/avatars/")) {
                                    "${ApiService.BASE_URL}${it.avatarUri}"
                                } else {
                                    it.avatarUri
                                }

                                Glide.with(this@EditProfileActivity)
                                    .load(avatarUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.avatar2)
                                    .error(R.drawable.avatar2)
                                    .into(findViewById(R.id.profileImage))
                            }
                            else -> {
                                try {
                                    Glide.with(this@EditProfileActivity)
                                        .load(Uri.parse(it.avatarUri))
                                        .circleCrop()
                                        .placeholder(R.drawable.avatar2)
                                        .error(R.drawable.avatar2)
                                        .into(findViewById(R.id.profileImage))
                                } catch (e: Exception) {
                                    Log.e("EditProfile", "Error parsing avatar URI", e)
                                    Glide.with(this@EditProfileActivity)
                                        .load(R.drawable.avatar2)
                                        .circleCrop()
                                        .into(findViewById(R.id.profileImage))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@EditProfileActivity,
                        "Ошибка загрузки профиля: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("EditProfile", "Error loading user profile", e)
                }
            }
        }
    }

    private fun saveProfileChanges() {
        val newName = findViewById<TextView>(R.id.editName).text.toString()
        if (newName.isEmpty()) {
            Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val avatarUri = selectedImageUri?.let { uri ->
                    try {
                        val uploadedUri = apiService.uploadAvatar(userId, uri)
                        Log.d("EditProfile", "Avatar uploaded: $uploadedUri")
                        uploadedUri
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@EditProfileActivity,
                                "Ошибка загрузки аватара: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.e("EditProfile", "Error uploading avatar", e)
                        }
                        null
                    }
                }

                val success = apiService.updateUser(
                    userId,
                    newName,
                    avatarUri
                )

                runOnUiThread {
                    if (success == true) {
                        Toast.makeText(
                            this@EditProfileActivity,
                            "Профиль успешно обновлен",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@EditProfileActivity,
                            "Ошибка обновления профиля",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@EditProfileActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("EditProfile", "Error saving profile", e)
                }
            }
        }
    }
    private fun showChangePasswordDialog() {
        showPasswordDialog(
            title = "Подтвердите текущий пароль",
            message = "Введите текущий пароль для подтверждения",
            positiveButtonText = "Продолжить",
            onSuccess = { oldPassword ->
                showNewPasswordDialog(oldPassword)
            }
        )
    }

    private fun getCurrentUserEmail(): String {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("user_email", "") ?: ""
    }

    private fun showPasswordDialog(
        title: String,
        message: String,
        positiveButtonText: String,
        onSuccess: (String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password2, null)

        dialogView.findViewById<TextView>(R.id.titleText).text = title
        dialogView.findViewById<TextView>(R.id.messageText).text = message
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(positiveButtonText) { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    val email = getCurrentUserEmail()
                    lifecycleScope.launch {
                        try {
                            val user = apiService.loginUser(email, password)
                            if (user != null) {
                                onSuccess(password)
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@EditProfileActivity,
                                        "Неверный пароль",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@EditProfileActivity,
                                    "Ошибка проверки пароля: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Введите пароль", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
    }


    private fun showNewPasswordDialog(oldPassword: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null) as LinearLayout

        dialogView.findViewById<TextView>(R.id.titleText).apply {
            text = "Смена пароля"
        }

        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText1)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Сменить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
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
                newPassword == oldPassword -> {
                    Toast.makeText(this, "Новый пароль должен отличаться от старого", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    lifecycleScope.launch {
                        try {
                            val success = apiService.updatePassword(
                                userId,
                                oldPassword,
                                newPassword
                            )
                            if (success == true) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@EditProfileActivity,
                                        "Пароль успешно изменен",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    dialog.dismiss()
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@EditProfileActivity,
                                        "Ошибка изменения пароля",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@EditProfileActivity,
                                    "Ошибка сети: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
    }

    private fun showDeleteAccountConfirmationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Удаление аккаунта")
            .setMessage("Вы уверены, что хотите удалить свой аккаунт? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteUserAccount()
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(resources.getColor(android.R.color.black))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(resources.getColor(android.R.color.black))
    }

    private fun deleteUserAccount() {
        lifecycleScope.launch {
            try {
                val success = apiService.deleteUser(userId)

                if (success == true) {
                    sharedPref.edit().clear().apply()

                    runOnUiThread {
                        val intent = Intent(this@EditProfileActivity, UserActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()

                        Toast.makeText(
                            this@EditProfileActivity,
                            "Аккаунт успешно удален",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@EditProfileActivity,
                            "Ошибка при удалении аккаунта",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@EditProfileActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE_REQUEST) {
            data?.data?.let { uri ->
                selectedImageUri = uri
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .placeholder(R.drawable.avatar2)
                    .error(R.drawable.avatar2)
                    .into(findViewById(R.id.profileImage))
            }
        }
    }
}
