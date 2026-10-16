package com.mikimouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mikimouse.app.ui.OperatorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OperatorScreen(operatorRoot = filesDir.resolve("operator")) }
    }
}
