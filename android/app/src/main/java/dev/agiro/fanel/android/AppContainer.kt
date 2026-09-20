package dev.agiro.fanel.android

import android.content.Context
import androidx.room.Room
import dev.agiro.fanel.android.data.CalendarRepository
import dev.agiro.fanel.android.data.CalendarRepositoryContract
import dev.agiro.fanel.android.data.local.FanelDatabase
import dev.agiro.fanel.android.data.offline.ChoresRepository
import dev.agiro.fanel.android.data.offline.MembersRepository
import dev.agiro.fanel.android.data.offline.MenuRepository
import dev.agiro.fanel.android.data.offline.OutboxPusher
import dev.agiro.fanel.android.data.offline.RecipesRepository
import dev.agiro.fanel.android.data.offline.ShoppingRepository
import dev.agiro.fanel.android.data.remote.ApiFactory
import dev.agiro.fanel.android.data.remote.AssistantApi
import dev.agiro.fanel.android.data.remote.ChoresApi
import dev.agiro.fanel.android.data.remote.HouseholdApi
import dev.agiro.fanel.android.data.remote.MenuApi
import dev.agiro.fanel.android.data.remote.RecipesApi
import dev.agiro.fanel.android.data.remote.ShoppingApi
import dev.agiro.fanel.android.sync.HouseholdEvents
import dev.agiro.fanel.android.sync.HouseholdSync
import dev.agiro.fanel.android.sync.HouseholdSyncContract
import dev.agiro.fanel.android.sync.SseHouseholdEvents

class AppContainer(context: Context) : AppContainerContract {
    override val sessionStore = SessionStore(context.applicationContext)
    override val authStore = AuthStore(context.applicationContext)

    private val database = Room.databaseBuilder(
        context.applicationContext,
        FanelDatabase::class.java,
        "fanel-android.db"
    ).addMigrations(FanelDatabase.MIGRATION_1_2, FanelDatabase.MIGRATION_2_3).build()

    private val syncPreferences = SyncPreferences(context.applicationContext)

    private var _householdApi: HouseholdApi = buildHouseholdApi()
    override val householdApi: HouseholdApi
        get() = _householdApi

    private var _recipesApi: RecipesApi = buildRecipesApi()
    override val recipesApi: RecipesApi
        get() = _recipesApi

    private var _menuApi: MenuApi = buildMenuApi()
    override val menuApi: MenuApi
        get() = _menuApi

    private var _shoppingApi: ShoppingApi = buildShoppingApi()
    override val shoppingApi: ShoppingApi
        get() = _shoppingApi

    private var _choresApi: ChoresApi = buildChoresApi()
    override val choresApi: ChoresApi
        get() = _choresApi

    private var _assistantApi: AssistantApi = buildAssistantApi()
    override val assistantApi: AssistantApi
        get() = _assistantApi

    private var _householdEvents: HouseholdEvents = buildHouseholdEvents()
    override val householdEvents: HouseholdEvents
        get() = _householdEvents

    private var _calendarRepository: CalendarRepositoryContract = buildCalendarRepository()
    override val calendarRepository: CalendarRepositoryContract
        get() = _calendarRepository

    private var offline: OfflineRepositories = buildOfflineRepositories()
    override val membersRepository: MembersRepository
        get() = offline.members
    override val recipesRepository: RecipesRepository
        get() = offline.recipes
    override val menuRepository: MenuRepository
        get() = offline.menu
    override val shoppingRepository: ShoppingRepository
        get() = offline.shopping
    override val choresRepository: ChoresRepository
        get() = offline.chores
    override val householdSync: HouseholdSyncContract
        get() = offline.sync

    override fun refreshConnection() {
        _householdApi = buildHouseholdApi()
        _recipesApi = buildRecipesApi()
        _menuApi = buildMenuApi()
        _shoppingApi = buildShoppingApi()
        _choresApi = buildChoresApi()
        _assistantApi = buildAssistantApi()
        _householdEvents = buildHouseholdEvents()
        _calendarRepository = buildCalendarRepository()
        offline = buildOfflineRepositories()
    }

    private class OfflineRepositories(
        val members: MembersRepository,
        val recipes: RecipesRepository,
        val menu: MenuRepository,
        val shopping: ShoppingRepository,
        val chores: ChoresRepository,
        val sync: HouseholdSyncContract
    )

    private fun buildOfflineRepositories(): OfflineRepositories {
        val snapshotDao = database.cachedSnapshotDao()
        val pendingDao = database.pendingOperationDao()
        val pusher = OutboxPusher(pendingDao)
        val members = MembersRepository(_householdApi, snapshotDao, pendingDao, pusher)
        val recipes = RecipesRepository(_recipesApi, snapshotDao, pendingDao, pusher)
        val menu = MenuRepository(_menuApi, snapshotDao, pendingDao, pusher)
        val shopping = ShoppingRepository(_shoppingApi, snapshotDao, pendingDao, pusher)
        val chores = ChoresRepository(_choresApi, snapshotDao, pendingDao, pusher)
        return OfflineRepositories(
            members, recipes, menu, shopping, chores,
            HouseholdSync(_calendarRepository, listOf(members, recipes, menu, shopping, chores))
        )
    }

    private fun buildHouseholdApi(): HouseholdApi =
        ApiFactory.householdApi(sessionStore.serverUrl, authStore)

    private fun buildRecipesApi(): RecipesApi =
        ApiFactory.recipesApi(sessionStore.serverUrl, authStore)

    private fun buildMenuApi(): MenuApi =
        ApiFactory.menuApi(sessionStore.serverUrl, authStore)

    private fun buildShoppingApi(): ShoppingApi =
        ApiFactory.shoppingApi(sessionStore.serverUrl, authStore)

    private fun buildChoresApi(): ChoresApi =
        ApiFactory.choresApi(sessionStore.serverUrl, authStore)

    private fun buildAssistantApi(): AssistantApi =
        ApiFactory.assistantApi(sessionStore.serverUrl, authStore)

    private fun buildHouseholdEvents(): HouseholdEvents =
        SseHouseholdEvents(sessionStore.serverUrl, authStore)

    private fun buildCalendarRepository(): CalendarRepositoryContract = CalendarRepository(
        api = ApiFactory.calendarApi(sessionStore.serverUrl, authStore),
        eventDao = database.calendarEventDao(),
        outboxDao = database.outboxDao(),
        syncPreferences = syncPreferences
    )
}
