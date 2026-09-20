package dev.agiro.fanel.android

import android.app.Application
import com.google.gson.Gson
import dev.agiro.fanel.android.data.CalendarDraft
import dev.agiro.fanel.android.data.CalendarRepositoryContract
import dev.agiro.fanel.android.data.local.CalendarEventEntity
import dev.agiro.fanel.android.data.offline.MembersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun membersRepository(): MembersRepository {
        val store = OfflineTestStore(RuntimeEnvironment.getApplication())
        return MembersRepository(FakeHouseholdApi(), store.snapshotDao, store.pendingDao, store.pusher, Gson())
    }

    @Test
    fun createEventDelegatesToRepository() = runBlocking {
        val repository = FakeCalendarRepository()
        val scheduler = FakeSyncScheduler()
        val viewModel = CalendarViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            syncScheduler = scheduler,
            membersRepository = membersRepository(),
            householdEvents = EmptyHouseholdEvents
        )

        viewModel.activateHousehold("household-1")
        viewModel.createEvent(
            CalendarDraft(
                title = "Dinar",
                date = "2026-09-13",
                time = "14:00",
                durationMinutes = 60,
                addedBy = null,
                assigneeIds = emptyList(),
                recurrenceFreq = null,
                recurrenceInterval = null,
                recurrenceUntil = null
            )
        )

        assertEquals("household-1", repository.createdHouseholdId)
        assertEquals("Dinar", repository.createdDraft?.title)
    }

    @Test
    fun deleteEventDelegatesToRepository() = runBlocking {
        val repository = FakeCalendarRepository()
        val viewModel = CalendarViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            syncScheduler = FakeSyncScheduler(),
            membersRepository = membersRepository(),
            householdEvents = EmptyHouseholdEvents
        )
        val event = CalendarEventEntity(
            householdId = "household-1",
            occurrenceKey = "key",
            localId = "local",
            remoteId = "remote",
            title = "Títol",
            date = "2026-09-13",
            anchorDate = null,
            time = null,
            durationMinutes = null,
            addedBy = null,
            assigneeIds = "",
            recurrenceFreq = null,
            recurrenceInterval = null,
            recurrenceUntil = null,
            pendingStatus = null
        )

        viewModel.deleteEvent(event)

        assertEquals(event, repository.deletedEvent)
    }

    @Test
    fun updateEventDelegatesToRepository() = runBlocking {
        val repository = FakeCalendarRepository()
        val viewModel = CalendarViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            syncScheduler = FakeSyncScheduler(),
            membersRepository = membersRepository(),
            householdEvents = EmptyHouseholdEvents
        )
        val event = CalendarEventEntity(
            householdId = "household-1",
            occurrenceKey = "key",
            localId = "local",
            remoteId = "remote",
            title = "Vell",
            date = "2026-09-13",
            anchorDate = null,
            time = null,
            durationMinutes = null,
            addedBy = null,
            assigneeIds = "",
            recurrenceFreq = null,
            recurrenceInterval = null,
            recurrenceUntil = null,
            pendingStatus = null
        )
        val draft = CalendarDraft(
            title = "Nou",
            date = "2026-09-14",
            time = "10:00",
            durationMinutes = null,
            addedBy = null,
            assigneeIds = listOf("member-1"),
            recurrenceFreq = "WEEKLY",
            recurrenceInterval = 2,
            recurrenceUntil = null
        )

        viewModel.updateEvent(event, draft)

        assertEquals(event, repository.updatedEvent)
        assertEquals(draft, repository.updatedDraft)
    }

    @Test
    fun navigationChangesReferenceDate() = runBlocking {
        val viewModel = CalendarViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = FakeCalendarRepository(),
            syncScheduler = FakeSyncScheduler(),
            membersRepository = membersRepository(),
            householdEvents = EmptyHouseholdEvents
        )

        viewModel.goToNext()
        assertEquals(LocalDate.now().plusMonths(1), viewModel.referenceDate.value)

        viewModel.setViewMode(CalendarViewMode.DAY)
        viewModel.goToPrevious()
        assertEquals(LocalDate.now().plusMonths(1).minusDays(1), viewModel.referenceDate.value)
    }

    @Test
    fun visibleRangeCoversWholeMonth() {
        val (from, to) = CalendarViewModel.rangeFor(CalendarViewMode.MONTH, LocalDate.of(2026, 9, 15))
        assertEquals("2026-09-01", from)
        assertEquals("2026-09-30", to)
    }

    @Test
    fun visibleRangeCoversWholeWeekStartingMonday() {
        val (from, to) = CalendarViewModel.rangeFor(CalendarViewMode.WEEK, LocalDate.of(2026, 9, 12))
        assertEquals("2026-09-07", from)
        assertEquals("2026-09-13", to)
    }

    @Test
    fun visibleRangeForDayIsSingleDate() {
        val (from, to) = CalendarViewModel.rangeFor(CalendarViewMode.DAY, LocalDate.of(2026, 9, 12))
        assertEquals("2026-09-12", from)
        assertEquals("2026-09-12", to)
    }

    @Test
    fun noSyncWithoutHousehold() = runBlocking {
        val scheduler = FakeSyncScheduler()
        val viewModel = CalendarViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = FakeCalendarRepository(),
            syncScheduler = scheduler,
            membersRepository = membersRepository(),
            householdEvents = EmptyHouseholdEvents
        )

        viewModel.syncNow()
        assertNull(scheduler.immediateHouseholdId)
    }
}

private class FakeCalendarRepository : CalendarRepositoryContract {
    var createdHouseholdId: String? = null
    var createdDraft: CalendarDraft? = null
    var deletedEvent: CalendarEventEntity? = null
    var updatedEvent: CalendarEventEntity? = null
    var updatedDraft: CalendarDraft? = null

    override fun observeEvents(householdId: String, from: String, to: String): Flow<List<CalendarEventEntity>> = flowOf(emptyList())

    override fun lastSuccessfulSync(householdId: String): Flow<Long> = flowOf(0L)

    override suspend fun createEvent(draft: CalendarDraft, householdId: String) {
        createdDraft = draft
        createdHouseholdId = householdId
    }

    override suspend fun updateEvent(event: CalendarEventEntity, draft: CalendarDraft) {
        updatedEvent = event
        updatedDraft = draft
    }

    override suspend fun deleteEvent(event: CalendarEventEntity) {
        deletedEvent = event
    }

    override suspend fun fullSync(householdId: String, from: String, to: String) = Unit
}
