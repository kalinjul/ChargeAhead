package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.julakali.chargeahead.android.phone.components.SectionLabel
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.about_legal_imprint
import org.julakali.chargeahead.shared.resources.about_legal_imprint_body
import org.julakali.chargeahead.shared.resources.about_legal_privacy

/** Imprint (§ 5 DDG) and privacy policy. The privacy policy follows. */
@Composable
fun LegalScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionLabel(stringResource(Res.string.about_legal_imprint))
        SelectionContainer {
            Text(stringResource(Res.string.about_legal_imprint_body), style = MaterialTheme.typography.bodyMedium)
        }
        SectionLabel(stringResource(Res.string.about_legal_privacy), modifier = Modifier.padding(top = 24.dp))
    }
}
