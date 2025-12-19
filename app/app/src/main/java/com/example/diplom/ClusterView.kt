package com.example.diplom

import android.content.Context
import android.graphics.Color
import androidx.appcompat.widget.AppCompatTextView

class ClusterView(context: Context) : AppCompatTextView(context) {
    init {
        textSize = 16f
        setTextColor(Color.WHITE)
        setBackgroundResource(R.drawable.cluster_background)
        setPadding(20, 10, 20, 10)
        gravity = android.view.Gravity.CENTER
        elevation = 8f
    }

    fun setText(count: String) {
        text = if (count.toInt() > 99) "99+" else count
    }
}