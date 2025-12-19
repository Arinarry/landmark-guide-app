package com.example.diplom

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FavoritesManager.init(this)
        MapKitFactory.setApiKey("7218d53b-8e13-44ce-9501-cc09c763c5dc")
    }
}
