package org.julakali.chargeahead.android.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.components.Fineprint
import org.julakali.chargeahead.android.phone.components.SectionLabel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.julakali.chargeahead.shared.domain.DataSource
import org.julakali.chargeahead.shared.domain.DatasetKind
import org.julakali.chargeahead.shared.ui.DataSourcesState
import org.julakali.chargeahead.shared.ui.LicensesUiState
import org.julakali.chargeahead.shared.ui.LicensesViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch

/** Imprint (§ 5 DDG) and privacy policy. The privacy policy follows. */
@Composable
fun LegalScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionLabel(stringResource(R.string.about_legal_imprint))
        SelectionContainer {
            Text(stringResource(R.string.about_legal_imprint_body), style = MaterialTheme.typography.bodyMedium)
        }
        SectionLabel(stringResource(R.string.about_legal_privacy), modifier = Modifier.padding(top = 24.dp))
    }
}

/**
 * The data sources, then the open-source libraries, each collapsible, all in
 * the one LazyColumn of [LibrariesContainer]. The library list is generated
 * at build time by the AboutLibraries Gradle plugin.
 */
@Composable
fun LicensesRoute(
    modifier: Modifier = Modifier,
    viewModel: LicensesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LicensesScreen(uiState = uiState, onRetry = viewModel::onRetry, modifier = modifier)
}

@Composable
fun LicensesScreen(
    uiState: LicensesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    var dataSourcesExpanded by rememberSaveable { mutableStateOf(false) }
    var librariesExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(modifier.fillMaxSize()) {
        LibrariesContainer(
            // No libraries while collapsed: the container then renders just header and footer.
            libraries = libraries.takeIf { librariesExpanded },
            modifier = Modifier.fillMaxSize(),
            lazyListState = listState,
            // Room below the last row for the back-to-top button.
            contentPadding = PaddingValues(bottom = 88.dp),
            header = {
                item(key = "intro") {
                    Fineprint(
                        stringResource(R.string.about_licenses_intro, stringResource(R.string.app_name)),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                stickyHeader(key = "data-sources") {
                    SectionHeader(
                        title = stringResource(R.string.about_licenses_data_sources),
                        expanded = dataSourcesExpanded,
                        onToggle = { dataSourcesExpanded = !dataSourcesExpanded },
                    )
                }
                if (dataSourcesExpanded) dataSourceItems(uiState.dataSources, onRetry)
                stickyHeader(key = "libraries") {
                    SectionHeader(
                        title = stringResource(R.string.about_licenses_libraries),
                        expanded = librariesExpanded,
                        onToggle = { librariesExpanded = !librariesExpanded },
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = scrolled,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.about_licenses_back_to_top))
            }
        }
    }
}

/** A tappable section label with a chevron that turns when the section opens. */
@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        SectionLabel(title, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp).rotate(rotation),
        )
    }
}

private fun LazyListScope.dataSourceItems(state: DataSourcesState, onRetry: () -> Unit) {
    when (state) {
        DataSourcesState.Loading -> item(key = "data-sources-loading") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        DataSourcesState.Failed -> item(key = "data-sources-failed") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            ) {
                Fineprint(stringResource(R.string.about_data_sources_failed), modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.about_data_sources_retry)) }
            }
        }

        is DataSourcesState.Loaded -> items(state.sources, key = { "data-source:${it.id}" }) { source ->
            DataSourceRow(source)
        }
    }
}

/** A source's name, linked to its page, then one line per dataset with its licence. */
@Composable
private fun DataSourceRow(source: DataSource) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            source.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (source.url != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = source.url?.let { url -> Modifier.clickable { uriHandler.openUri(url) } } ?: Modifier,
        )
        source.datasets.forEach { dataset ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
                Text(
                    stringResource(dataset.kind.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                dataset.license?.let { license ->
                    Text(
                        license.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (license.url != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        modifier = license.url?.let { url -> Modifier.clickable { uriHandler.openUri(url) } } ?: Modifier,
                    )
                }
                // The slot stays when there is no link, so the licences line up.
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    dataset.url?.let { url ->
                        IconButton(onClick = { uriHandler.openUri(url) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.about_data_sources_open_dataset),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DatasetKind.label: Int
    get() = when (this) {
        DatasetKind.SITES -> R.string.about_dataset_sites
        DatasetKind.STATUS -> R.string.about_dataset_status
        DatasetKind.ROUTING -> R.string.about_dataset_routing
        DatasetKind.PLACES -> R.string.about_dataset_places
        DatasetKind.OTHER -> R.string.about_dataset_other
    }
