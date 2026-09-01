package com.pansare.sadan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pansare.sadan.ui.PansareApp
import com.pansare.sadan.ui.theme.PansareTheme
import com.pansare.sadan.ui.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PansareTheme { PansareApp(viewModel()) } }
    }
}
