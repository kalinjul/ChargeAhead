package de.autoapp.android.phone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.autoapp.android.phone.twoDecimals
import de.autoapp.android.phone.theme.ChargeAheadColors
import de.autoapp.android.phone.theme.tabular

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp, bottom = 8.dp),
    )
}

@Composable
fun Fineprint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = ChargeAheadColors.faint, modifier = modifier)
}

@Composable
fun NetworkDot(color: Color, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

@Composable
fun RankBadge(number: Int, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Box(modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            number.toString(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

@Composable
fun PriceText(euroPerKwh: Double, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("${euroPerKwh.twoDecimals()} €")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) { append("/kWh") }
        },
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.titleSmall.tabular.copy(fontWeight = FontWeight.ExtraBold),
        modifier = modifier,
    )
}
