package dev.agiro.fanel.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agiro.fanel.android.data.offline.ShoppingRepository
import dev.agiro.fanel.android.data.remote.AddItemRequest
import dev.agiro.fanel.android.data.remote.ShoppingItemDto
import dev.agiro.fanel.android.data.remote.ShoppingListDto
import dev.agiro.fanel.android.data.remote.UpdateItemRequest
import dev.agiro.fanel.android.sync.HouseholdEvents
import dev.agiro.fanel.android.sync.HouseholdSyncScheduler
import dev.agiro.fanel.android.sync.SyncSchedulerContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingCategoryGroup(
    val name: String,
    val items: List<ShoppingItemDto>
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(
    application: Application,
    private val repository: ShoppingRepository = (application as FanelApplication).appContainer.shoppingRepository,
    private val sessionStore: SessionStore = (application as FanelApplication).appContainer.sessionStore,
    private val householdEvents: HouseholdEvents = (application as FanelApplication).appContainer.householdEvents,
    private val syncScheduler: SyncSchedulerContract = HouseholdSyncScheduler
) : AndroidViewModel(application) {
    private val householdId = MutableStateFlow("")
    private val _selectedListId = MutableStateFlow<String?>(null)
    private val _groupByCategory = MutableStateFlow(true)
    private val _loading = MutableStateFlow(false)
    private val _errorRes = MutableStateFlow<Int?>(null)

    private val lists: Flow<List<ShoppingListDto>> = householdId.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList()) else repository.observeLists(id)
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<ShoppingUiState> = combine(
        householdId, lists, _selectedListId, _groupByCategory, _loading, _errorRes
    ) { values ->
        val currentHouseholdId = values[0] as String
        val lists = values[1] as List<ShoppingListDto>
        val selectedListId = values[2] as String?
        val groupByCategory = values[3] as Boolean
        val loading = values[4] as Boolean
        val errorRes = values[5] as Int?

        val activeList = lists.firstOrNull { it.id == selectedListId } ?: lists.firstOrNull()
        val uncategorized = getApplication<Application>().getString(R.string.shopping_uncategorized)
        val items = activeList?.items.orEmpty()
        val groups = items
            .groupBy { it.category?.trim()?.ifEmpty { null } ?: uncategorized }
            .toSortedMap(compareBy({ it == uncategorized }, { it }))
            .map { (name, groupItems) -> ShoppingCategoryGroup(name, groupItems) }

        ShoppingUiState(
            householdId = currentHouseholdId,
            lists = lists,
            activeList = activeList,
            groupByCategory = groupByCategory,
            groups = groups,
            hasPurchased = items.any { it.done },
            loading = loading,
            errorRes = errorRes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState.empty())

    init {
        activateHousehold(sessionStore.householdId)
        subscribeToEvents()
        viewModelScope.launch {
            repository.observeDropped().collect { _errorRes.value = R.string.shopping_sync_dropped }
        }
    }

    private fun subscribeToEvents() {
        viewModelScope.launch {
            householdId
                .flatMapLatest { id ->
                    if (id.isBlank()) emptyFlow() else householdEvents.observe(id)
                }
                .collect { topic -> if (topic == "shopping") refresh() }
        }
    }

    fun activateHousehold(newHouseholdId: String) {
        if (householdId.value == newHouseholdId) return
        householdId.value = newHouseholdId
        refresh()
    }

    fun refresh() {
        val currentHouseholdId = householdId.value
        if (currentHouseholdId.isBlank()) return
        _loading.value = true
        viewModelScope.launch {
            runCatching { repository.sync(currentHouseholdId) }
                .onSuccess { _errorRes.value = null }
                .onFailure {
                    if (repository.cached(currentHouseholdId).isNullOrEmpty()) {
                        _errorRes.value = R.string.shopping_load_error
                    }
                }
            _loading.value = false
        }
    }

    fun selectList(listId: String) {
        _selectedListId.value = listId
    }

    fun toggleGroupByCategory() {
        _groupByCategory.value = !_groupByCategory.value
    }

    fun createList(name: String) {
        mutate { _selectedListId.value = repository.createList(it, name) }
    }

    fun renameList(listId: String, name: String) {
        mutate { repository.renameList(it, listId, name) }
    }

    fun deleteList(listId: String) {
        mutate {
            repository.deleteList(it, listId)
            _selectedListId.value = null
        }
    }

    fun addItem(listId: String, request: AddItemRequest) {
        mutate { repository.addItem(it, listId, request) }
    }

    fun toggleDone(item: ShoppingItemDto) {
        mutate { repository.updateItem(it, item.id, UpdateItemRequest(done = !item.done)) }
    }

    fun toggleRecurring(item: ShoppingItemDto) {
        mutate { repository.updateItem(it, item.id, UpdateItemRequest(recurring = !item.recurring)) }
    }

    fun removeItem(item: ShoppingItemDto) {
        mutate { repository.removeItem(it, item.id) }
    }

    fun clearPurchased(listId: String) {
        mutate { repository.clearPurchased(it, listId) }
    }

    /** Applies the change to the local cache, then tries to push it; if offline, WorkManager retries later. */
    private fun mutate(block: suspend (String) -> Unit) {
        val currentHouseholdId = householdId.value
        if (currentHouseholdId.isBlank()) return
        viewModelScope.launch {
            runCatching { block(currentHouseholdId) }
                .onFailure { _errorRes.value = R.string.shopping_save_error }
                .onSuccess {
                    runCatching { repository.sync(currentHouseholdId) }
                        .onFailure { syncScheduler.enqueueImmediate(getApplication(), currentHouseholdId) }
                }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShoppingViewModel(application) as T
    }
}

data class ShoppingUiState(
    val householdId: String,
    val lists: List<ShoppingListDto>,
    val activeList: ShoppingListDto?,
    val groupByCategory: Boolean,
    val groups: List<ShoppingCategoryGroup>,
    val hasPurchased: Boolean,
    val loading: Boolean,
    val errorRes: Int?
) {
    fun pendingCount(list: ShoppingListDto): Int = list.items.orEmpty().count { !it.done }

    companion object {
        fun empty(): ShoppingUiState = ShoppingUiState(
            householdId = "",
            lists = emptyList(),
            activeList = null,
            groupByCategory = true,
            groups = emptyList(),
            hasPurchased = false,
            loading = false,
            errorRes = null
        )
    }
}
