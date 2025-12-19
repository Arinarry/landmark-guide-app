package com.example.diplom

import kotlin.collections.*


object ActivityHistoryManager {
    private val activityStack = mutableListOf<Class<*>>()

    fun addActivity(activityClass: Class<*>) {
        if (activityStack.isEmpty() || activityStack.last() != activityClass) {
            activityStack.add(activityClass)
        }
    }

    fun getPreviousActivity(): Class<*>? {
        if (activityStack.size < 2) return null
        return activityStack[activityStack.size - 2]
    }

    fun removeLastActivity() {
        if (activityStack.isNotEmpty()) {
            activityStack.removeAt(activityStack.size - 1)
        }
    }
}