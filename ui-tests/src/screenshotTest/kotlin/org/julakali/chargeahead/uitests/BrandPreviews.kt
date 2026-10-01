package org.julakali.chargeahead.uitests

import android.content.res.Configuration
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

