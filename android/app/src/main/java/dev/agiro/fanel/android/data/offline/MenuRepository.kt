package dev.agiro.fanel.android.data.offline

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.agiro.fanel.android.data.local.CachedSnapshotDao
import dev.agiro.fanel.android.data.local.PendingOperationDao
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import dev.agiro.fanel.android.data.remote.MealPlanDto
import dev.agiro.fanel.android.data.remote.MealSlotDto
import dev.agiro.fanel.android.data.remote.MealType
import dev.agiro.fanel.android.data.remote.MenuApi
import dev.agiro.fanel.android.data.remote.SetSlotRequest
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs

/**
 * Weekly meal plans cached per ISO week. Only the weeks the user has visited are cached; weeks far
 * from the current one are evicted on sync to keep the cache bounded.
 */
class MenuRepository(
    private val api: MenuApi,
    snapshotDao: CachedSnapshotDao,
    pendingDao: PendingOperationDao,
    pusher: OutboxPusher,
    gson: Gson = Gson(),
    private val today: () -> LocalDate = LocalDate::now
) : SnapshotRepository<MealPlanDto>(
    DOMAIN, snapshotDao, pendingDao, pusher, gson, object : TypeToken<MealPlanDto>() {}
) {

    data class SetSlotOperation(val year: Int, val week: Int, val request: SetSlotRequest)
    data class ClearSlotOperation(val year: Int, val week: Int, val dayOfWeek: Int, val mealType: MealType)

    fun observeWeek(householdId: String, year: Int, week: Int): Flow<MealPlanDto?> =
        observe(householdId, weekKey(year, week))

    suspend fun syncWeek(householdId: String, year: Int, week: Int) {
        sync(householdId)
        pullWeek(householdId, year, week)
    }

    suspend fun setSlot(householdId: String, year: Int, week: Int, request: SetSlotRequest) {
        val slot = MealSlotDto(
            id = newLocalId(),
            dayOfWeek = request.dayOfWeek,
            mealType = request.mealType,
            text = request.text,
            recipeId = request.recipeId
        )
        mutateWeek(householdId, year, week) { plan ->
            val others = plan.slots.orEmpty().filterNot { it.dayOfWeek == slot.dayOfWeek && it.mealType == slot.mealType }
            plan.copy(slots = others + slot)
        }
        enqueue(householdId, OP_SET_SLOT, null, SetSlotOperation(year, week, request))
    }

    suspend fun clearSlot(householdId: String, year: Int, week: Int, dayOfWeek: Int, mealType: MealType) {
        mutateWeek(householdId, year, week) { plan ->
            plan.copy(slots = plan.slots.orEmpty().filterNot { it.dayOfWeek == dayOfWeek && it.mealType == mealType })
        }
        enqueue(householdId, OP_CLEAR_SLOT, null, ClearSlotOperation(year, week, dayOfWeek, mealType))
    }

    override suspend fun pull(householdId: String) {
        val current = today()
        val currentYear = current.get(IsoFields.WEEK_BASED_YEAR)
        val currentWeek = current.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val keys = cachedKeys(householdId).ifEmpty { listOf(weekKey(currentYear, currentWeek)) }
        for (key in keys) {
            val (year, week) = parseWeekKey(key) ?: continue
            val distance = abs((year - currentYear) * 52 + (week - currentWeek))
            if (distance > MAX_WEEK_DISTANCE) evict(householdId, key) else pullWeek(householdId, year, week)
        }
    }

    override suspend fun execute(operation: PendingOperationEntity): IdRemap? {
        val householdId = operation.householdId
        when (operation.type) {
            OP_SET_SLOT -> {
                val op = payload<SetSlotOperation>(operation)
                val slot = api.setSlot(householdId, op.year, op.week, op.request)
                mutateWeek(householdId, op.year, op.week) { plan ->
                    val others = plan.slots.orEmpty().filterNot { it.dayOfWeek == slot.dayOfWeek && it.mealType == slot.mealType }
                    plan.copy(slots = others + slot)
                }
            }
            OP_CLEAR_SLOT -> {
                val op = payload<ClearSlotOperation>(operation)
                api.clearSlot(householdId, op.year, op.week, op.dayOfWeek, op.mealType)
            }
        }
        return null
    }

    private suspend fun pullWeek(householdId: String, year: Int, week: Int) {
        val key = weekKey(year, week)
        val remote = api.getWeek(householdId, year, week)
        val remoteIds = remote.slots.orEmpty().mapTo(HashSet()) { it.id }
        val unsynced = cached(householdId, key)?.slots.orEmpty()
            .filter { isLocalId(it.id) && it.id !in remoteIds }
        write(householdId, remote.copy(slots = remote.slots.orEmpty() + unsynced), key)
    }

    private suspend fun mutateWeek(householdId: String, year: Int, week: Int, transform: (MealPlanDto) -> MealPlanDto) {
        mutate(
            householdId,
            weekKey(year, week),
            empty = { MealPlanDto(id = null, householdId = householdId, isoYear = year, isoWeek = week, slots = emptyList()) },
            transform = transform
        )
    }

    companion object {
        const val DOMAIN = "menu"
        const val OP_SET_SLOT = "SET_SLOT"
        const val OP_CLEAR_SLOT = "CLEAR_SLOT"
        private const val MAX_WEEK_DISTANCE = 8

        fun weekKey(year: Int, week: Int): String = "$year-W$week"

        private fun parseWeekKey(key: String): Pair<Int, Int>? {
            val (year, week) = key.split("-W").takeIf { it.size == 2 } ?: return null
            return Pair(year.toIntOrNull() ?: return null, week.toIntOrNull() ?: return null)
        }
    }
}
