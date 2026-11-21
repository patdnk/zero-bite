package com.mazesys.zerobite.screens.scan

import android.net.Uri
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.ScanRoute
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.scanning.UnreadablePhoto
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.english
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class ScanFlowViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var finished = 0
    private val coordinator = ScanCoordinator(onFinished = { finished++ })
    private val labels = FakeLabels()
    private val vm by lazy { ScanFlowViewModel(coordinator, supportsLiveScanning = true, scanner = labels, clock = TestClock.fixed()) }
    private val photo = Uri.parse("content://media/picker/0/1")
    private val milk = ScanResult(lines = listOf("SEMI SKIMMED MILK"), barcode = "5000119000000")

    private fun failure() = assertIs<ScanPhase.Failed>(vm.phase.value).message.english()

    @Test
    fun `a readable photo goes to review`() {
        labels.result = milk
        vm.process(photo)
        assertEquals(ScanPhase.Idle, vm.phase.value)
        assertEquals(ScanRoute.Review(milk), coordinator.backStack.last())
    }

    @Test
    fun `empty photo explains what to try`() {
        vm.process(photo)
        assertEquals("No text or barcode was found. Try a sharper, closer photo of the label.", failure())
        assertEquals(listOf<ScanRoute>(ScanRoute.Choose), coordinator.backStack.toList())
    }

    @Test
    fun `unreadable photo says so`() {
        labels.failure = UnreadablePhoto()
        vm.process(photo)
        assertEquals("That file doesn't look like a photo.", failure())
    }

    @Test
    fun `a photo that can't be opened says so`() {
        labels.failure = IOException("boom")
        vm.process(photo)
        assertEquals("Couldn't load that photo.", failure())
        vm.dismissError()
        assertEquals(ScanPhase.Idle, vm.phase.value)
    }

    @Test
    fun `any other failure never shows the raw exception text`() {
        labels.failure = IllegalStateException("MlKitException: internal 13")
        vm.process(photo)
        assertEquals("Couldn't load that photo.", failure())
    }

    @Test
    fun `typing it in opens a fresh form, live scan opens the scanner`() {
        vm.enterManually()
        val form = assertIs<ScanRoute.Form>(coordinator.backStack.last())
        assertEquals(TestClock.today.plus(7, DateTimeUnit.DAY), form.draft.expiresOn)
        coordinator.pop()
        vm.startLiveScan()
        assertEquals(ScanRoute.Live, coordinator.backStack.last())
        vm.liveScanFinished(milk)
        assertEquals(ScanRoute.Review(milk), coordinator.backStack.last())
    }

    @Test
    fun `pop stops at the first screen, cancel closes the sheet`() {
        coordinator.pop()
        assertEquals(1, coordinator.backStack.size)
        vm.cancel()
        assertEquals(1, finished)
    }
}
