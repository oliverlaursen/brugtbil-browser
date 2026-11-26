package dk.brugtbil.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dk.brugtbil.browser.data.FuelType
import dk.brugtbil.browser.data.Gearbox
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dk.brugtbil.browser.data.Catalog
import dk.brugtbil.browser.data.ModelGroup
import dk.brugtbil.browser.data.SearchFilters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onFilters: ((SearchFilters) -> SearchFilters) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onOpenGroup: (ModelGroup) -> Unit,
    hiddenWebView: @Composable () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Brugtbil Browser") }) }) { padding ->
      Box(Modifier.fillMaxSize()) {
        hiddenWebView()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { FilterPanel(state.filters, state.loading, onFilters, onSearch, onCancel) }
            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(state.progress, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error) }
            }
            if (state.searched && !state.loading) {
                item {
                    Text(
                        "${state.groups.size} modeller · ${state.groups.sumOf { it.count }} af ${state.listingsScanned} hentede annoncer matcher",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (state.groups.isEmpty()) {
                    item { Text("Ingen biler matcher filtrene.") }
                }
            }
            items(state.groups, key = { it.makeSlug + "/" + it.modelSlug }) { g ->
                ModelRow(g, onClick = { onOpenGroup(g) })
            }
        }
      }
    }
}

@Composable
private fun ModelRow(g: ModelGroup, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CarImage(g.imageUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    range(g.minPrice, g.maxPrice, ::kr).orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                val details = listOfNotNull(
                    "${g.count} ${if (g.count == 1) "bil" else "biler"}",
                    range(g.minYear, g.maxYear) { it.toString() },
                    range(g.minKm, g.maxKm, ::km),
                    g.fuels.takeIf { it.isNotEmpty() }?.joinToString("/") { it.label },
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Åbn på Bilbasen")
        }
    }
}

@Composable
private fun CarImage(url: String?) {
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier.width(112.dp).height(84.dp).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (url == null) {
        Box(modifier) { placeholder() }
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .addHeader("Referer", "https://www.bilbasen.dk/")
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { placeholder() },
        error = { placeholder() },
        modifier = modifier,
    )
}

@Composable
private fun FilterPanel(
    f: SearchFilters,
    loading: Boolean,
    onFilters: ((SearchFilters) -> SearchFilters) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SuggestField(
            label = "Mærke",
            value = f.brand,
            suggestions = Catalog.brands.map { it.name },
            onValue = { v -> onFilters { it.copy(brand = v, model = if (v != it.brand) "" else it.model) } },
        )
        val models = Catalog.brandBySlug(Catalog.brandSlugFor(f.brand))?.models.orEmpty()
        SuggestField(
            label = "Model",
            value = f.model,
            suggestions = models,
            enabled = f.brand.isNotBlank(),
            onValue = { v -> onFilters { it.copy(model = v) } },
        )
        RangeRow("Årgang fra", f.yearFrom, "Årgang til", f.yearTo,
            onFrom = { v -> onFilters { it.copy(yearFrom = v) } },
            onTo = { v -> onFilters { it.copy(yearTo = v) } })
        RangeRow("Km fra", f.kmFrom, "Km til", f.kmTo,
            onFrom = { v -> onFilters { it.copy(kmFrom = v) } },
            onTo = { v -> onFilters { it.copy(kmTo = v) } })
        RangeRow("Pris fra (kr.)", f.priceFrom, "Pris til (kr.)", f.priceTo,
            onFrom = { v -> onFilters { it.copy(priceFrom = v) } },
            onTo = { v -> onFilters { it.copy(priceTo = v) } })
        ChipGroup("Drivmiddel") {
            FuelType.entries.forEach { fuel ->
                FilterChip(
                    selected = fuel in f.fuels,
                    onClick = { onFilters { it.copy(fuels = if (fuel in it.fuels) it.fuels - fuel else it.fuels + fuel) } },
                    label = { Text(fuel.label) },
                )
            }
        }
        ChipGroup("Gearkasse") {
            FilterChip(selected = f.gearbox == null, onClick = { onFilters { it.copy(gearbox = null) } }, label = { Text("Alle") })
            Gearbox.entries.forEach { gb ->
                FilterChip(
                    selected = f.gearbox == gb,
                    onClick = { onFilters { it.copy(gearbox = gb) } },
                    label = { Text(gb.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSearch, enabled = !loading) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Søg")
            }
            if (loading) OutlinedButton(onClick = onCancel) { Text("Stop") }
            else OutlinedButton(onClick = { onFilters { SearchFilters() } }) { Text("Nulstil") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun RangeRow(
    fromLabel: String, from: Int?, toLabel: String, to: Int?,
    onFrom: (Int?) -> Unit, onTo: (Int?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(fromLabel, from, onFrom, Modifier.weight(1f))
        NumberField(toLabel, to, onTo, Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(label: String, value: Int?, onValue: (Int?) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { s -> onValue(s.filter { it.isDigit() }.take(7).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestField(
    label: String,
    value: String,
    suggestions: List<String>,
    onValue: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = suggestions.filter { value.isBlank() || it.contains(value, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValue(it); expanded = true },
            label = { Text(label) },
            placeholder = { Text("Alle") },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled),
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Alle") }, onClick = { onValue(""); expanded = false })
                filtered.forEach { s ->
                    DropdownMenuItem(text = { Text(s) }, onClick = { onValue(s); expanded = false })
                }
            }
        }
    }
}
