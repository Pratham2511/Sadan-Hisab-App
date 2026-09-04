package com.pansare.sadan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.SadanApp
import com.pansare.sadan.ui.theme.SadanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SadanTheme {
                SadanApp(viewModel())
            }
        }
    }
}
