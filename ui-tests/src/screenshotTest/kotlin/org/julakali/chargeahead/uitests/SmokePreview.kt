package org.julakali.chargeahead.uitests

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.HintChip

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SmokeHintChip() {
    HintChip("Hallo")
}
