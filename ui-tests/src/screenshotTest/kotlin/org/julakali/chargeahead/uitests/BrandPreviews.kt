package org.julakali.chargeahead.uitests

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.julakali.chargeahead.android.phone.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.DrawerHead

@PreviewTest
@Preview(showBackground = true, locale = "de")
@Composable
fun DrawerHeadBrand() {
    PreviewScaffold {
        Box(Modifier.padding(16.dp)) { DrawerHead() }
    }
}

@PreviewTest
@Preview(showBackground = true, backgroundColor = 0xFF121212, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "de")
@Composable
fun DrawerHeadBrandDark() = DrawerHeadBrand()

/**
 * The system splash is drawn before any app code runs, so nothing can screenshot the real one. This
 * lays the same drawables out the way Android 12+ does: 240dp icon slot in the centre (with icon
 * background colour), branding image at most 200×80dp at the bottom, on the launcher background
 * colour. Only the inner two thirds of the slot survive the circular mask, plain drawable or not,
 * which is how three corners of a triangle go missing.
 */
@PreviewTest
@Preview(widthDp = 360, heightDp = 780, locale = "de")
@Composable
fun SplashBrand() {
    Box(Modifier.fillMaxSize().background(Color(0xFF26215C))) {
        Box(Modifier.align(Alignment.Center).size(160.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.splash_icon), contentDescription = null, modifier = Modifier.requiredSize(240.dp))
        }
        Image(
            painterResource(R.drawable.splash_branding),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).sizeIn(maxWidth = 200.dp, maxHeight = 80.dp),
        )
    }
}

/**
 * The car's detail pane image, rendered from the stacked logo vector. The
 * host mangles that vector (nested scale, tinted white), so the app ships a
 * bitmap instead. The preview tool always paints a ground, so the logo is
 * rendered on black and on white and `tools/pane-logo.py` derives the
 * transparent PNG from the pair; run it whenever the logo changes.
 */
@PreviewTest
@Preview(widthDp = 192, heightDp = 192, locale = "de")
@Composable
fun CarPaneLogoOnBlack() = CarPaneLogoOn(Color.Black)

@PreviewTest
@Preview(widthDp = 192, heightDp = 192, locale = "de")
@Composable
fun CarPaneLogoOnWhite() = CarPaneLogoOn(Color.White)

@Composable
private fun CarPaneLogoOn(ground: Color) {
    Box(Modifier.fillMaxSize().background(ground).padding(8.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.logo_powertrip_stacked_dark), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}
