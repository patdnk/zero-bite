package com.mazesys.zerobite.testing

import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.notifications.PlannedNotification
import com.mazesys.zerobite.reminders.NotificationAccess
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.reminders.Notifier
import com.mazesys.zerobite.reminders.ReminderScheduler
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.core.time.FixedClock
import com.mazesys.zerobite.data.SampleData
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.time.Instant

/** Mon 7 Sep 2026, 09:00 in London. */
object TestClock {
    val zone: TimeZone = TimeZone.of("Europe/London")
    val now: Instant = Instant.parse("2026-09-07T08:00:00Z")
    val today: LocalDate = LocalDate(2026, 9, 7)
    fun fixed(): AppClock = FixedClock(now, zone)
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun next(): String = "$prefix-${++n}"
}

fun sampleFridge(): List<FoodItem> = SampleData.items(TestClock.today, TestClock.now, SequentialIds("sample")::next)

class FakeInventoryRepository(initial: List<FoodItem> = emptyList()) : InventoryRepository {
    private val state = MutableStateFlow(sorted(initial))
    val current: List<FoodItem> get() = state.value
    override val items: Flow<List<FoodItem>> = state

    override suspend fun item(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun add(item: FoodItem) { state.value = sorted(state.value + item) }
    override suspend fun update(item: FoodItem) { state.value = sorted(state.value.map { if (it.id == item.id) item else it }) }
    override suspend fun markConsumed(ids: List<String>, at: Instant) {
        state.value = state.value.map { if (it.id in ids) it.copy(consumedAt = at) else it }
    }
    override suspend fun restore(id: String) { state.value = state.value.map { if (it.id == id) it.copy(consumedAt = null) else it } }
    override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun deleteAll() { state.value = emptyList() }

    private fun sorted(items: List<FoodItem>) = items.sortedWith(compareBy<FoodItem> { it.expiresOn }.thenBy { it.name.lowercase() })
}

class FakeSettings(initial: SettingsSnapshot = SettingsSnapshot()) : SettingsRepository {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<SettingsSnapshot> = flow
    override suspend fun current() = flow.value
    override suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) { flow.value = transform(flow.value) }
}

class FakeRecipes(private val recipes: List<Recipe> = emptyList()) : RecipeRepository {
    override suspend fun recipes() = recipes
}

fun testDependencies(
    inventory: FakeInventoryRepository = FakeInventoryRepository(),
    settings: FakeSettings = FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)),
    recipes: FakeRecipes = FakeRecipes(),
    clock: AppClock = TestClock.fixed(),
    ids: IdGenerator = SequentialIds(),
    access: FakeAccess = FakeAccess(),
    scheduler: FakeScheduler = FakeScheduler(),
    notifier: FakeNotifier = FakeNotifier(),
) = AppDependencies(clock, settings, inventory, recipes, ids, CoroutineScope(Dispatchers.Unconfined), access, scheduler, notifier)

/** Runs `viewModelScope` work immediately on the test thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Four recipes whose ranking against [recipeFridge] is easy to reason about. */
fun testRecipes(): List<Recipe> {
    fun recipe(id: String, title: String, vararg ingredients: Pair<String, Boolean>) = Recipe(
        id = id, title = title, summary = "$title summary", prepMinutes = 15, servings = 2,
        tags = listOf("quick"), steps = listOf("Prepare", "Cook", "Serve"),
        ingredients = ingredients.map { (name, optional) -> Recipe.Ingredient(name, amount = "1", optional = optional) },
    )
    return listOf(
        recipe("omelette", "Omelette", "eggs" to false, "butter" to false, "salt" to false, "cheese" to true),
        recipe("stirfry", "Chicken stir-fry", "chicken" to false, "bell pepper" to false, "soy sauce" to false),
        recipe("pancakes", "Pancakes", "flour" to false, "eggs" to false, "milk" to false, "blueberries" to false),
        recipe("salad", "Big salad", "lettuce" to false, "tomatoes" to false, "cucumber" to false, "olive oil" to false),
    )
}

/** Eggs expiring tomorrow, butter and chicken: omelette ready, stir-fry and pancakes nearly, salad far off. */
fun recipeFridge(): List<FoodItem> {
    fun item(id: String, name: String, days: Int) =
        FoodItem(id = id, name = name, addedAt = TestClock.now, expiresOn = TestClock.today.plus(days, DateTimeUnit.DAY))
    return listOf(item("eggs", "Eggs", 1), item("butter", "Butter", 20), item("chicken", "Chicken breast", 5))
}

class FakeAccess(var current: NotificationPermission = NotificationPermission.GRANTED) : NotificationAccess {
    var asked = false
    override fun permission() = current
    override fun markAsked() {
        asked = true
    }
}

class FakeScheduler : ReminderScheduler {
    var last: List<PlannedNotification> = emptyList()
    var calls = 0
    override fun reschedule(planned: List<PlannedNotification>) {
        last = planned
        calls++
    }
}

class FakeNotifier : Notifier {
    var tests = 0
    override fun sendTest() {
        tests++
    }
}
