package com.example.diplom

import android.Manifest
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.diplom.model.Landmark
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.VehicleType
import com.yandex.runtime.Error
import com.yandex.mapkit.geometry.geo.PolylineUtils
import com.yandex.runtime.image.ImageProvider
import java.util.concurrent.atomic.AtomicInteger

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var map: Map
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocationMarker: com.yandex.mapkit.map.PlacemarkMapObject? = null
    private lateinit var type_car: Button
    private lateinit var type_moto: Button
    private lateinit var type_footer: Button
    private var selectedVehicleType: VehicleType = VehicleType.DEFAULT
    private val routePolylines = mutableListOf<com.yandex.mapkit.map.PolylineMapObject>()
    private var pendingRouteRequests = AtomicInteger(0)
    private lateinit var locationCallback: LocationCallback
    private val locationUpdateInterval = 15000L // 15 секунд
    private val locationUpdateFastestInterval = 10000L // 10 секунд

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapKitFactory.initialize(this)
        setContentView(R.layout.activity_map)
        mapView = findViewById(R.id.mapview)
        map = mapView.mapWindow.map

        type_car = findViewById(R.id.type_car)
        type_moto = findViewById(R.id.type_moto)
        type_footer = findViewById(R.id.type_footer)

        setActiveButton(type_car)

        type_car.setOnClickListener {
            selectedVehicleType = VehicleType.DEFAULT
            setActiveButton(type_car)
            getUserLocationAndBuildRoute()
        }

        type_moto.setOnClickListener {
            selectedVehicleType = VehicleType.MOTO
            setActiveButton(type_moto)
            getUserLocationAndBuildRoute()
        }

        type_footer.setOnClickListener {
            selectedVehicleType = VehicleType.TRUCK
            setActiveButton(type_footer)
            getUserLocationAndBuildRoute()
        }

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getUserLocationAndPrecalculateRoutes()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        val landmark = intent.getParcelableExtra<Landmark>("landmark")

        val backButton = findViewById<Button>(R.id.back)
        backButton.setOnClickListener {
            val fromSearch = intent.getBooleanExtra("from_search", false)
            val fromMain = intent.getBooleanExtra("from_main", false)
            val fromComm = intent.getBooleanExtra("from_comment", false)
            if (landmark != null) {
                if (fromSearch) {
                    val intent = Intent(this, LandmarkDetailActivity::class.java).apply {
                        putExtra("landmark", landmark)
                        putExtra("from_search", true)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } else if (fromMain) {
                    val intent = Intent(this, LandmarkDetailActivity::class.java).apply {
                        putExtra("landmark", landmark)
                        putExtra("from_main", true)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } else if (fromComm) {
                    val intent = Intent(this, LandmarkDetailActivity::class.java).apply {
                        putExtra("landmark", landmark)
                        putExtra("from_comment", true)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }else {
                    val intent = Intent(this, LandmarkDetailActivity::class.java).apply {
                        putExtra("landmark", landmark)
                    }
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
            } else {
                Toast.makeText(this, "Информация не найдена", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setActiveButton(button: Button) {
        resetAllButtons()
        button.compoundDrawableTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.SkyBlue))
    }

    private fun resetAllButtons() {
        val defaultIconColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))

        type_car.compoundDrawableTintList = defaultIconColor
        type_moto.compoundDrawableTintList = defaultIconColor
        type_footer.compoundDrawableTintList = defaultIconColor
    }

    private fun getUserLocationAndPrecalculateRoutes() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = Point(location.latitude, location.longitude)
                val landmark = intent.getParcelableExtra<Landmark>("landmark")
                map.move(CameraPosition(userLocation, 16.5f, 0f, 0f))
                updateUserLocationMarker(userLocation)
                if (landmark != null) {
                    val coords = landmark.coordinates.split(",")
                    if (coords.size == 2) {
                        val lat = coords[0].trim().toDoubleOrNull()
                        val lon = coords[1].trim().toDoubleOrNull()
                        if (lat != null && lon != null) {
                            val destination = Point(lat, lon)
                            precalculateRoutesForAllTypes(userLocation, destination)
                            buildRoute(userLocation, destination, selectedVehicleType, true)
                        } else {
                            Toast.makeText(this, "Некорректные координаты", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Некорректный формат координат", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Достопримечательность не найдена", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Не удалось получить местоположение", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getUserLocationAndBuildRoute() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createLocationCallback()
        requestLocationUpdates()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = Point(location.latitude, location.longitude) // val userLocation = Point(55.002239600775155, 82.94555255230006)
                val landmark = intent.getParcelableExtra<Landmark>("landmark")
                if (landmark != null) {
                    val coords = landmark.coordinates.split(",")
                    if (coords.size == 2) {
                        val lat = coords[0].trim().toDoubleOrNull()
                        val lon = coords[1].trim().toDoubleOrNull()
                        if (lat != null && lon != null) {
                            val destination = Point(lat, lon)
                            buildRoute(userLocation, destination, selectedVehicleType, true)
                        }
                    }
                }
            }
        }
    }

    private fun precalculateRoutesForAllTypes(startPoint: Point, endPoint: Point) {
        pendingRouteRequests.set(3)

        buildRoute(startPoint, endPoint, VehicleType.DEFAULT, false)
        buildRoute(startPoint, endPoint, VehicleType.MOTO, false)
        buildRoute(startPoint, endPoint, VehicleType.TRUCK, false)
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    val userLocation = Point(location.latitude, location.longitude)
                    updateUserLocationMarker(userLocation)

                    val landmark = intent.getParcelableExtra<Landmark>("landmark")
                    landmark?.let {
                        val coords = it.coordinates.split(",")
                        if (coords.size == 2) {
                            val lat = coords[0].trim().toDoubleOrNull()
                            val lon = coords[1].trim().toDoubleOrNull()
                            if (lat != null && lon != null) {
                                val destination = Point(lat, lon)
                                updateTravelTimesForAllTypes(userLocation, destination)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateTravelTimesForAllTypes(startPoint: Point, endPoint: Point) {
        pendingRouteRequests.set(3)

        buildRoute(startPoint, endPoint, VehicleType.DEFAULT, false)
        buildRoute(startPoint, endPoint, VehicleType.MOTO, false)
        buildRoute(startPoint, endPoint, VehicleType.TRUCK, false)
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = locationUpdateInterval
            fastestInterval = locationUpdateFastestInterval
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun updateUserLocationMarker(userLocation: Point) {
        if (userLocationMarker == null) {
            userLocationMarker = map.mapObjects.addPlacemark().apply {
                geometry = userLocation
                setIcon(ImageProvider.fromResource(this@MapActivity, R.drawable.chel))
                isDraggable = false
            }
        } else {
            // Плавное перемещение маркера
            val animation = TypeEvaluator { fraction: Float, from: Point, to: Point ->
                Point(
                    from.latitude + (to.latitude - from.latitude) * fraction,
                    from.longitude + (to.longitude - from.longitude) * fraction
                )
            }

            userLocationMarker?.let { marker ->
                ValueAnimator.ofObject(
                    animation,
                    marker.geometry,
                    userLocation
                ).apply {
                    duration = 1000 // 1 секунда анимации
                    addUpdateListener { animator ->
                        marker.geometry = animator.animatedValue as Point
                    }
                    start()
                }
            }
        }
    }

    private fun buildRoute(startPoint: Point, endPoint: Point, vehicleType: VehicleType, displayOnMap: Boolean) {
        val drivingRouter = DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
        val drivingOptions = DrivingOptions()
        val vehicleOptions = VehicleOptions().apply {
            this.vehicleType = vehicleType
            if (vehicleType == VehicleType.TRUCK) {
                weight = 10000.0f
                height = 4.0f
                width = 2.5f
                length = 12.0f
                hasTrailer = true
            }
        }

        val requestPoints = listOf(
            RequestPoint(startPoint, RequestPointType.WAYPOINT, null, null, null),
            RequestPoint(endPoint, RequestPointType.WAYPOINT, null, null, null)
        )

        val routeListener = object : DrivingSession.DrivingRouteListener {
            override fun onDrivingRoutes(drivingRoutes: MutableList<DrivingRoute>) {
                if (drivingRoutes.isNotEmpty()) {
                    if (displayOnMap) {
                        routePolylines.forEach { it.isVisible = false }
                        routePolylines.clear()
                        displayRouteOnMap(drivingRoutes[0])
                    }
                    updateButtonWithTravelTime(drivingRoutes[0], vehicleType)
                } else {
                    if (displayOnMap) {
                        Toast.makeText(this@MapActivity, "Маршрут не найден", Toast.LENGTH_SHORT).show()
                    }
                }

                if (pendingRouteRequests.decrementAndGet() == 0) { }
            }

            override fun onDrivingRoutesError(error: Error) {
                if (displayOnMap) {
                    Toast.makeText(this@MapActivity, "Ошибка при получении маршрута", Toast.LENGTH_SHORT).show()
                }

                if (pendingRouteRequests.decrementAndGet() == 0) { }
            }
        }

        if (displayOnMap) {
            map.mapObjects.addPlacemark().apply {
                geometry = endPoint
                setIcon(ImageProvider.fromResource(this@MapActivity, R.drawable.gps))
                isDraggable = false
            }
        }

        drivingRouter.requestRoutes(requestPoints, drivingOptions, vehicleOptions, routeListener)
    }

    private fun displayRouteOnMap(route: DrivingRoute) {
        val polyline = route.geometry
        val routePolyline = map.mapObjects.addPolyline(polyline)
        routePolyline.style.outlineColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        routePolyline.style.strokeWidth = 5f

        routePolylines.add(routePolyline)
    }

    private fun updateButtonWithTravelTime(route: DrivingRoute, vehicleType: VehicleType) {
        val travelTime = timeTravelToPoint(route, route.geometry.points.last())
        val minutes = (travelTime / 60).toInt()

        runOnUiThread {
            when (vehicleType) {
                VehicleType.DEFAULT -> type_car.text = "${-minutes} мин"
                VehicleType.MOTO -> type_moto.text = "${-minutes} мин"
                VehicleType.TRUCK -> type_footer.text = "${-minutes} мин"
                else -> {}
            }
        }
    }

    private fun timeTravelToPoint(route: DrivingRoute, point: Point): Double {
        val currentPosition = route.routePosition
        val distance = distanceBetweenPointsOnRoute(route, currentPosition.point, point)
        val targetPosition = currentPosition.advance(distance)
        return targetPosition.timeToFinish() - currentPosition.timeToFinish()
    }

    private fun distanceBetweenPointsOnRoute(route: DrivingRoute, first: Point, second: Point): Double {
        val polylineIndex = PolylineUtils.createPolylineIndex(route.geometry)
        val firstPosition = polylineIndex.closestPolylinePosition(first, com.yandex.mapkit.geometry.geo.PolylineIndex.Priority.CLOSEST_TO_RAW_POINT, 1.0)!!
        val secondPosition = polylineIndex.closestPolylinePosition(second, com.yandex.mapkit.geometry.geo.PolylineIndex.Priority.CLOSEST_TO_RAW_POINT, 1.0)!!
        return PolylineUtils.distanceBetweenPolylinePositions(route.geometry, firstPosition, secondPosition)
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
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
            requestLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}