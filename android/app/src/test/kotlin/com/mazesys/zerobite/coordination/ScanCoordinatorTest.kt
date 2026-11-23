package com.mazesys.zerobite.coordination

import android.net.Uri
import com.mazesys.zerobite.coordination.ScanCoordinator.Command
import com.mazesys.zerobite.coordination.ScanCoordinator.Event
import com.mazesys.zerobite.coordination.ScanCoordinator.Failure
import com.mazesys.zerobite.coordination.ScanCoordinator.State
import com.mazesys.zerobite.coordination.ScanCoordinator.Status
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.scanning.LabelScanner
import com.mazesys.zerobite.scanning.PhotoScanner
import com.mazesys.zerobite.scanning.UnreadablePhoto
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.testDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val today = TestClock.today

private fun labelResult(barcode: String? = "5000000000000"): ScanResult =
    LabelScanner.analyse(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "PACKED 01/09/2026"), barcode, today)

private val milk = ProductInfo("5000000000000", "Organic Semi Skimmed Milk", "Yeo", FoodCategory.DAIRY)

/** Robolectric for Uri. */
@RunWith(RobolectricTestRunner::class)
class ScanStateMachineTest {

    private val photo = Uri.parse("content://media/picker/0/1")

    /** The state after [events], and the commands the last one produced. */
    private fun run(vararg events: Event, from: State = State()): Pair<State, List<Command>> {
        val replay = ScanCoordinator.replay(events.toList(), from)
        return replay.state to (replay.commands.lastOrNull() ?: emptyList())
    }

    @Test
    fun `choosing a photo starts reading it once`() {
        val (state, commands) = run(Event.DidChoosePhoto(photo, today))
        assertEquals(Status.Processing, state.status)
        assertEquals(listOf<Command>(Command.WillReadLabel(photo, today)), commands)

        assertTrue(run(Event.DidChoosePhoto(photo, today), from = state).second.isEmpty())
    }

    @Test
    fun `a read label opens review and looks up the barcode`() {
        val (state, commands) = run(Event.DidChoosePhoto(photo, today), Event.DidReadLabel(labelResult()))
        assertEquals(Status.Idle, state.status)
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Review), state.backStack)
        assertEquals("Semi Skimmed Milk", state.review?.name)
        assertEquals(true, state.review?.isLookingUp)
        assertEquals(listOf<Command>(Command.WillLookUpProduct("5000000000000")), commands)
    }

    @Test
    fun `no barcode means no lookup`() {
        val (state, commands) = run(Event.DidChoosePhoto(photo, today), Event.DidReadLabel(labelResult(barcode = null)))
        assertEquals(false, state.review?.isLookingUp)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `an empty read is a failure the user can dismiss`() {
        val (state, _) = run(Event.DidChoosePhoto(photo, today), Event.DidReadLabel(ScanResult()))
        assertEquals(Status.Failed(Failure.NOTHING_FOUND), state.status)
        assertEquals(listOf<ScanRoute>(ScanRoute.Choose), state.backStack)

        assertEquals(Status.Idle, run(Event.DidDismissError, from = state).first.status)
    }

    @Test
    fun `a result that arrives when not reading is ignored`() {
        val (state, commands) = run(Event.DidReadLabel(labelResult()))
        assertEquals(State(), state)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `live scan pushes review on top of the scanner`() {
        val (state, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()))
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Live, ScanRoute.Review), state.backStack)
    }

    @Test
    fun `barcode lookup fills name and category without overwriting user edits`() {
        val (filled, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidLookUpProduct(milk))
        assertEquals("Organic Semi Skimmed Milk", filled.review?.name)
        assertEquals(FoodCategory.DAIRY, filled.review?.category)
        assertEquals(false, filled.review?.isLookingUp)

        val (edited, _) = run(
            Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidChangeName("My milk"), Event.DidLookUpProduct(milk),
        )
        assertEquals("My milk", edited.review?.name)
        assertEquals(milk, edited.review?.product)
    }

    @Test
    fun `going back from review cancels the lookup`() {
        val (reviewing, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()))
        val (back, commands) = run(Event.DidTapBack, from = reviewing)
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Live), back.backStack)
        assertEquals(false, back.review?.isLookingUp)
        assertEquals(listOf<Command>(Command.WillCancelLookUp), commands)

        // Continue can't be sent from a screen that isn't review.
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Live), run(Event.DidTapContinue(today), from = back).first.backStack)

        // A lookup that finishes afterwards changes nothing.
        assertEquals(back, run(Event.DidLookUpProduct(milk), from = back).first)
    }

    @Test
    fun `continue uses the selected date or falls back to shelf life`() {
        val (state, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidTapContinue(today))
        val draft = assertIs<ScanRoute.Form>(state.backStack.last()).draft
        assertEquals(LocalDate(2026, 9, 12), draft.expiresOn)
        assertEquals("5000000000000", draft.barcode)

        val review = assertNotNull(
            run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidSelectCandidate(null), Event.DidChangeCategory(FoodCategory.DAIRY))
                .first.review,
        )
        assertEquals(today.plus(FoodCategory.DAIRY.defaultShelfLifeDays, DateTimeUnit.DAY), review.makeDraft(today).expiresOn)
    }

    @Test
    fun `continue needs a name`() {
        val (state, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidChangeName("  "), Event.DidTapContinue(today))
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Live, ScanRoute.Review), state.backStack)
    }

    @Test
    fun `the saved name is trimmed`() {
        val (state, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidChangeName("  Milk  "), Event.DidTapContinue(today))
        val form = assertIs<ScanRoute.Form>(state.backStack.last())
        assertEquals("Milk", form.draft.name)
    }

    @Test
    fun `closing cancels all work and ignores everything after`() {
        val (state, commands) = run(Event.DidChoosePhoto(photo, today), Event.DidTapClose)
        assertTrue(state.isFinished)
        assertEquals(listOf(Command.WillCancelReadLabel, Command.WillCancelLookUp, Command.WillFinish), commands)

        val (after, more) = run(Event.DidReadLabel(labelResult()), Event.DidTapLiveScan, from = state)
        assertEquals(state, after)
        assertTrue(more.isEmpty())
    }

    @Test
    fun `swiping the sheet away cancels work without closing again`() {
        val (_, commands) = run(Event.DidChoosePhoto(photo, today), Event.DidDismiss)
        assertEquals(listOf<Command>(Command.WillCancelReadLabel, Command.WillCancelLookUp), commands)
    }

    @Test
    fun `a double tap does not push the same screen twice`() {
        val (live, _) = run(Event.DidTapLiveScan, Event.DidTapLiveScan)
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Live), live.backStack)

        val (manual, _) = run(Event.DidTapManualEntry(today), Event.DidTapManualEntry(today))
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Form(ItemDraft.new(today))), manual.backStack)

        val (form, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidTapContinue(today), Event.DidTapContinue(today))
        assertEquals(4, form.backStack.size)
    }

    @Test
    fun `back never pops the first screen`() {
        val (state, commands) = run(Event.DidTapBack)
        assertEquals(listOf<ScanRoute>(ScanRoute.Choose), state.backStack)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `nothing starts while a photo is being read`() {
        val (state, _) = run(Event.DidChoosePhoto(photo, today), Event.DidTapLiveScan, Event.DidTapManualEntry(today))
        assertEquals(listOf<ScanRoute>(ScanRoute.Choose), state.backStack)
    }

    @Test
    fun `the read text can be shown and hidden`() {
        val (shown, _) = run(Event.DidTapLiveScan, Event.DidFinishLiveScan(labelResult()), Event.DidToggleLines)
        assertEquals(true, shown.review?.showsLines)
        assertEquals(false, run(Event.DidToggleLines, from = shown).first.review?.showsLines)
    }
}

/** The commands carried out, with fakes for the label reader and the barcode lookup. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScanCoordinatorTest {

    private val photo = Uri.parse("content://media/picker/0/1")

    private fun CoroutineScope.scan(scanner: PhotoScanner, lookup: FakeProductLookup = FakeProductLookup()) =
        ScanCoordinator(parent = null, supportsLiveScanning = false, scanner = scanner, productLookup = lookup, scope = this)

    /** A reader that never finishes and counts how often it was cancelled. */
    private class StuckScanner : PhotoScanner {
        var cancelled = 0
        override suspend fun scan(photo: Uri, today: LocalDate): ScanResult {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    @Test
    fun `a photo is read then the barcode is looked up`() = runTest(UnconfinedTestDispatcher()) {
        val lookup = FakeProductLookup(info = milk)
        val scan = backgroundScope.scan(FakeLabels(result = labelResult()), lookup)
        scan.eventLoop(Event.DidChoosePhoto(photo, today)).join()
        assertEquals(listOf(ScanRoute.Choose, ScanRoute.Review), scan.state.backStack)
        assertEquals("Organic Semi Skimmed Milk", scan.state.review?.name)
        assertEquals(false, scan.state.review?.isLookingUp)
        assertEquals(listOf("5000000000000"), lookup.asked)
    }

    @Test
    fun `a failed lookup keeps the scanned name`() = runTest(UnconfinedTestDispatcher()) {
        val scan = backgroundScope.scan(FakeLabels(result = labelResult()), FakeProductLookup(failure = IOException("offline")))
        scan.eventLoop(Event.DidChoosePhoto(photo, today)).join()
        assertEquals("Semi Skimmed Milk", scan.state.review?.name)
        assertNull(scan.state.review?.product)
        assertEquals(false, scan.state.review?.isLookingUp)
    }

    @Test
    fun `an unreadable photo fails`() = runTest(UnconfinedTestDispatcher()) {
        val scan = backgroundScope.scan(FakeLabels(failure = UnreadablePhoto()))
        scan.eventLoop(Event.DidChoosePhoto(photo, today)).join()
        assertEquals(Status.Failed(Failure.UNREADABLE), scan.state.status)
    }

    @Test
    fun `any other failure is a photo that could not be loaded`() = runTest(UnconfinedTestDispatcher()) {
        for (failure in listOf(IOException("boom"), IllegalStateException("MlKitException: internal 13"))) {
            val scan = backgroundScope.scan(FakeLabels(failure = failure))
            scan.eventLoop(Event.DidChoosePhoto(photo, today)).join()
            assertEquals(Status.Failed(Failure.LOAD_FAILED), scan.state.status)
        }
    }

    @Test
    fun `closing while reading stops the read and closes the sheet once`() = runTest(UnconfinedTestDispatcher()) {
        val scanner = StuckScanner()
        val main = MainCoordinator(parent = null, dependencies = testDependencies(labels = FakeLabels()), scope = backgroundScope)
        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        // As iOS's test: a scan reporting to Main, with a reader that hangs.
        val scan = ScanCoordinator(main, supportsLiveScanning = false, scanner = scanner, productLookup = FakeProductLookup(), scope = backgroundScope)

        val reading = scan.eventLoop(Event.DidChoosePhoto(photo, today))
        scan.eventLoop(Event.DidTapClose)
        scan.eventLoop(Event.DidDismiss)
        reading.join()

        assertNull(main.state.sheet)
        assertEquals(1, scanner.cancelled)
        assertNull(scan.state.review)
        assertEquals(1, main.history.count { it.event == MainCoordinator.Event.DidFinishScan })
        assertTrue(scan.history.last().wasIgnored)
    }

    @Test
    fun `swiping the sheet away stops the scan`() = runTest(UnconfinedTestDispatcher()) {
        val main = MainCoordinator(parent = null, dependencies = testDependencies(), scope = backgroundScope)
        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        val scan = assertNotNull(main.scan)

        main.eventLoop(MainCoordinator.Event.DidDismissSheet)

        assertNull(main.state.sheet)
        assertTrue(scan.state.isFinished)
        assertEquals(Event.DidDismiss, scan.history.last().event)
        assertSame(scan, main.scan) // kept, inert, while the sheet animates out
    }

    @Test
    fun `swiping the sheet away mid-read cancels the read in main's scan`() = runTest(UnconfinedTestDispatcher()) {
        val labels = FakeLabels(result = labelResult()).apply { hold = true }
        val main = MainCoordinator(parent = null, dependencies = testDependencies(labels = labels), scope = backgroundScope)
        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        val scan = assertNotNull(main.scan)

        val reading = scan.eventLoop(Event.DidChoosePhoto(photo, today))
        assertEquals(Status.Processing, scan.state.status)
        main.eventLoop(MainCoordinator.Event.DidDismissSheet)
        reading.join()

        assertEquals(1, labels.cancelled)
        assertNull(scan.state.review)
    }

    @Test
    fun `starting the next scan cancels the previous scan's scope`() = runTest(UnconfinedTestDispatcher()) {
        val main = MainCoordinator(parent = null, dependencies = testDependencies(), scope = backgroundScope)
        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        val firstScope = backgroundScope.coroutineContext.job.children.single()
        main.eventLoop(MainCoordinator.Event.DidDismissSheet)
        assertFalse(firstScope.isCancelled) // the stopped scan stays until the next one starts

        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        assertTrue(firstScope.isCancelled)
        assertEquals(1, backgroundScope.coroutineContext.job.children.count { it.isActive })
    }

    @Test
    fun `each scan sheet gets a fresh coordinator`() = runTest(UnconfinedTestDispatcher()) {
        val main = MainCoordinator(parent = null, dependencies = testDependencies(), scope = backgroundScope)
        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        val first = assertNotNull(main.scan)
        first.eventLoop(Event.DidTapLiveScan)
        first.eventLoop(Event.DidTapClose)
        assertNull(main.state.sheet)

        main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        val second = assertNotNull(main.scan)
        assertNotSame(first, second)
        assertEquals(State(), second.state)
        assertFalse(second.state.isFinished)
    }
}
