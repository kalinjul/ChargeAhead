package org.julakali.chargeahead.android.phone

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.theme.ChargeAheadTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Bars follow the system theme, like the rest of the UI.
        enableEdgeToEdge()
        setContent {
            ChargeAheadTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneApp(librariesRes = R.raw.aboutlibraries)
                }
            }
        }
    }
}
