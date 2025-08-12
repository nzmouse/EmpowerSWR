package com.empowerswr.luksave.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity

fun Context.findEmpowerActivity(): ComponentActivity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is ComponentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}