# UI Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle every phone screen of the ChargeAhead Android app to the design language of `docs/mockup/index.html`, on native Material 3 machinery.

**Architecture:** Design-system-first: one theme file translating the mockup's CSS variables into an M3 color scheme/type scale/shapes, one component package translating its recurring CSS classes into composables, then a mostly-mechanical restyle of each screen on top. One additive shared-KMP change (`ChargeNowResult.more`). Navigation stays MainActivity's enum-page pattern.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 (BOM 2026.06.01), KMP shared module, kotlin.test (jvmTest).

**Spec:** `docs/superpowers/specs/2026-09-07-ui-overhaul-design.md`

## Global Constraints

- **All user-facing strings are German**, live in `androidApp/src/main/res/values/strings.xml`, and follow its terse conversational tone. Never hardcode UI text in Kotlin.
- **No mockup fiction**: no live-availability UI, no occupancy counts, no amenity chips, no "choose a different charger", no From/To swap.
- **Light-only theme.** Font stays `FontFamily.SansSerif`. No new dependencies.
- **Prices/times/distances** always render with the tabular-numerals style (`.tabular` from Task 2) and the existing `Double.twoDecimals()` / `Double.oneDecimal()` German formatters (defined in `SubscriptionsScreen.kt` / `GarageScreen.kt`, `internal` so visible module-wide).
- Compile check: `./gradlew :androidApp:compileDebugKotlin`. Shared tests: `./gradlew :shared:jvmTest`.
- **Screenshot verification** uses the `app-laufen-lassen` skill (emulator install + screenshot); compare against the matching mockup screen in `docs/mockup/index.html` opened in a browser.
- Import lists in code blocks are elided; add the obvious `androidx.compose.*` imports as the compiler demands.
- Commit messages: `type(LD-0000): lowercase subject ≤70 chars`, no co-author trailers, never push.
- The Android Auto (`car/`) package is untouched throughout.

---

### Task 1: Shared — `ChargeNowResult.more`

The expanded charge-now sheet ("Mehr in der Nähe") needs the candidates that didn't make top-3. Today the ranker throws them away.

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/core/ChargeNowRanker.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/core/ChargeNowRankerTest.kt`

**Interfaces:**
- Produces: `ChargeNowResult(candidates: List<ChargeNowCandidate>, relaxed: List<RelaxedFilter>, more: List<ChargeNowCandidate> = emptyList())` — `more` = every DC-qualified candidate not in `candidates`, sorted by `distanceKm` ascending. Task 8 consumes it.

- [ ] **Step 1: Write the failing test**

Add to `ChargeNowRankerTest` (helpers `site(...)` and `rank(...)` already exist in the file):

```kotlin
@Test
fun `the rest of the pool comes along, nearest first`() {
    val result = rank(
        listOf(
            site("ionity", "Ionity", 350.0, 3.0),         // ad-hoc 0.79 — priciest, misses top 3
            site("tesla", "Tesla", 250.0, 3.0),           // 0.55
            site("vattenfall", "Vattenfall", 150.0, 2.0), // 0.59
            site("fastned", "Fastned", 300.0, 1.0),       // 0.69
            site("wallbox", "Stadtwerke", 22.0, 0.2),     // AC — never appears anywhere
        ),
    )
    assertEquals(listOf("demo:ionity"), result.more.map { it.site.id })
}

@Test
fun `more is sorted by distance, not by price`() {
    val result = rank(
        listOf(
            site("a", "Tesla", 250.0, 1.0),      // 0.55 — top 3
            site("b", "Vattenfall", 150.0, 1.0), // 0.59 — top 3
            site("c", "Fastned", 300.0, 1.0),    // 0.69 — top 3
            site("far-cheap", "Tesla", 250.0, 9.0),
            site("near-pricey", "Ionity", 350.0, 4.0),
        ),
    )
    assertEquals(listOf("demo:near-pricey", "demo:far-cheap"), result.more.map { it.site.id })
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "de.autoapp.shared.core.ChargeNowRankerTest"`
Expected: FAIL — `more` unresolved.

- [ ] **Step 3: Implement**

In `ChargeNowRanker.kt`:

```kotlin
data class ChargeNowResult(
    val candidates: List<ChargeNowCandidate>,
    val relaxed: List<RelaxedFilter>,
    /** Everything DC-qualified that missed the top spots, nearest first — the expanded sheet scrolls on. */
    val more: List<ChargeNowCandidate> = emptyList(),
)
```

In `rank(...)`, the early-return inside the relax loop becomes:

```kotlin
if (hits.size >= MIN_RESULTS || relaxCount == ladder.size) {
    val chosen = hits.map { it.site.id }.toSet()
    return ChargeNowResult(
        candidates = hits,
        relaxed = ladder.take(relaxCount).map { it.first },
        more = candidates.filter { it.site.id !in chosen }.sortedBy { it.distanceKm },
    )
}
```

- [ ] **Step 4: Run the full shared test suite**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (existing tests unaffected — `more` has a default).

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "feat(LD-0000): charge-now ranker stops discarding the rest of the pool"
```

---

### Task 2: Theme — `ChargeAheadTheme`

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/theme/Theme.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (delete `LightMapsScheme` + `StandardTypography` at lines 113–144, use `ChargeAheadTheme` in `onCreate`)

**Interfaces:**
- Produces: `@Composable fun ChargeAheadTheme(content: @Composable () -> Unit)`; `object ChargeAheadColors { val faint: Color; val trafficBg: Color; val trafficText: Color }`; `val TextStyle.tabular: TextStyle` — package `de.autoapp.android.phone.theme`. Every later task consumes these.

- [ ] **Step 1: Write `theme/Theme.kt`**

```kotlin
package de.autoapp.android.phone.theme

/** Colors from the mockup that have no honest slot in the M3 scheme. */
object ChargeAheadColors {
    val faint = Color(0xFF80868B)
    // Reserved for the trip summary's delay badge once traffic data exists.
    val trafficBg = Color(0xFFFEF7E0)
    val trafficText = Color(0xFFB06000)
}

/** The mockup's `.num`: prices, times and distances that don't wobble. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

// docs/mockup/index.html :root — the palette, translated slot by slot.
private val MockupScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF174EA6),
    tertiary = Color(0xFF188038),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6F4EA),
    onTertiaryContainer = Color(0xFF188038),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFD93025),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFDADCE0),
    // M3 components pick container tones on their own (sheets, drawers,
    // dialogs, cards) — the mockup knows only white surfaces.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF1F3F4),
)

private val Sans = FontFamily.SansSerif

// The mockup's voice: bold and tight for titles, small and quiet for meta.
private val MockupTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Sans),
        displayMedium = displayMedium.copy(fontFamily = Sans),
        displaySmall = displaySmall.copy(fontFamily = Sans),
        headlineLarge = headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Sans, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        titleMedium = titleMedium.copy(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
        titleSmall = titleSmall.copy(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontFamily = Sans, fontSize = 13.sp),
        bodySmall = bodySmall.copy(fontFamily = Sans, fontSize = 12.sp),
        labelLarge = labelLarge.copy(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
}

private val MockupShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),   // gobtn, search field, segments
    medium = RoundedCornerShape(14.dp),  // --radius: cards, CTAs
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(26.dp), // sheet top corners
)

@Composable
fun ChargeAheadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MockupScheme,
        typography = MockupTypography,
        shapes = MockupShapes,
        content = content,
    )
}
```

- [ ] **Step 2: Use it in `MainActivity.onCreate`**

Replace `MaterialTheme(colorScheme = LightMapsScheme, typography = StandardTypography)` with `ChargeAheadTheme` (import `de.autoapp.android.phone.theme.ChargeAheadTheme`) and delete the now-unused `LightMapsScheme` and `StandardTypography` declarations plus their imports. Keep the `Surface(modifier = Modifier.fillMaxSize())` wrapper.

- [ ] **Step 3: Compile + eyeball**

Run: `./gradlew :androidApp:compileDebugKotlin` — expected BUILD SUCCESSFUL.
Then launch via `app-laufen-lassen`, screenshot the home screen: background `#F5F7FA`, bolder titles. Nothing should be broken, just re-toned.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): mockup palette and type scale become the one true theme"
```

---

### Task 3: Component library + icon set

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/components/Chrome.kt`
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/components/Cards.kt`
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/components/Rows.kt`
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/components/Inputs.kt`
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/components/AppSheet.kt`
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/components/AppTopBar.kt`
- Create drawables in `androidApp/src/main/res/drawable/`: `ic_check.xml`, `ic_add.xml`, `ic_search.xml`, `ic_filter.xml`, `ic_send.xml`, `ic_car.xml`, `ic_cardpay.xml`, `ic_bolt.xml`, `ic_pen.xml`

**Interfaces:**
- Consumes: `ChargeAheadColors`, `TextStyle.tabular` (Task 2); `Double.twoDecimals()` (exists).
- Produces (package `de.autoapp.android.phone.components`, all `@Composable` unless noted):
  - `SectionLabel(text: String, modifier: Modifier = Modifier)`
  - `Fineprint(text: String, modifier: Modifier = Modifier)`
  - `NetworkDot(color: Color, modifier: Modifier = Modifier, size: Dp = 9.dp)`
  - `RankBadge(number: Int, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White)`
  - `PriceText(euroPerKwh: Double, modifier: Modifier = Modifier)`
  - `AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`
  - `KeyValueGrid(entries: List<Pair<String, String>>, modifier: Modifier = Modifier)`
  - `TickRow(label: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, sublabel: String? = null, dotColor: Color? = null, tick: TickStyle = TickStyle.CHECK)` + `enum class TickStyle { CHECK, ADD, DELETE }`
  - `PrefRow(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, sublabel: String? = null)`
  - `GoButton(icon: Painter, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, containerColor: Color = <primaryContainer>, tint: Color = <primary>)`
  - `SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier)`
  - `AppChip(text: String, modifier: Modifier = Modifier, icon: Painter? = null)`
  - `AppSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
  - `AppTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {})`
  - `TopBarIcon(icon: Painter, contentDescription: String?, onClick: () -> Unit, badge: Boolean = false)`

- [ ] **Step 1: Icon drawables**

Vector XMLs, 24×24 viewport, `android:tint`-able via `?attr/colorControlNormal`-free plain paths. Stroke icons use `strokeColor="#FF000000"` + `fillColor="#00000000"`; Compose `Icon(tint=…)` recolors them. Path data comes straight from the mockup's `<symbol>`s:

`ic_check.xml`: stroke path `M20,6 L9,17 l-5,-5`, strokeWidth 3, strokeLineCap round, strokeLineJoin round.
`ic_add.xml`: stroke `M12,5 v14 M5,12 h14`, width 2.4, cap round.
`ic_search.xml`: stroke circle as path `M11,4 a7,7 0 1,0 0,14 a7,7 0 1,0 0,-14` width 2 + `M21,21 l-4.35,-4.35` width 2 cap round.
`ic_filter.xml`: stroke `M4,7 h16 M7,12 h10 M10,17 h4`, width 2.2, cap round.
`ic_send.xml`: stroke `M7,17 L17,7 M10,7 h7 v7`, width 2.3, cap+join round.
`ic_car.xml`: stroke `M5,12 L6.5,7 h11 L19,12 M5,12 h14 v5 h-2.2 M5,17 V12 m0,5 h2.2 m9.6,0 H7.2` width 2 cap+join round, plus two filled circles `M8,17 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0` and same at cx 16.8.
`ic_cardpay.xml`: stroke rounded rect `M5.5,6 h13 a2.5,2.5 0 0 1 2.5,2.5 v8 a2.5,2.5 0 0 1 -2.5,2.5 h-13 a2.5,2.5 0 0 1 -2.5,-2.5 v-8 a2.5,2.5 0 0 1 2.5,-2.5` width 2 + `M3,10.5 h18` width 2.
`ic_bolt.xml`: filled `M13,2 L4,14 h6 l-1,8 9,-12 h-6 l1,-8z`.
`ic_pen.xml`: filled `M3,17.2 V21 h3.8 L17.8,10 14,6.2 3,17.2z M20.7,7.04 a1,1 0 0 0 0,-1.41 L18.37,3.3 a1,1 0 0 0 -1.41,0 l-1.83,1.83 3.75,3.75 1.82,-1.84z`.

Example file shape (`ic_check.xml`; the others follow the same skeleton):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M20,6 L9,17 l-5,-5"
        android:strokeColor="#FF000000"
        android:strokeWidth="3"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000" />
</vector>
```

- [ ] **Step 2: `components/Chrome.kt`**

```kotlin
package de.autoapp.android.phone.components

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
```

- [ ] **Step 3: `components/Cards.kt`**

```kotlin
/** The mockup's `.card`: white, hairline border, 14dp corners, whisper of a shadow. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, shadowElevation = 1.dp, modifier = modifier) {
            Column(content = content)
        }
    } else {
        Surface(shape = shape, color = color, border = border, shadowElevation = 1.dp, modifier = modifier) {
            Column(content = content)
        }
    }
}

/** The mockup's `.kv`: a two-column spec grid with uppercase keys. */
@Composable
fun KeyValueGrid(entries: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        entries.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEachIndexed { cellIndex, (key, value) ->
                    if (cellIndex > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 11.dp)) {
                        Text(
                            key.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.titleSmall.tabular,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
```

- [ ] **Step 4: `components/Rows.kt`**

```kotlin
enum class TickStyle { CHECK, ADD, DELETE }

/** The mockup's `.netrow`: label, optional dot and sublabel, trailing tick circle. */
@Composable
fun TickRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    dotColor: Color? = null,
    tick: TickStyle = TickStyle.CHECK,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        dotColor?.let { NetworkDot(it) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            sublabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val scheme = MaterialTheme.colorScheme
        val (container, borderColor, iconTint) = when {
            tick == TickStyle.DELETE -> Triple(scheme.errorContainer, Color(0xFFF2B8B2), scheme.error)
            tick == TickStyle.ADD -> Triple(scheme.primaryContainer, Color(0xFFA8C7FA), scheme.primary)
            checked -> Triple(scheme.primary, scheme.primary, scheme.onPrimary)
            else -> Triple(Color.Transparent, scheme.outlineVariant, Color.Transparent)
        }
        Box(
            Modifier.size(22.dp).background(container, CircleShape).border(1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (tick) {
                TickStyle.CHECK -> R.drawable.ic_check
                TickStyle.ADD -> R.drawable.ic_add
                TickStyle.DELETE -> R.drawable.ic_remove
            }
            if (tick != TickStyle.CHECK || checked) {
                Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** The mockup's `.prefrow`: drawer entries with icon, summary and chevron. */
@Composable
fun PrefRow(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, sublabel: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            sublabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", style = MaterialTheme.typography.bodyLarge, color = ChargeAheadColors.faint)
    }
}

/** The mockup's `.gobtn`/`.sendone`: the small square action at a row's end. */
@Composable
fun GoButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = containerColor, modifier = modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}
```

- [ ] **Step 5: `components/Inputs.kt`**

```kotlin
/** The mockup's `.searchwrap`: bordered, rounded, magnifier left, no underline. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = ChargeAheadColors.faint) },
        leadingIcon = {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    )
}

/** The mockup's `.chip`: soft round pill with an optional blue icon. */
@Composable
fun AppChip(text: String, modifier: Modifier = Modifier, icon: Painter? = null) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium.tabular)
        }
    }
}
```

- [ ] **Step 6: `components/AppSheet.kt`**

```kotlin
/**
 * The mockup's `.sheet`: opens half-height, drags up to fullscreen. The
 * expansion is native ModalBottomSheet behavior — content taller than half
 * the screen starts partially expanded; below-the-fold sections are the
 * mockup's `.fullonly`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) { content() }
    }
}
```

- [ ] **Step 7: `components/AppTopBar.kt`**

```kotlin
/** The mockup's `.topbar`: centered title with optional sub-line, hairline below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier) {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.tabular.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            navigationIcon = {
                TopBarIcon(painterResource(R.drawable.ic_back), stringResource(R.string.common_back), onBack)
            },
            actions = actions,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The mockup's `.iconbtn`: 38dp soft square, with the blue filter-active dot on demand. */
@Composable
fun TopBarIcon(icon: Painter, contentDescription: String?, onClick: () -> Unit, badge: Boolean = false) {
    Box(Modifier.padding(horizontal = 8.dp)) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}
```

- [ ] **Step 8: Compile**

Run: `./gradlew :androidApp:compileDebugKotlin` — expected BUILD SUCCESSFUL. (Nothing uses the components yet; this task only has to compile.)

- [ ] **Step 9: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): the mockup's css classes, reborn as composables"
```

---

### Task 4: MainActivity — AppTopBar, filter badge, detail subtitle

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (the `Scaffold` `topBar` block, currently lines 291–326)
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AppTopBar`, `TopBarIcon` (Task 3); `ChargeFilters.isDefault`, `NetworkPreferences.isActive` (exist in shared).
- Produces: `val filtersCustomized = !filters.isDefault || networks.isActive` and `val networksSummary: String`, both computed in `PhoneApp` — Task 5 (home burger badge) and Task 6 (drawer) read them. Detail and networks pages get subtitles.

- [ ] **Step 1: Strings**

```xml
<string name="trip_topbar_sub">ab jetzt · %1$d Stopps</string>
<string name="trip_filters">Filter</string>
<string name="detail_stop_x_of_y">Stopp %1$d von %2$d</string>
<string name="drawer_networks_all">alle aktiv</string>
<string name="drawer_networks_selected">%1$d ausgewählt</string>
```

- [ ] **Step 2: Replace the topBar block**

In `PhoneApp`, right after the settings flows are collected, add:

```kotlin
val filtersCustomized = !filters.isDefault || networks.isActive
val networksSummary = if (networks.isActive) {
    stringResource(R.string.drawer_networks_selected, networks.preferredOperators.size)
} else {
    stringResource(R.string.drawer_networks_all)
}
```

Replace the whole `topBar = { if (page != Page.HOME) { TopAppBar(...) } }` block with:

```kotlin
topBar = {
    if (page != Page.HOME) {
        AppTopBar(
            title = when (page) {
                Page.TRIP -> "→ ${tripPlan?.destination?.name.orEmpty()}"
                Page.STOP_DETAIL -> stringResource(R.string.detail_title)
                Page.GARAGE -> stringResource(R.string.garage_title)
                Page.VEHICLE_EDIT -> stringResource(R.string.phone_settings_title)
                Page.SUBSCRIPTIONS -> stringResource(R.string.subs_title)
                Page.NETWORKS -> stringResource(R.string.phone_networks_title)
                Page.CAR_DATA -> stringResource(R.string.cardata_title)
                Page.HOME -> stringResource(R.string.app_name)
            },
            subtitle = when (page) {
                Page.TRIP -> tripPlan?.let { stringResource(R.string.trip_topbar_sub, it.stops.size) }
                Page.STOP_DETAIL -> {
                    val plan = tripPlan
                    val stop = detailStop
                    if (plan != null && stop != null && stop in plan.stops) {
                        stringResource(R.string.detail_stop_x_of_y, plan.stops.indexOf(stop) + 1, plan.stops.size)
                    } else {
                        null
                    }
                }
                Page.NETWORKS -> networksSummary
                else -> null
            },
            onBack = {
                page = when (page) {
                    Page.STOP_DETAIL -> Page.TRIP
                    Page.VEHICLE_EDIT -> Page.GARAGE
                    else -> Page.HOME
                }
            },
            actions = {
                if (page == Page.TRIP) {
                    TopBarIcon(
                        painterResource(R.drawable.ic_filter),
                        stringResource(R.string.trip_filters),
                        onClick = { scope.launch { drawerState.open() } },
                        badge = filtersCustomized,
                    )
                }
            },
        )
    }
},
```

Drop the now-unused `TopAppBar`/`IconButton` imports.

- [ ] **Step 3: Compile + verify**

`./gradlew :androidApp:compileDebugKotlin`, then via `app-laufen-lassen`: plan any route, check the trip top bar shows "→ {Ziel}" with the sub-line, the filter icon opens the drawer, and (after changing min power in the drawer) the blue badge dot appears. Open a stop: "Stopp 1 von n" under the title.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): topbar with subtitle, filters reachable from the trip"
```

---

### Task 5: Home screen — own file, pills, scrim, burger badge

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/HomeScreen.kt` (move `HomeScreen` out of `MainActivity.kt` lines 514–657)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (call site gains `filtersCustomized` parameter)

**Interfaces:**
- Consumes: `filtersCustomized` (Task 4), theme (Task 2).
- Produces: `HomeScreen(...)` — exactly the existing parameter list plus `filtersCustomized: Boolean`. Internal `HomePill` stays `private`.

- [ ] **Step 1: Move & restyle**

Move the `HomeScreen` composable into `HomeScreen.kt` unchanged in logic, then apply these changes inside it:

Add after the map, before the menu button (the mockup's `.topscrim`):

```kotlin
Box(
    Modifier
        .fillMaxWidth()
        .height(96.dp)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), Color.Transparent))),
)
```

Replace the burger `FloatingActionButton` with:

```kotlin
Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)) {
    Surface(
        onClick = onMenu,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.size(46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_menu),
                contentDescription = stringResource(R.string.home_menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (filtersCustomized) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(11.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}
```

Replace the bottom `Row` of FABs with mockup pills:

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
) {
    HomePill(
        text = stringResource(R.string.home_pill_plan),
        icon = painterResource(R.drawable.ic_route),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        onClick = onPlan,
    )
    HomePill(
        text = stringResource(R.string.home_pill_charge_now),
        icon = painterResource(R.drawable.ic_battery),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        iconTint = MaterialTheme.colorScheme.tertiary,
        onClick = onChargeNow,
    )
    HomePill(
        text = null,
        icon = painterResource(R.drawable.ic_heart),
        contentDescription = stringResource(R.string.home_routes),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        iconTint = MaterialTheme.colorScheme.error,
        onClick = onRoutes,
    )
}
```

```kotlin
/** The mockup's `.pill`: fully round, floating, 15sp/700. */
@Composable
private fun HomePill(
    text: String?,
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    iconTint: Color = contentColor,
    contentDescription: String? = null,
) {
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, contentColor = contentColor, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = if (text != null) 21.dp else 15.dp, vertical = 14.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(18.dp))
            text?.let { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)) }
        }
    }
}
```

The demo-notice/zoom-hint/permission blocks stay; change the demo notice to `Fineprint`-sized error-colored text (keep `color = MaterialTheme.colorScheme.error`).

- [ ] **Step 2: Update the call site**

In `MainActivity`, add `filtersCustomized = filtersCustomized` to the `HomeScreen(...)` call and delete the moved composable + now-unused imports (`ExtendedFloatingActionButton`, `FloatingActionButton`).

- [ ] **Step 3: Compile + verify**

`./gradlew :androidApp:compileDebugKotlin`; screenshot home vs mockup `#scr-home`: blue Plan pill, white Charge-now pill with green bolt, heart circle, white burger with badge after customizing a filter, scrim visible at the top.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): home pills like the mockup, burger learns the filter dot"
```

---

### Task 6: Drawer — own file, mockup structure

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/DrawerContent.kt` (move `DrawerContent` out of `MainActivity.kt` lines 659–785)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (drawer call site)
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SectionLabel`, `PrefRow`, `Fineprint` (Task 3), theme (Task 2), `networksSummary` (Task 4).
- Produces: `DrawerContent(vehicleName: String?, activeTariffCount: Int, networksSummary: String, filters: ChargeFilters, labelStyle: MapLabelStyle, onOpen: (Page) -> Unit, onFilters: (ChargeFilters) -> Unit, onLabelStyle: (MapLabelStyle) -> Unit)` — same as today plus `networksSummary`. `DrawerContent` already takes `Page` and lives in the same package; keep `Page` in MainActivity.kt but change its visibility from `private` to `internal`.

- [ ] **Step 1: Rebuild `DrawerContent` in its own file**

Change `private enum class Page` to `internal enum class Page` in MainActivity.kt. New file:

```kotlin
@Composable
internal fun DrawerContent(
    vehicleName: String?,
    activeTariffCount: Int,
    networksSummary: String,
    filters: ChargeFilters,
    labelStyle: MapLabelStyle,
    onOpen: (Page) -> Unit,
    onFilters: (ChargeFilters) -> Unit,
    onLabelStyle: (MapLabelStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The mockup's drawer-head: bolt + wordmark over a hairline.
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(vertical = 14.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_bolt),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_preferences))
            PrefRow(
                icon = painterResource(R.drawable.ic_car),
                label = stringResource(R.string.drawer_car),
                sublabel = vehicleName ?: stringResource(R.string.drawer_car_none),
                onClick = { onOpen(Page.GARAGE) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            PrefRow(
                icon = painterResource(R.drawable.ic_cardpay),
                label = stringResource(R.string.drawer_subscriptions),
                sublabel = stringResource(R.string.drawer_subs_count, activeTariffCount),
                onClick = { onOpen(Page.SUBSCRIPTIONS) },
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_filters))
            PrefRow(
                icon = painterResource(R.drawable.ic_filter),
                label = stringResource(R.string.drawer_networks),
                sublabel = networksSummary,
                onClick = { onOpen(Page.NETWORKS) },
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_min_power))
            PowerSegments(filters, onFilters)
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_max_price, filters.maxPriceEuroPerKwh.twoDecimals()))
            Slider(
                value = filters.maxPriceEuroPerKwh.toFloat(),
                onValueChange = { onFilters(filters.copy(maxPriceEuroPerKwh = (it * 100).roundToInt() / 100.0)) },
                valueRange = 0.4f..1.0f,
            )
            SectionLabel(stringResource(R.string.drawer_max_distance, filters.maxDistanceKm.oneDecimal()))
            Slider(
                value = filters.maxDistanceKm.toFloat(),
                onValueChange = { onFilters(filters.copy(maxDistanceKm = (it * 2).roundToInt() / 2.0)) },
                valueRange = 1f..10f,
            )
        }

        Column {
            SectionLabel(stringResource(R.string.drawer_map_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = labelStyle == MapLabelStyle.PRICE,
                    onClick = { onLabelStyle(MapLabelStyle.PRICE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.drawer_label_price)) }
                SegmentedButton(
                    selected = labelStyle == MapLabelStyle.FREE_CHARGERS,
                    onClick = {},
                    enabled = false,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.drawer_label_free)) }
            }
            Fineprint(stringResource(R.string.drawer_label_free_note), modifier = Modifier.padding(top = 6.dp))
        }

        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(stringResource(R.string.drawer_debug), modifier = Modifier.padding(top = 12.dp))
            PrefRow(
                icon = painterResource(R.drawable.ic_send),
                label = stringResource(R.string.drawer_cardata),
                onClick = { onOpen(Page.CAR_DATA) },
            )
        }

        Fineprint(stringResource(R.string.drawer_availability_note))
    }
}

/** The mockup's `.seg`: soft track, white active segment with blue text. */
@Composable
private fun PowerSegments(filters: ChargeFilters, onFilters: (ChargeFilters) -> Unit) {
    val steps = listOf(50.0, 150.0, 300.0)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(3.dp),
    ) {
        steps.forEach { step ->
            val selected = filters.minPowerKw == step
            Surface(
                onClick = { onFilters(filters.copy(minPowerKw = step)) },
                shape = MaterialTheme.shapes.extraSmall,
                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = if (selected) 1.dp else 0.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "${step.roundToInt()} kW",
                    style = MaterialTheme.typography.labelMedium.tabular,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
```

Note `SectionLabel` here carries the value inside the label for the sliders — pass the formatted strings through the existing `drawer_max_price`/`drawer_max_distance` resources (they already contain `%1$s`).

- [ ] **Step 2: Update MainActivity**

Delete the old `DrawerContent` (and unused imports: `NavigationDrawerItem`, `SegmentedButton*` if now unused there). Pass the Task-4 `networksSummary` to `DrawerContent`. Give `ModalDrawerSheet` the mockup's white: `ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) { ... }`.

- [ ] **Step 3: Compile + verify**

Compile; screenshot the open drawer vs mockup `#drawer`: wordmark header, three icon rows with sublabels and chevrons, segmented power with white active chip, two labeled sliders, debug section, fineprint.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): drawer rebuilt — sections, icons, no more default rows"
```

---

### Task 7: Plan sheet — route card, car chip, CTA

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/PlanSheets.kt` (`PlanSheetContent`, lines 46–162)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (Sheet.PLAN uses `AppSheet`)
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AppSheet`, `AppCard`, `AppChip`, `SectionLabel`, `Fineprint` (Task 3); `tabular` (Task 2).
- Produces: `PlanSheetContent` — same signature as today (`recent`, `vehicleName`, `initialSocPercent`, `onSearch`, `onPlan`).

- [ ] **Step 1: Strings**

```xml
<string name="plan_from">Von</string>
<string name="plan_from_current">Dein Standort</string>
<string name="plan_to">Nach</string>
<string name="plan_cta">Route planen</string>
<string name="plan_soc_hint">Ladestand jetzt</string>
```

- [ ] **Step 2: Rebuild `PlanSheetContent`**

Keep the state and debounce logic (`query`, `results`, `searching`, `socText`, `LaunchedEffect`) exactly as it is, add `var chosen by remember { mutableStateOf<Destination?>(null) }`, and replace the layout. Choosing a search result or recent sets `chosen` (and copies its name into `query` for display); editing the query clears `chosen`; the CTA fires `onPlan`:

```kotlin
Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleMedium)

    if (vehicleName == null) {
        Text(
            stringResource(R.string.plan_vehicle_missing),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    // The mockup's routecard: From is fixed, To is the live search field.
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Column {
                SectionLabel(stringResource(R.string.plan_from), modifier = Modifier.padding(0.dp))
                Text(stringResource(R.string.plan_from_current), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.plan_to), modifier = Modifier.padding(0.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it; chosen = null },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.plan_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ChargeAheadColors.faint,
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
            if (searching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    vehicleName?.let { name ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(text = name, icon = painterResource(R.drawable.ic_car))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    BasicTextField(
                        value = socText,
                        onValueChange = { socText = it.filter(Char::isDigit).take(3) },
                        textStyle = MaterialTheme.typography.labelMedium.tabular.copy(
                            color = if (socPercent == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(28.dp),
                    )
                    Text("%", style = MaterialTheme.typography.labelMedium)
                }
            }
            Fineprint(stringResource(R.string.plan_soc_hint))
        }
    }

    when {
        results == null -> Text(
            stringResource(R.string.plan_search_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        results!!.isEmpty() && query.trim().length >= 3 && !searching && chosen == null -> Text(
            stringResource(R.string.plan_no_results),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Button(
        onClick = { chosen?.let { destination -> socPercent?.let { onPlan(destination, it.toDouble()) } } },
        enabled = chosen != null && socPercent != null && vehicleName != null,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(15.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(stringResource(R.string.plan_cta), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
    }

    // Results while typing; recents when idle — the expanded sheet's "fullonly".
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
        if (chosen == null) {
            items(results.orEmpty(), key = { it.name + it.position.lat }) { place ->
                AppCard(onClick = {
                    chosen = Destination(place.name, place.position)
                    query = place.name
                    results = emptyList()
                }) {
                    Text(
                        place.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        if (query.trim().length < 3 && recent.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.plan_recent), modifier = Modifier.padding(top = 8.dp)) }
            items(recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
                AppCard(onClick = { chosen = destination; query = destination.name }) {
                    Text(
                        destination.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
```

Drop the outer `.padding(16.dp)` — `AppSheet` provides horizontal padding.

- [ ] **Step 3: MainActivity — swap the sheet wrapper**

`Sheet.PLAN -> AppSheet(onDismissRequest = { sheet = Sheet.NONE }) { PlanSheetContent(...) }` (same for the other two sheets in Tasks 8–9; here only PLAN).

- [ ] **Step 4: Compile + verify**

Compile; open the plan sheet: route card with From/To, chip + SoC bubble, disabled CTA that enables after picking a result, recents underneath, sheet drags up to fullscreen. Compare with mockup `#sheet-plan`.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): plan sheet gets the route card and an actual cta"
```

---

### Task 8: Charge-now sheet — ranked cards + "Mehr in der Nähe"

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/PlanSheets.kt` (`ChargeNowSheetContent`, lines 164–243)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (Sheet.CHARGE_NOW uses `AppSheet`)
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ChargeNowResult.more` (Task 1); `AppCard`, `RankBadge`, `NetworkDot`, `PriceText`, `GoButton`, `SectionLabel` (Task 3); `operatorColor` (exists in `MapCanvas.kt`).
- Produces: `ChargeNowSheetContent` — signature unchanged.

- [ ] **Step 1: Strings**

```xml
<string name="cn_more">Mehr in der Nähe</string>
```

- [ ] **Step 2: Rebuild the candidate list**

Keep the `loading` / `empty` / relax-notice branches (restyle the notice to `bodySmall`, error color when relaxed, `onSurfaceVariant` otherwise — as today). Replace the `forEachIndexed` row rendering with a `LazyColumn` of cards, and extract one private row composable used by both lists:

```kotlin
@Composable
private fun ChargerRow(
    rank: Int,
    ranked: Boolean,
    candidate: de.autoapp.shared.core.ChargeNowCandidate,
    onNavigate: (de.autoapp.shared.core.ChargeNowCandidate) -> Unit,
) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            RankBadge(
                number = rank,
                color = if (ranked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                textColor = if (ranked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NetworkDot(operatorColor(candidate.site.operator), size = 8.dp)
                    Text(candidate.site.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    stringResource(R.string.cn_distance_power, candidate.distanceKm.oneDecimal(), candidate.maxPowerKw.roundToInt()),
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            candidate.quote.best?.let { PriceText(it.euroPerKwh) }
            GoButton(
                icon = painterResource(R.drawable.ic_destination),
                contentDescription = stringResource(R.string.cn_navigate, candidate.site.name),
                onClick = { onNavigate(candidate) },
            )
        }
    }
}
```

Body of the non-empty branch:

```kotlin
LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
    itemsIndexed(result.candidates, key = { _, c -> c.site.id }) { index, candidate ->
        ChargerRow(rank = index + 1, ranked = true, candidate = candidate, onNavigate = onNavigate)
    }
    if (result.more.isNotEmpty()) {
        item { SectionLabel(stringResource(R.string.cn_more), modifier = Modifier.padding(top = 8.dp)) }
        itemsIndexed(result.more, key = { _, c -> "more-${c.site.id}" }) { index, candidate ->
            ChargerRow(rank = result.candidates.size + index + 1, ranked = false, candidate = candidate, onNavigate = onNavigate)
        }
    }
}
```

- [ ] **Step 3: MainActivity — AppSheet for CHARGE_NOW**

Same swap as Task 7; the no-position message keeps its early branch inside the sheet.

- [ ] **Step 4: Compile + verify**

Compile; open Charge now: three blue-ranked cards with dots, prices, go-buttons; drag up: "MEHR IN DER NÄHE" with gray-ranked rows. Trigger the relax notice (set max distance to 1 km somewhere remote) and check the red subtitle. Compare with mockup `#sheet-charge`.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): charge-now sheet as ranked cards, expands to more nearby"
```

---

### Task 9: Routes sheet — cards with hearts and pencils

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/PlanSheets.kt` (`RoutesSheetContent`, lines 252–336; `RenameDialog` stays)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (Sheet.ROUTES uses `AppSheet`)

**Interfaces:**
- Consumes: `AppCard`, `GoButton`, `SectionLabel` (Task 3).
- Produces: `RoutesSheetContent` — signature unchanged.

- [ ] **Step 1: Rebuild the list**

Replace the `LazyColumn` body (keep `renaming` state + `RenameDialog`):

```kotlin
LazyColumn(modifier = modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.titleMedium) }
    item { SectionLabel(stringResource(R.string.routes_saved)) }
    if (saved.isEmpty()) {
        item {
            AppCard {
                Text(
                    stringResource(R.string.routes_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                )
            }
        }
    }
    items(saved, key = { it.id }) { route ->
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                Column(Modifier.weight(1f).clickable { onOpen(route.destination) }) {
                    Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    route.summary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GoButton(painterResource(R.drawable.ic_pen), stringResource(R.string.routes_rename), onClick = { renaming = route })
                GoButton(
                    painterResource(R.drawable.ic_remove),
                    stringResource(R.string.routes_delete),
                    onClick = { onDelete(route) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    item { SectionLabel(stringResource(R.string.routes_recent), modifier = Modifier.padding(top = 8.dp)) }
    items(recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
        val alreadySaved = saved.any { it.destination.position == destination.position }
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                Text(
                    destination.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { onOpen(destination) },
                )
                GoButton(
                    icon = painterResource(if (alreadySaved) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                    contentDescription = stringResource(R.string.routes_save_recent),
                    onClick = { if (!alreadySaved) onFavorite(destination) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
```

- [ ] **Step 2: MainActivity — AppSheet for ROUTES**

Same swap as Tasks 7–8. All three sheets now use `AppSheet`; the `ModalBottomSheet` import in MainActivity goes away.

- [ ] **Step 3: Compile + verify**

Compile; open ♡: saved routes as cards with pencil + red delete, recents with heart squares (filled when saved), empty-state card when nothing is saved. Compare with mockup `#sheet-routes`.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): routes sheet as cards with hearts where they belong"
```

---

### Task 10: Trip plan screen

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/TripPlanScreen.kt`
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AppCard`, `RankBadge`, `PriceText`, `GoButton`, `NetworkDot` (Task 3); `tabular` (Task 2); `operatorColor`, `MapsHandoff.navigateUrl`, `etaText`, `minutesText` (exist).
- Produces: `TripPlanScreen` gains one parameter, `startSocPercent: Double?` (after `startPosition`) — MainActivity passes `manualSoc`, which `planTo` persists before planning, so it is the SoC the plan started from. Per-stop send reuses `onSendToMaps`.

- [ ] **Step 1: Strings**

```xml
<string name="trip_dep_now">ab jetzt · %1$d %%</string>
<string name="trip_dep_now_unknown">ab jetzt</string>
<string name="trip_arr">an %1$s · %2$d %%</string>
<string name="trip_send_stop">%1$s an Maps senden</string>
<string name="trip_summary_stops">%1$d Stopps</string>
<string name="trip_summary_charging">%1$s laden</string>
<string name="trip_section_hint_second">Jetzt den Endpunkt antippen</string>
```

- [ ] **Step 2: Summary bar (mockup `.summary`)**

Add `startSocPercent: Double?` to `TripPlanScreen`'s parameters and pass `startSocPercent = manualSoc` at the MainActivity call site. Replace `TripSummary` with:

```kotlin
@Composable
private fun TripSummary(plan: TripPlan) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.trip_summary_distance, plan.route.distanceKm.roundToInt()),
                style = MaterialTheme.typography.headlineSmall.tabular,
            )
            Column {
                Text(
                    buildString {
                        append(minutesText(plan.totalMinutes))
                        append(" · ")
                        append(stringResource(R.string.trip_summary_stops, plan.stops.size))
                    },
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append(stringResource(R.string.trip_summary_charging, minutesText(plan.chargeMinutes)))
                        plan.estimatedCostEuro?.let { append(" · ≈ ${it.twoDecimals()} €") }
                    },
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
```

- [ ] **Step 3: Terminus rows, stop cards, per-stop send**

Replace `TerminusRow` with the mockup's `.terminus` (dot, name, dim right label):

```kotlin
@Composable
private fun TerminusRow(
    name: String,
    dotColor: Color,
    squareDot: Boolean,
    rightLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    ) {
        Box(Modifier.size(10.dp).background(dotColor, if (squareDot) RoundedCornerShape(2.dp) else CircleShape))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            rightLabel,
            style = MaterialTheme.typography.bodySmall.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Call sites: start terminus `dotColor = MaterialTheme.colorScheme.tertiary, squareDot = false, rightLabel = startSocPercent?.let { stringResource(R.string.trip_dep_now, it.roundToInt()) } ?: stringResource(R.string.trip_dep_now_unknown)`; destination `dotColor = MaterialTheme.colorScheme.error, squareDot = true, rightLabel = stringResource(R.string.trip_arr, etaText(plan.totalMinutes), plan.arrivalSocPercent.roundToInt())`.

Replace `StopCard` and wrap it with the send button (the mockup's card + `.sendone` side by side):

```kotlin
@Composable
private fun StopCard(index: Int, stop: PlannedStop, selected: Boolean, onClick: () -> Unit) {
    AppCard(
        onClick = onClick,
        modifier = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            RankBadge(index, operatorColor(stop.site.operator))
            Column(Modifier.weight(1f)) {
                Text(stop.site.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        R.string.trip_stop_line,
                        etaText(stop.etaMinutesFromStart - stop.chargeMinutes),
                        stop.chargeMinutes.roundToInt(),
                        stop.maxPowerKw.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            stop.quote.best?.let { PriceText(it.euroPerKwh) }
        }
    }
}
```

In the `items(plan.stops.size)` block, wrap card + send button:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f)) {
        StopCard(
            index = index + 1,
            stop = stop,
            selected = selecting && (selectionA == index + 1 || selectionB == index + 1),
            onClick = { if (selecting) pick(index + 1) else onOpenStop(stop) },
        )
    }
    if (!selecting) {
        GoButton(
            icon = painterResource(R.drawable.ic_send),
            contentDescription = stringResource(R.string.trip_send_stop, stop.site.name),
            onClick = { onSendToMaps(MapsHandoff.navigateUrl(stop.site.position)) },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
```

(Note the ETA fix that comes along: `etaMinutesFromStart` is *departure* from the stop, so arrival = `etaMinutesFromStart - chargeMinutes`.)

- [ ] **Step 4: Selection banner + actions row**

Replace the `if (selecting)` hint `Text` with the mockup's dashed banner (two-step prompt):

```kotlin
if (selecting) {
    val bothPicked = selectionA != null && selectionB != null
    val hint = if (selectionA != null && !bothPicked) {
        stringResource(R.string.trip_section_hint_second)
    } else {
        stringResource(R.string.trip_section_hint)
    }
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFFA8C7FA),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            hint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
```

Actions row: keep the existing logic/labels, restyle: the send `Button` gets `shape = MaterialTheme.shapes.small` and a leading `Icon(painterResource(R.drawable.ic_destination), null, Modifier.size(15.dp))`; the select toggle becomes `OutlinedButton(shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, if (selecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selecting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface))`; the save heart becomes the mockup's `.btn-fav`:

```kotlin
Surface(
    onClick = onToggleSave,
    shape = MaterialTheme.shapes.small,
    color = if (isSaved) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, if (isSaved) Color(0xFFF2B8B2) else MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.size(width = 44.dp, height = 40.dp),
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painterResource(if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart),
            contentDescription = stringResource(R.string.trip_save),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
    }
}
```

The estimate note becomes `Fineprint(stringResource(R.string.trip_estimate_note))`.

- [ ] **Step 5: Compile + verify**

Compile; plan a route: summary bar with big km, stop cards with colored rank circles + prices + per-stop send arrows, terminus rows with dep/arr labels, selection mode shows the dashed banner and blue outlines and hides the send arrows. Compare with mockup `#scr-route`.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): trip screen — summary bar, stop cards, per-stop send"
```

---

### Task 11: Stop detail screen

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/StopDetailScreen.kt`
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `KeyValueGrid`, `AppCard`, `NetworkDot`, `Fineprint` (Task 3); `operatorColor`, `etaText` (exist); `Address` fields `street`/`postalCode`/`town`.
- Produces: `StopDetailScreen` — signature unchanged.

- [ ] **Step 1: Strings**

```xml
<string name="detail_kv_connectors">Anschlüsse</string>
<string name="detail_kv_charge">Geplanter Halt</string>
<string name="detail_kv_charge_value">%1$d min · %2$d → %3$d %%</string>
<string name="detail_kv_arrival">Ankunft</string>
<string name="detail_kv_arrival_value">%1$s bei %2$d %%</string>
<string name="detail_kv_energy">Energie</string>
<string name="detail_kv_energy_value">≈ %1$d kWh</string>
<string name="detail_connector_value">%1$d× CCS · %2$d kW</string>
<string name="detail_cheapest">Günstigster</string>
<string name="detail_navigate_maps">In Google Maps navigieren</string>
```

- [ ] **Step 2: Rebuild the screen**

```kotlin
@Composable
fun StopDetailScreen(
    stop: PlannedStop,
    onSendToMaps: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The mockup's det-head.
        Column {
            stop.site.operator?.let { operator ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NetworkDot(operatorColor(operator))
                    Text(
                        operator.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stop.site.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 5.dp))
            stop.site.address?.let { address ->
                val place = listOfNotNull(address.postalCode, address.town).joinToString(" ")
                val line = listOfNotNull(address.street, place.takeIf { it.isNotBlank() }).joinToString(", ")
                if (line.isNotBlank()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        val dcCount = stop.site.connectors
            .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
            .sumOf { it.count ?: 1 }
        KeyValueGrid(
            listOf(
                stringResource(R.string.detail_kv_connectors) to
                    stringResource(R.string.detail_connector_value, dcCount, stop.maxPowerKw.roundToInt()),
                stringResource(R.string.detail_kv_charge) to stringResource(
                    R.string.detail_kv_charge_value,
                    stop.chargeMinutes.roundToInt(),
                    stop.arrivalSocPercent.roundToInt(),
                    stop.departureSocPercent.roundToInt(),
                ),
                stringResource(R.string.detail_kv_arrival) to stringResource(
                    R.string.detail_kv_arrival_value,
                    etaText(stop.etaMinutesFromStart - stop.chargeMinutes),
                    stop.arrivalSocPercent.roundToInt(),
                ),
                stringResource(R.string.detail_kv_energy) to
                    stringResource(R.string.detail_kv_energy_value, stop.chargeKwh.roundToInt()),
            ),
        )

        // The mockup's pricelist: hairline rows, cheapest tagged.
        AppCard {
            stop.quote.prices.forEachIndexed { index, price ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(price.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                when (price.kind) {
                                    PriceKind.AD_HOC -> R.string.detail_price_adhoc
                                    PriceKind.SUBSCRIPTION -> R.string.detail_price_subscription
                                    PriceKind.ROAMING -> R.string.detail_price_roaming
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (price == stop.quote.best) {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                stringResource(R.string.detail_cheapest).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                        PriceText(price.euroPerKwh)
                    } else {
                        Text(
                            "${price.euroPerKwh.twoDecimals()} €",
                            style = MaterialTheme.typography.titleSmall.tabular,
                        )
                    }
                }
            }
        }

        if (stop.quote.isEstimate) Fineprint(stringResource(R.string.trip_estimate_note))

        Button(
            onClick = { onSendToMaps(MapsHandoff.navigateUrl(stop.site.position)) },
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_destination), contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.detail_navigate_maps))
        }
    }
}
```

The old `detail_plan`/`detail_plan_value`/`detail_energy`/`phone_detail_navigate` strings stay (the corridor dialog and car UI still use some); remove only what `grep` proves unused after the change.

- [ ] **Step 3: Compile + verify**

Compile; open a stop from a trip: network dot + uppercase operator, big name, address, 2×2 spec grid, price card with GÜNSTIGSTER tag, blue CTA. Compare with mockup `#scr-detail`.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): stop detail with kv grid and the cheapest tag"
```

---

### Task 12: Networks + subscriptions screens

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/NetworkSettingsScreen.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/SubscriptionsScreen.kt`

**Interfaces:**
- Consumes: `SearchField`, `AppCard`, `TickRow`, `Fineprint` (Task 3); `operatorColor` (exists).
- Produces: both screens — signatures unchanged.

- [ ] **Step 1: NetworkSettingsScreen**

Keep all logic (folded search, only-preferred switch, All/None acting on `shown`, positional keys). Restyle the skeleton: outer `Column(modifier.padding(horizontal = 18.dp))`, intro as `Fineprint`, `OutlinedTextField` → `SearchField(value = search, onValueChange = { search = it }, placeholder = stringResource(R.string.phone_networks_search))`, All/None keep `OutlinedButton` but with `shape = MaterialTheme.shapes.small`, and the checkbox list becomes an `AppCard` of `TickRow`s inside the existing `LazyColumn` (one `item` wrapping the card, rows generated with `forEachIndexed` inside it is fine here since the list is small and the card needs one border):

```kotlin
LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
    item {
        AppCard {
            shown.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                val checked = option.key in preferences.preferredOperators
                TickRow(
                    label = option.displayName,
                    sublabel = pluralStringResource(R.plurals.phone_networks_count, option.siteCount, option.siteCount),
                    checked = checked,
                    dotColor = operatorColor(option.displayName),
                    onClick = {
                        val updated = if (checked) {
                            preferences.preferredOperators - option.key
                        } else {
                            preferences.preferredOperators + option.key
                        }
                        onChange(preferences.copy(preferredOperators = updated))
                    },
                )
            }
        }
    }
}
```

The switch row stays a `Switch` + `Text(style = titleSmall)`.

- [ ] **Step 2: SubscriptionsScreen**

Same treatment: `SearchField` (placeholder `subs_search`), note as `Fineprint`, list becomes one `AppCard` of `TickRow`s (`label = tariff.displayName`, `sublabel = fee text as today`, `checked = tariff.id in activeIds`, `onClick` toggles — replacing the `Switch`), hairline dividers between rows as above. Padding: `horizontal = 18.dp` on the outer column, vertical rhythm `Arrangement.spacedBy(14.dp)`.

- [ ] **Step 3: Compile + verify**

Compile; open both from the drawer: search field with magnifier, one bordered card with dot/tick rows (networks) and fee sublabels (subscriptions). Compare with mockup `#scr-networks` / `#scr-subs`.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): networks and subscriptions in tick rows"
```

---

### Task 13: Garage + add-car page

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/GarageScreen.kt` (drop `adding` state + `AddVehicleList`; restyle)
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/AddCarScreen.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (new `Page.ADD_CAR`)
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AppCard`, `TickRow`, `TickStyle`, `KeyValueGrid`, `SectionLabel`, `SearchField`, `Fineprint` (Task 3); `VehicleCatalog`, `RangeCalculator` (exist).
- Produces:
  - `GarageScreen(vehicles, selected, socPercent, socFromCar, onSelect, onRemove, onSocChange, onOpenAdvanced, onOpenAdd: () -> Unit, modifier)` — `snackbar` parameter and internal add-list removed.
  - `AddCarScreen(owned: Set<String>, onAdd: (VehiclePreset) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Strings**

```xml
<string name="garage_connector">Anschluss</string>
<string name="garage_connector_ccs">CCS</string>
```

- [ ] **Step 2: `Page.ADD_CAR` in MainActivity**

Add `ADD_CAR` to the `Page` enum. Wire: back handling (`Page.ADD_CAR -> Page.GARAGE` in both `BackHandler` and the top-bar `onBack`), title `garage_add_title`, and the page content:

```kotlin
Page.ADD_CAR -> AddCarScreen(
    owned = vehicles.map { it.displayName }.toSet(),
    onAdd = { preset ->
        scope.launch {
            settings.setVehicle(preset.toProfile())
            snackbar.showSnackbar(context.getString(R.string.garage_added, preset.name))
        }
        page = Page.GARAGE
    },
    modifier = Modifier.fillMaxSize().padding(padding),
)
```

`GarageScreen` call site: drop `snackbar = snackbar`, add `onOpenAdd = { page = Page.ADD_CAR }`.

- [ ] **Step 3: Restyle GarageScreen**

Delete `adding`, `AddVehicleList`, the `snackbar`/`scope` plumbing. New body structure (logic per row unchanged):

```kotlin
LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    item {
        Column {
            SectionLabel(stringResource(R.string.garage_your_cars))
            AppCard {
                vehicles.forEachIndexed { index, vehicle ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    TickRow(
                        label = vehicle.displayName,
                        checked = vehicle.displayName == selected?.displayName,
                        tick = if (deleteMode) TickStyle.DELETE else TickStyle.CHECK,
                        onClick = { if (deleteMode) onRemove(vehicle.displayName) else onSelect(vehicle) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    if (deleteMode) {
                        TextButton(onClick = { deleteMode = false }) { Text(stringResource(R.string.garage_delete_done)) }
                    } else {
                        TextButton(onClick = onOpenAdd) { Text(stringResource(R.string.garage_add)) }
                        Spacer(Modifier.weight(1f))
                        if (vehicles.isNotEmpty()) {
                            TextButton(onClick = { deleteMode = true }) {
                                Text(stringResource(R.string.garage_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (selected == null) {
        item { Fineprint(stringResource(R.string.garage_no_car_yet)) }
    } else {
        item { SelectedVehiclePanel(selected, socPercent, socFromCar, onSelect, onSocChange, onOpenAdvanced) }
    }
}
```

`SelectedVehiclePanel`: specs become a `KeyValueGrid` under `SectionLabel(stringResource(R.string.garage_specs))`:

```kotlin
KeyValueGrid(
    listOf(
        stringResource(R.string.garage_battery) to stringResource(R.string.garage_battery_value, vehicle.usableBatteryKwh.oneDecimal()),
        stringResource(R.string.garage_dc) to (
            vehicle.dcPeakPowerKw?.let { stringResource(R.string.garage_dc_value, it.roundToInt()) }
                ?: stringResource(R.string.garage_dc_unknown)
            ),
        stringResource(R.string.garage_connector) to stringResource(R.string.garage_connector_ccs),
        stringResource(R.string.garage_range) to stringResource(
            R.string.garage_range_value,
            RangeCalculator.rangeKm(vehicle, socPercent = 100.0, reserveSocPercent = 0.0).roundToInt(),
        ),
    ),
)
```

Consumption and SoC sliders each move into an `AppCard` with inner `padding(13.dp)`: label `Text(style = titleSmall.tabular)`, the `Slider` (commit-on-release logic unchanged), and the spec hint as `Fineprint`. The advanced `TextButton` stays last.

- [ ] **Step 4: `AddCarScreen.kt`**

```kotlin
/** The mockup's add-car screen: search the catalog, tap the ＋. */
@Composable
fun AddCarScreen(owned: Set<String>, onAdd: (VehiclePreset) -> Unit, modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    val hits = VehicleCatalog.all.filter {
        it.name !in owned && it.name.contains(search.trim(), ignoreCase = true)
    }

    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchField(
            value = search,
            onValueChange = { search = it },
            placeholder = stringResource(R.string.garage_search),
            modifier = Modifier.padding(top = 12.dp),
        )
        if (hits.isEmpty()) {
            Fineprint(stringResource(R.string.garage_none_found))
        }
        LazyColumn {
            item {
                AppCard {
                    hits.forEachIndexed { index, preset ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        TickRow(
                            label = preset.name,
                            sublabel = stringResource(
                                R.string.garage_preset_line,
                                preset.usableBatteryKwh.oneDecimal(),
                                preset.consumptionKwhPer100Km.oneDecimal(),
                                preset.dcPeakPowerKw.roundToInt(),
                            ),
                            checked = false,
                            tick = TickStyle.ADD,
                            onClick = { onAdd(preset) },
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Compile + verify**

Compile; drawer → Auto: card list with ticks, add/delete rows, spec grid with four cells, two slider cards. "Auto hinzufügen…" opens the catalog page with search + ＋ rows; adding returns to the garage with a snackbar.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src
git commit -m "feat(LD-0000): garage in cards, add-car becomes its own page"
```

---

### Task 14: Light polish + dead code

**Files:**
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/VehicleSettingsScreen.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/CarDataDebugScreen.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/CorridorStopDialog.kt`
- Delete: `androidApp/src/main/kotlin/de/autoapp/android/phone/DestinationScreen.kt`

**Interfaces:**
- Consumes: `SectionLabel`, `AppCard`, `Fineprint` (Task 3).
- Produces: no signature changes.

- [ ] **Step 1: Delete `DestinationScreen.kt`**

It is defined but never called (verified in the spec). `grep -rn "DestinationScreen" androidApp/src/main/kotlin/de/autoapp/android/phone` must afterwards return nothing; if that deletion orphans strings, leave them — the car package shares some.

- [ ] **Step 2: Light restyle, mechanical rules**

In `VehicleSettingsScreen.kt` and `CarDataDebugScreen.kt`, apply only these substitutions — no structural changes:
- Section headings (`Text(..., style = typography.titleSmall)` used as headers) → `SectionLabel(...)`.
- `Card { ... }` → `AppCard { ... }` (padding stays inside).
- Explanatory `bodySmall` intro/footnote texts → `Fineprint(...)`.

In `CorridorStopDialog.kt`: secondary/address/connector lines get `color = MaterialTheme.colorScheme.onSurfaceVariant`; the connectors heading becomes `SectionLabel(stringResource(R.string.phone_detail_connectors))`. `AlertDialog` itself stays.

- [ ] **Step 3: Compile + verify**

Compile; open drawer → Debug → Fahrzeugdaten and garage → advanced: same content, mockup tone. Tap a map pin for the corridor dialog.

- [ ] **Step 4: Commit**

```bash
git add -A androidApp/src
git commit -m "chore(LD-0000): polish for the leftover screens, destination screen was dead"
```

---

### Task 15: Full verification walk

**Files:** none (fixes go where they belong; separate small commits).

- [ ] **Step 1: Run the demo-script flows**

Via `app-laufen-lassen`, walk the mockup's five flows and screenshot each stage:
1. **Charge now** — pills → sheet, ranks, prices, drag to fullscreen ("Mehr in der Nähe"), go-button hands to Maps chooser.
2. **Plan** — sheet → route card → pick destination → CTA → trip screen (summary, cards, terminus rows).
3. **Send** — whole route, a marked section (dashed banner, outlines), and a single stop via the ↗ button.
4. **Menu** — drawer sections, car/subscriptions/networks screens, filter change → badge dot on burger + trip filter icon.
5. **♡** — routes sheet, favorite a recent, rename, delete; heart on the trip screen fills.

- [ ] **Step 2: Compare against the mockup**

Open `docs/mockup/index.html` in a browser next to the screenshots. For each screen note deviations that are *not* covered by the spec's no-fiction rule; fix what's a clear miss (spacing, weight, color), commit fixes as `fix(LD-0000): …`.

- [ ] **Step 3: Full test suite**

Run: `./gradlew :shared:jvmTest :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 4: Update `plans/UI.md`**

Replace its content with a pointer to the spec and plan (it's superseded):

```markdown
Done — see docs/superpowers/specs/2026-09-07-ui-overhaul-design.md and
docs/superpowers/plans/2026-09-07-ui-overhaul.md.
```

Commit: `chore(LD-0000): ui.md sketch superseded by the actual plan`
