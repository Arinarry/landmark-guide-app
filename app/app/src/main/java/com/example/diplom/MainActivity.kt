package com.example.diplom

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.diplom.model.Landmark
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.ClusterListener
import com.yandex.mapkit.map.ClusterizedPlacemarkCollection
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import com.yandex.runtime.ui_view.ViewProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class   MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var cityText: TextView
    private lateinit var logoText: TextView
    private lateinit var navManager: BottomNavManager
    private lateinit var mapView: MapView
    private lateinit var map: Map
    private var allLandmarks = listOf<Landmark>()
    private var activePlacemark: PlacemarkMapObject? = null
    private lateinit var clusterizedCollection: ClusterizedPlacemarkCollection
    private var isMapExpanded = false
    private lateinit var openMapButton: ImageButton
    private lateinit var closeButton: ImageButton

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                startLocationUpdates()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                startLocationUpdates()
            }
            else -> {
                Toast.makeText(this, "Разрешение на местоположение не предоставлено", Toast.LENGTH_SHORT).show()
                cityText.text = "Новосибирск"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cityText = findViewById(R.id.city)
        logoText = findViewById(R.id.logo)
        MapKitFactory.initialize(this)
        ActivityHistoryManager.addActivity(this::class.java)

        mapView = findViewById(R.id.mapview)

        val photosViewPager = findViewById<ViewPager2>(R.id.photosViewPager)
        val photos = listOf(
            R.drawable.monument,
            R.drawable.novat,
            R.drawable.teatr_globus)

        photosViewPager.adapter = PhotoAdapter(photos)
        val handler = Handler(Looper.getMainLooper())
        val autoScrollRunnable = object : Runnable {
            override fun run() {
                val nextItem = if (photosViewPager.currentItem == photos.size - 1) {
                    0
                } else {
                    photosViewPager.currentItem + 1
                }
                photosViewPager.setCurrentItem(nextItem, true)
                handler.postDelayed(this, 5000)
            }
        }

        handler.postDelayed(autoScrollRunnable, 5000)

        photosViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                handler.removeCallbacks(autoScrollRunnable)
                handler.postDelayed(autoScrollRunnable, 5000)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    handler.removeCallbacks(autoScrollRunnable)
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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

        navManager.setSelected(findViewById(R.id.home))

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        map = mapView.mapWindow.map
        val zoomInButton = findViewById<ImageButton>(R.id.zoom_in_button)
        val zoomOutButton = findViewById<ImageButton>(R.id.zoom_out_button)

        zoomInButton.setOnClickListener {
            val currentZoom = map.cameraPosition.zoom
            if (currentZoom < 19) {
                val newZoom = currentZoom + 1
                val cameraPosition = CameraPosition(map.cameraPosition.target, newZoom, 0f, 0f)
                map.move(cameraPosition)
            }
        }

        zoomOutButton.setOnClickListener {
            val currentZoom = map.cameraPosition.zoom
            if (currentZoom > 1) {
                val newZoom = currentZoom - 1
                val cameraPosition = CameraPosition(map.cameraPosition.target, newZoom, 0f, 0f)
                map.move(cameraPosition)
            }
        }

        openMapButton = findViewById(R.id.open_map)
        openMapButton.setOnClickListener {
            mapView.post {
                toggleFullscreenMap()
            }

        }

        closeButton = findViewById(R.id.btn_close)
        closeButton.setOnClickListener {
            hidePlaceInfo()
            activePlacemark?.let {
                it.setIcon(ImageProvider.fromResource(this, R.drawable.label))
                it.setText("")
                activePlacemark = null
            }
        }

        lifecycleScope.launch {
            try {
                val landmarks = withContext(Dispatchers.IO) {
                    LocationService().getLandmarks()
                }
                allLandmarks = landmarks.sortedBy { it.name }
                showLandmarkMarkers()

            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка при загрузке достопримечательностей", e)
                Toast.makeText(this@MainActivity, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFullscreenMap() {
        val photosViewPager = findViewById<ViewPager2>(R.id.photosViewPager)
        val textView = findViewById<TextView>(R.id.textView)
        val mapContainer = findViewById<FrameLayout>(R.id.map_container)

        if (isMapExpanded) {
            mapContainer.layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            photosViewPager.visibility = View.VISIBLE
            textView.visibility = View.VISIBLE
            openMapButton.setImageResource(R.drawable.size)

        } else {
            mapContainer.layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            photosViewPager.visibility = View.GONE
            textView.visibility = View.GONE
            openMapButton.setImageResource(R.drawable.size_shrink)

        }

        mapContainer.requestLayout()
        isMapExpanded = !isMapExpanded
    }


    private val clusterListener = ClusterListener { cluster ->
        cluster.appearance.setView(
            ViewProvider(
                ClusterView(this).apply {
                    setText("${cluster.placemarks.size}")
                }
            )
        )
    }

    private val mapObjectTapListener = object : MapObjectTapListener {
        override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
            if (mapObject is PlacemarkMapObject) {
                activePlacemark?.let {
                    it.setIcon(ImageProvider.fromResource(this@MainActivity, R.drawable.label))
                    it.setText("")
                }

                val landmark = mapObject.userData as? Landmark
                val name = landmark?.name ?: "Unknown place"
                mapObject.setIcon(ImageProvider.fromResource(this@MainActivity, R.drawable.label_blue))

                mapObject.setText(
                    name,
                    TextStyle().apply {
                        size = 10f
                        placement = TextStyle.Placement.RIGHT
                        offset = 5f
                    }
                )
                activePlacemark = mapObject
                showPlaceInfo(name, point)
            }

            val cameraPosition = CameraPosition(point, 16.5f, 0f, 0f)
            map.move(cameraPosition)

            return true
        }
    }

    private fun showLandmarkMarkers() {
        val location = Point(55.03058, 82.92446)
        map.move(CameraPosition(location, 14f, 0f, 0f))

        if (::clusterizedCollection.isInitialized) {
            map.mapObjects.remove(clusterizedCollection)
        }

        clusterizedCollection = map.mapObjects.addClusterizedPlacemarkCollection(clusterListener)

        for (landmark in allLandmarks) {
            val coords = landmark.coordinates.split(",")
            if (coords.size == 2) {
                val lat = coords[0].trim().toDoubleOrNull()
                val lon = coords[1].trim().toDoubleOrNull()
                if (lat != null && lon != null) {
                    val point = Point(lat, lon)
                    val placemark = clusterizedCollection.addPlacemark(point)
                    placemark.setIcon(ImageProvider.fromResource(this, R.drawable.label))
                    placemark.userData = landmark
                    placemark.addTapListener(mapObjectTapListener)
                }
            }
        }
        clusterizedCollection.clusterPlacemarks(60.0, 15)
    }

    private fun showPlaceInfo(placeName: String, point: Point) {
        val bottomSheet = findViewById<LinearLayout>(R.id.bottom_sheet)
        val placeTitle = findViewById<TextView>(R.id.place_title)
        val placeTag = findViewById<TextView>(R.id.place_tag)
        val placeStreet = findViewById<TextView>(R.id.place_street)
        val zoomControls = findViewById<LinearLayout>(R.id.zoom_controls)

        val landmark = allLandmarks.firstOrNull { it.name == placeName }

        placeTitle.text = placeName
        placeTag.text = landmark?.tag ?: "Тег отсутствует"
        placeStreet.text = landmark?.location ?: "Адрес отсутствует"

        findViewById<Button>(R.id.btn_details).setOnClickListener {
            showPlaceDetails(landmark)
        }

        if (bottomSheet.visibility != View.VISIBLE) {
            bottomSheet.visibility = View.VISIBLE
            bottomSheet.translationY = bottomSheet.height.toFloat()

            bottomSheet.animate()
                .translationY(0f)
                .setDuration(300)
                .start()

            zoomControls.post {
                zoomControls.animate()
                    .translationY(-bottomSheet.height.toFloat())
                    .setDuration(300)
                    .start()
            }
        }
    }


    private fun hidePlaceInfo() {
        val bottomSheet = findViewById<LinearLayout>(R.id.bottom_sheet)
        val zoomControls = findViewById<LinearLayout>(R.id.zoom_controls)
        bottomSheet.animate()
            .translationY(bottomSheet.height.toFloat())
            .setDuration(200)
            .withEndAction { bottomSheet.visibility = View.GONE }
            .start()
        zoomControls.animate()
            .translationY(0f)
            .setDuration(200)
            .start()
    }

    private fun showPlaceDetails(landmark: Landmark?) {
        val intent = Intent(this, LandmarkDetailActivity::class.java).apply {
            putExtra("landmark", landmark)
            putExtra("from_main", true)
        }
        startActivity(intent)
    }

    private fun handleNavigation(buttonId: Int) {
        when (buttonId) {
            R.id.home -> {
                val intent = Intent(this,MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }
            R.id.search -> {
                val intent = Intent(this, SearchActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)}
            R.id.photo -> {
                val intent = Intent(this, PhotoActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)}
            R.id.like -> {
                val intent = Intent(this, FavoriteActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)}
            R.id.user-> {
                val intent = Intent(this, UserActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)}
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            else -> {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).apply {
            setMinUpdateDistanceMeters(10f)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    getAddressFromLocation(location)
                }
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun getAddressFromLocation(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )

            addresses?.let {
                if (it.isNotEmpty()) {
                    val address = it[0]
                } else {
                    Toast.makeText(this, "Адрес не найден", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Ошибка геокодирования: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Недопустимые координаты", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        if (isMapExpanded ) {
            mapView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onStop() {
        if (isMapExpanded ) {
            mapView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (::locationCallback.isInitialized) {
            checkLocationPermission()
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}