package com.example.diplom

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.diplom.model.Landmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class PhotoActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var flashBtn: Button
    private lateinit var outputDirectory: File
    private var imageUri: Uri? = null
    private var cameraControl: androidx.camera.core.Camera? = null
    private lateinit var imageCapture: ImageCapture
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val serverUrl = "https://pitiably-supersonic-gar.cloudpub.ru/predict"
    private val locationService = LocationService()
    private lateinit var progressBar: ProgressBar
    private lateinit var photoPreview: ImageView
    private var currentUploadCall: Call? = null

    // Оффлайн-режим
    private lateinit var tflite: Interpreter
    private val pendingUploads = LinkedBlockingQueue<Pair<File, String>>()
    private var isOnline = false
    private val OUTPUT_SIZE = 5
    private val IMAGE_SIZE = 224

    private var isCancelled = false

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkNetworkStatus()
            tryUploadPending()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)

        ActivityHistoryManager.addActivity(this::class.java)

        previewView = findViewById(R.id.previewView)
        val takePhotoBtn = findViewById<Button>(R.id.takePhoto)
        val galleryBtn = findViewById<Button>(R.id.gallery)
        val hintText = findViewById<TextView>(R.id.hintText)
        progressBar = findViewById(R.id.progressBar)
        photoPreview = findViewById(R.id.photoPreview)
        flashBtn = findViewById(R.id.flash)
        outputDirectory = getOutputDirectory()

        initTFLite()
        checkNetworkStatus()

        takePhotoBtn.setOnClickListener {
            if (photoPreview.visibility == View.VISIBLE) {
                hidePhotoPreview()
            } else {
                takePhoto()
            }
        }

        galleryBtn.setOnClickListener { openGallery() }
        flashBtn.setOnClickListener { toggleFlash() }

        hintText.postDelayed({
            hintText.visibility = View.GONE
        }, 5000)

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val previousActivity = ActivityHistoryManager.getPreviousActivity()

            if (previousActivity != null && previousActivity != this::class.java) {
                ActivityHistoryManager.removeLastActivity()
                val intent = Intent(this, previousActivity)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(networkReceiver)
    }

    private fun initTFLite() {
        try {
            val assetManager = assets
            val assetFileDescriptor = assetManager.openFd("landmark.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val byteArray = inputStream.readBytes()
            inputStream.close()
            assetFileDescriptor.close()

            val byteBuffer = ByteBuffer.allocateDirect(byteArray.size)
                .order(ByteOrder.nativeOrder())
            byteBuffer.put(byteArray)
            byteBuffer.rewind()

            tflite = Interpreter(byteBuffer)

            val inputTensor = tflite.getInputTensor(0)
            val outputTensor = tflite.getOutputTensor(0)

            Log.d("TFLite", "Input shape: ${inputTensor.shape().contentToString()}")
            Log.d("TFLite", "Output shape: ${outputTensor.shape().contentToString()}")

        } catch (e: Exception) {
            Log.e("TFLite", "Error loading model", e)
            Toast.makeText(this, "Ошибка загрузки модели", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNetworkStatus() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        isOnline = networkInfo != null && networkInfo.isConnected
    }

    private fun processImageOffline(imageFile: File) {
        if (isCancelled) {
            imageFile.delete()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (isCancelled) return@launch

                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }

                val bitmap = BitmapFactory.decodeFile(imageFile.path, options)?.let {
                    Bitmap.createScaledBitmap(it, IMAGE_SIZE, IMAGE_SIZE, true)
                } ?: throw IOException("Не удалось загрузить изображение")

                if (isCancelled) return@launch

                val inputBuffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                    for (y in 0 until IMAGE_SIZE) {
                        for (x in 0 until IMAGE_SIZE) {
                            if (isCancelled) return@launch
                            val pixel = bitmap.getPixel(x, y)
                            putFloat(Color.red(pixel) / 255.0f)
                            putFloat(Color.green(pixel) / 255.0f)
                            putFloat(Color.blue(pixel) / 255.0f)
                        }
                    }
                }

                val output = Array(1) { FloatArray(OUTPUT_SIZE) }
                tflite.run(inputBuffer, output)
                val (result, confidence) = processModelOutput(output[0])
                val confidencePercent = confidence * 100

                if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        if (confidencePercent >= 80) {
                            showOfflineResult(result, confidence)
                        } else {
                            Toast.makeText(
                                this@PhotoActivity,
                                "Не удалось уверенно распознать достопримечательность (${"%.0f".format(confidencePercent)}%)",
                                Toast.LENGTH_LONG
                            ).show()
                            hidePhotoPreview()
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            this@PhotoActivity,
                            "Оффлайн-распознавание не удалось",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Log.e("Offline", "Ошибка обработки", e)
                }
            }
        }
    }

    private fun processModelOutput(output: FloatArray): Pair<String, Float>  {
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: -1
        val confidence = output[maxIndex]

        val result = when(maxIndex) {
            0 -> "monument_alexanderthird"
            1 -> "monument_to_the_first traffic_light"
            2 -> "novat"
            3 -> "old_house"
            4 -> "theater_globus"
            else -> "unknown"
        }
        return Pair(result, confidence)
    }

    private fun showOfflineResult(landmarkTag: String, confidence: Float) {
        val landmarkName = translateLandmark(landmarkTag)
        val confidencePercent = confidence * 100

        findViewById<TextView>(R.id.hintText).apply {
            text = "Оффлайн результат: $landmarkName (${"%.0f".format(confidencePercent)}%)"
            visibility = View.VISIBLE
        }

        GlobalScope.launch(Dispatchers.Main) {
            val landmarks = withContext(Dispatchers.IO) {
                locationService.getLandmarks()
            }

            val fullLandmark = landmarks.find {
                it.name.equals(landmarkName, ignoreCase = true)
            }

            fullLandmark?.let {
                navigateToLandmarkDetail(it)
            }
        }
    }

    private fun tryUploadPending() {
        if (isOnline && pendingUploads.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                while (pendingUploads.isNotEmpty()) {
                    val (file, result) = pendingUploads.peek()
                    try {
                        if (uploadToServer(file, result)) {
                            pendingUploads.poll()
                            Log.d("Upload", "Pending upload successful")
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("Upload", "Failed to upload pending item", e)
                        break
                    }
                }
            }
        }
    }

    private val galleryResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                showPhotoPreview(uri)
                val file = uriToFile(uri)
                file?.let {
                    uploadImage(it)
                } ?: run {
                    Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleFlash() {
        val isFlashOn = cameraControl?.cameraInfo?.torchState?.value == TorchState.ON

        if (isFlashOn) {
            cameraControl?.cameraControl?.enableTorch(false)
            flashBtn.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.off, 0, 0)
        } else {
            cameraControl?.cameraControl?.enableTorch(true)
            flashBtn.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.on, 0, 0)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Камера не разрешена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setTargetRotation(previewView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                cameraControl = camera

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun takePhoto() {
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        Log.d("Camera", "File path: ${photoFile.absolutePath}")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@PhotoActivity, "Ошибка: ${exception.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (photoFile.exists()) {
                            imageUri = FileProvider.getUriForFile(
                                this@PhotoActivity,
                                "${packageName}.provider",
                                photoFile
                            )

                            runOnUiThread {
                                showPhotoPreview(imageUri)
                                uploadImage(photoFile)
                            }
                        } else {
                            Log.e("Camera", "Файл не найден после задержки")
                            runOnUiThread {
                                Toast.makeText(
                                    this@PhotoActivity,
                                    "Ошибка: не удалось сохранить фото",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }, 300)
                }
            }
        )
    }

    private fun showPhotoPreview(uri: Uri?) {
        if (uri != null) {
            isCancelled = false
            Glide.with(this)
                .load(uri)
                .into(photoPreview)

            photoPreview.visibility = View.VISIBLE
            flashBtn.isEnabled = false
            flashBtn.alpha = 0.5f
            val newDrawable = ContextCompat.getDrawable(this, R.drawable.krest)
            findViewById<Button>(R.id.takePhoto).setCompoundDrawablesWithIntrinsicBounds(null, newDrawable, null, null)
        }
    }

    private fun hidePhotoPreview() {
        isCancelled = true
        showLoading(false)

        currentUploadCall?.cancel()
        currentUploadCall = null

        photoPreview.visibility = View.GONE

        flashBtn.isEnabled = true
        flashBtn.alpha = 1f

        val originalDrawable = ContextCompat.getDrawable(this, R.drawable.circle)
        findViewById<Button>(R.id.takePhoto).setCompoundDrawablesWithIntrinsicBounds(null, originalDrawable, null, null)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryResultLauncher.launch(intent)
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(
                outputDirectory,
                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                    .format(System.currentTimeMillis()) + ".jpg"
            )
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "photos").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun uploadImage(file: File) {
        if (isCancelled) {
            file.delete()
            return
        }

        runOnUiThread { showLoading(true) }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (isCancelled) return@launch

                if (isOnline) {
                    val success = uploadToServer(file, "")
                    if (!success && !isCancelled) {
                        withContext(Dispatchers.Main) {
                            showLoading(false)
                            Toast.makeText(
                                this@PhotoActivity,
                                "Сервер недоступен. Используется оффлайн-режим.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (!isCancelled) processImageOffline(file)
                    }
                } else if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            this@PhotoActivity,
                            "Режим оффлайн. Данные будут отправлены позже.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (!isCancelled) processImageOffline(file)
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            this@PhotoActivity,
                            "Ошибка сети: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (!isCancelled) processImageOffline(file)
                }
            }
        }
    }
    private suspend fun uploadToServer(file: File, offlineResult: String): Boolean = withContext(Dispatchers.IO) {
        if (isCancelled) return@withContext false

        try {
            withContext(Dispatchers.Main) { showLoading(true) }

            if (!isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@PhotoActivity,
                        "Нет подключения к интернету",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@withContext false
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaType())
                )
                .apply {
                    if (offlineResult.isNotEmpty()) {
                        addFormDataPart("offline_result", offlineResult)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            currentUploadCall = client.newCall(request)
            val response = currentUploadCall?.execute()
            val result = response?.body?.string()

            if (isCancelled) return@withContext false

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (response?.isSuccessful == true && !isCancelled) {
                    try {
                        val json = JSONObject(result)
                        val landmark = json.getString("landmark")
                        val confidence = json.getDouble("confidence")
                        val confidencePercent = confidence * 100
                        val landmarkName = translateLandmark(landmark)

                        if (confidencePercent < 80) {
                            Toast.makeText(
                                this@PhotoActivity,
                                "Не удалось уверенно распознать достопримечательность (${"%.0f".format(confidencePercent)}%)",
                                Toast.LENGTH_LONG
                            ).show()
                            hidePhotoPreview()
                            return@withContext false
                        }

                        launch {
                            val landmarks = withContext(Dispatchers.IO) {
                                if (!isCancelled) locationService.getLandmarks() else emptyList()
                            }

                            if (!isCancelled) {
                                val fullLandmark = landmarks.find {
                                    it.name.equals(landmarkName, ignoreCase = true) ||
                                            it.tag.equals(landmarkName, ignoreCase = true)
                                }

                                fullLandmark?.let {
                                    findViewById<TextView>(R.id.hintText).apply {
                                        text = "Распознано: ${translateLandmark(landmarkName)} (${"%.0f".format(confidencePercent)}%)"
                                        visibility = View.VISIBLE
                                    }
                                    navigateToLandmarkDetail(it)
                                } ?: run {
                                    Toast.makeText(
                                        this@PhotoActivity,
                                        "Достопримечательность '$landmarkName' не найдена в базе",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    hidePhotoPreview()
                                }
                            }
                        }
                    } catch (e: Exception) {
                            val errorMsg = "Ошибка обработки ответа: ${e.message ?: e.javaClass.simpleName}"
                            Log.e("ServerResponse", errorMsg, e)
                            Toast.makeText(
                                this@PhotoActivity,
                                errorMsg,
                                Toast.LENGTH_LONG
                            ).show()

                    }
                } else {
                    val errorMsg = when {
                        response == null -> "Нет ответа от сервера"
                        else -> "Ошибка сервера: ${response.code} - ${response.message}"
                    }
                    Toast.makeText(
                        this@PhotoActivity,
                        errorMsg,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            response?.isSuccessful == true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                if (!isCancelled) {
                    showLoading(false)
                    val errorMsg = when {
                        e.localizedMessage != null -> e.localizedMessage
                        e.message != null -> e.message
                        else -> "Неизвестная ошибка (${e.javaClass.simpleName})"
                    }
                    Toast.makeText(
                        this@PhotoActivity,
                        "Ошибка соединения: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            false
        } finally {
            if (!isCancelled) {
                currentUploadCall = null
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        } catch (e: Exception) {
            false
        }
    }

    private fun navigateToLandmarkDetail(landmark: Landmark) {
        val intent = Intent(this, LandmarkDetailActivity::class.java).apply {
            putExtra("landmark", landmark)
            putExtra("from_main", true)
        }
        startActivity(intent)
    }

    private fun translateLandmark(landmark: String): String {
        return when (landmark) {
            "monument_alexanderthird" -> "Памятник императору Александру III"
            "monument_to_the_first traffic_light" -> "Памятник первому светофору"
            "novat" -> "НОВАТ"
            "old_house" -> "Театр «Старый дом»"
            "theater_globus" -> "Театр «Глобус»"
            else -> landmark
        }
    }
}