package dk.brugtbil.browser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.brugtbil.browser.data.Aggregator
import dk.brugtbil.browser.data.BilbasenClient
import dk.brugtbil.browser.data.Listing
import dk.brugtbil.browser.data.ModelGroup
import dk.brugtbil.browser.data.SearchFilters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val filters: SearchFilters = SearchFilters(),
    val loading: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val groups: List<ModelGroup> = emptyList(),
    val listingsScanned: Int = 0,
    val searched: Boolean = false,
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var listings: List<Listing> = emptyList()
    private var job: Job? = null

    fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
    }

    fun search(client: BilbasenClient, maxPages: Int = MAX_PAGES) {
        job?.cancel()
        val filters = _state.value.filters
        _state.update { it.copy(loading = true, error = null, progress = "Henter fra Bilbasen…") }
        job = viewModelScope.launch {
            try {
                listings = client.fetch(filters, maxPages) { page, count ->
                    _state.update { it.copy(progress = "Side $page hentet · $count annoncer") }
                }
                _state.update {
                    it.copy(
                        loading = false,
                        searched = true,
                        listingsScanned = listings.size,
                        groups = Aggregator.group(listings, filters),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Ukendt fejl") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(loading = false) }
    }

    companion object {
        const val MAX_PAGES = 10
    }
}
