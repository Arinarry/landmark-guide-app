package com.example.diplom

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.diplom.model.Landmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity: AppCompatActivity()  {
    private lateinit var adapter: LandmarkAdapter
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var navManager: BottomNavManager
    private lateinit var filterButton: ImageButton
    private var allLandmarks = listOf<Landmark>()
    private val selectedFilters = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.landmarks_recycler)
        filterButton = findViewById(R.id.filter_button)

        ActivityHistoryManager.addActivity(this::class.java)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = LandmarkAdapter(allLandmarks) { landmark ->
            showLandmarkDetails(landmark)
        }
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            try {
                val landmarks = withContext(Dispatchers.IO) {
                    LocationService().getLandmarks()
                }
                allLandmarks = landmarks.sortedBy { it.name }

                adapter.updateData(allLandmarks)
            } catch (e: Exception) {
                Toast.makeText(this@SearchActivity, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                filterLandmarks(newText.orEmpty())
                return true
            }
        })

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

        filterButton.setOnClickListener {
            val popup = androidx.appcompat.widget.PopupMenu(this, filterButton, Gravity.NO_GRAVITY, 0, R.style.PopupMenuWhiteBackground)
            popup.menuInflater.inflate(R.menu.filter_dropdown, popup.menu)

            val tagTitles = (0 until popup.menu.size())
                .map { popup.menu.getItem(it) }
                .filter { it.itemId != R.id.filter_all }
                .map { it.title.toString() }

            for (i in 0 until popup.menu.size()) {
                val menuItem = popup.menu.getItem(i)
                val title = menuItem.title.toString()
                menuItem.isChecked = when (menuItem.itemId) {
                    R.id.filter_all -> selectedFilters.containsAll(tagTitles) && tagTitles.size == selectedFilters.size
                    else -> selectedFilters.contains(title)
                }
            }

            popup.setOnMenuItemClickListener { item ->
                val allItem = popup.menu.findItem(R.id.filter_all)
                when (item.itemId) {
                    R.id.filter_all -> {
                        val allFiltersSelected = (0 until popup.menu.size()).all { i ->
                            val menuItem = popup.menu.getItem(i)
                            menuItem.itemId == R.id.filter_all || menuItem.isChecked
                        }
                        if (allFiltersSelected) {
                            selectedFilters.clear()
                            for (i in 0 until popup.menu.size()) {
                                popup.menu.getItem(i).isChecked = false
                            }
                        } else {
                            selectedFilters.clear()
                            for (i in 0 until popup.menu.size()) {
                                val menuItem = popup.menu.getItem(i)
                                menuItem.isChecked = menuItem.itemId != R.id.filter_all
                                if (menuItem.isChecked) {
                                    selectedFilters.add(menuItem.title.toString())
                                }
                            }
                            allItem.isChecked = true
                        }
                    }

                    else -> {
                        item.isChecked = !item.isChecked
                        toggleFilter(item.title.toString(), item.isChecked)
                        if (allItem.isChecked) {
                            allItem.isChecked = false
                        }

                        val anyChecked = (0 until popup.menu.size()).any { i ->
                            val it = popup.menu.getItem(i)
                            it.itemId != R.id.filter_all && it.isChecked
                        }

                        if (!anyChecked) {
                            selectedFilters.clear()
                        }
                    }
                }
                applyFilters()
                true
            }
            popup.show()
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
        navManager.setSelected(findViewById(R.id.search))
    }

    private fun applyFilters() {
        val currentQuery = searchView.query?.toString() ?: ""
        filterLandmarks(currentQuery)
    }

    private fun toggleFilter(type: String, enabled: Boolean) {
        if (enabled) selectedFilters.add(type) else selectedFilters.remove(type)
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

    private fun filterLandmarks(query: String) {
        val filteredByTags = if (selectedFilters.isEmpty()) {
            allLandmarks
        } else {
            allLandmarks.filter { landmark ->
                selectedFilters.contains(landmark.tag)
            }
        }

        val filtered = if (query.isEmpty()) {
            filteredByTags
        } else {
            filteredByTags.filter {
                it.name.contains(query, ignoreCase = true) //|| it.description.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filtered)
    }

    private fun showLandmarkDetails(landmark: Landmark) {
        val intent = Intent(this, LandmarkDetailActivity::class.java)
        intent.putExtra("landmark", landmark)
        intent.putExtra("from_search", true)
        startActivity(intent)
    }
}