package com.example.diplom

import android.content.res.ColorStateList
import android.widget.Button

class BottomNavManager(private val buttons: List<Button>) {

    private var currentSelected: Button? = null

    fun setSelected(selectedButton: Button) {
        currentSelected?.apply {
            val drawables = compoundDrawables
            drawables[1]?.mutate()?.setTint(
                context.getColor(R.color.white))
        }

        val drawables = selectedButton.compoundDrawables
        drawables[1]?.mutate()?.setTint(
            selectedButton.context.getColor(R.color.SkyBlue))

        currentSelected = selectedButton
    }
}