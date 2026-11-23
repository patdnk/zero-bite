package com.mazesys.zerobite.coordination

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import com.mazesys.zerobite.core.model.DateCandidate
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.products.ProductLookup
import com.mazesys.zerobite.scanning.PhotoScanner
import com.mazesys.zerobite.scanning.UnreadablePhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

sealed interface ScanRoute : NavKey {
    /** Pick a source: camera, photo or typing. */
    data object Choose : ScanRoute
    data object Live : ScanRoute
    /** What was read lives in the coordinator's state.review. */
    data object Review : ScanRoute
    data class Form(val draft: ItemDraft) : ScanRoute
}

/**
 * The add-food sheet: choose a source → (live scan | read a photo) → review what was read (with a
 * barcode lookup) → item form → finished. MainCoordinator starts one per scan sheet; it reports
 * back with DidFinishScan.
 */
class ScanCoordinator(
    private val parent: MainCoordinator?,
    val supportsLiveScanning: Boolean,
    private val scanner: PhotoScanner,
    private val productLookup: ProductLookup,
    scope: CoroutineScope,
) : EventDrivenCoordinator<ScanCoordinator.State, ScanCoordinator.Event, ScanCoordinator.Command>(State(), ScanCoordinator, scope) {

    data class State(
        val backStack: List<ScanRoute> = listOf(ScanRoute.Choose),
        val status: Status = Status.Idle,
        /** What was read, and the user's edits to it. Kept after review is popped so it can animate out; the next read replaces it. */
        val review: Review? = null,
        /** Saved, cancelled or dismissed. Every later event is ignored. */
        val isFinished: Boolean = false,
    )

    /** Reading a chosen photo. Processing and an error can't both be true. */
    sealed interface Status {
        data object Idle : Status
        data object Processing : Status
        data class Failed(val failure: Failure) : Status
    }

    /** Why a photo gave nothing. The screen maps it to copy; the raw error text is never shown. */
    enum class Failure { NOTHING_FOUND, UNREADABLE, LOAD_FAILED }

    data class Review(
        val result: ScanResult,
        val name: String,
        val category: FoodCategory = FoodCategory.OTHER,
        /** Index into `candidates`; null for "None of these". */
        val selectedIndex: Int? = null,
        val product: ProductInfo? = null,
        val isLookingUp: Boolean = false,
        val showsLines: Boolean = false,
    ) {
        val candidates: List<DateCandidate> get() = result.candidates
        val lines: List<String> get() = result.lines
        val barcode: String? get() = result.barcode
        val selectedCandidate: DateCandidate? get() = selectedIndex?.let { candidates.getOrNull(it) }
        val canContinue: Boolean get() = name.isNotBlank()

        fun makeDraft(today: LocalDate): ItemDraft = ItemDraft.new(today).copy(
            name = name.trim(),
            category = category,
            barcode = barcode,
            brand = product?.brand,
            expiresOn = selectedCandidate?.date ?: today.plus(category.defaultShelfLifeDays, DateTimeUnit.DAY),
        )

        companion object {
            /** Starts from the guessed name and the most likely date; looks up the barcode if there is one. */
            fun of(result: ScanResult) = Review(
                result,
                name = result.suggestedName ?: "",
                selectedIndex = if (result.candidates.isEmpty()) null else 0,
                isLookingUp = result.barcode != null,
            )
        }
    }

    sealed interface Event {
        // Choosing a source
        data object DidTapLiveScan : Event
        data class DidTapManualEntry(val today: LocalDate) : Event
        data class DidChoosePhoto(val photo: Uri, val today: LocalDate) : Event
        data class DidReadLabel(val result: ScanResult) : Event
        data class DidFailToReadLabel(val failure: Failure) : Event
        data object DidDismissError : Event
        data class DidFinishLiveScan(val result: ScanResult) : Event

        // Reviewing what was read
        data class DidChangeName(val name: String) : Event
        data class DidChangeCategory(val category: FoodCategory) : Event
        /** null means "None of these". */
        data class DidSelectCandidate(val index: Int?) : Event
        data object DidToggleLines : Event
        data class DidLookUpProduct(val info: ProductInfo?) : Event
        data class DidTapContinue(val today: LocalDate) : Event

        // Navigation and lifecycle
        /** Back button, gesture or the system back. Never pops the first screen. */
        data object DidTapBack : Event
        data object DidSaveItem : Event
        data object DidTapClose : Event
        /** The sheet went away without DidTapClose (system back on the first screen, or Main closed it). */
        data object DidDismiss : Event
    }

    sealed interface Command {
        data class WillReadLabel(val photo: Uri, val today: LocalDate) : Command
        data object WillCancelReadLabel : Command
        data class WillLookUpProduct(val barcode: String) : Command
        data object WillCancelLookUp : Command
        /** Tell the parent to close the sheet. */
        data object WillFinish : Command
    }

    private enum class TaskId { READ_LABEL, LOOK_UP }

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> {
            if (state.isFinished) return Step(state)
            val processing = state.status == Status.Processing

            return when (event) {
                Event.DidTapLiveScan ->
                    if (processing) Step(state) else Step(state.copy(backStack = push(state.backStack, ScanRoute.Live)))

                is Event.DidTapManualEntry ->
                    if (processing) Step(state) else Step(state.copy(backStack = push(state.backStack, ScanRoute.Form(ItemDraft.new(event.today)))))

                is Event.DidChoosePhoto ->
                    if (processing) Step(state) else Step(state.copy(status = Status.Processing), listOf(Command.WillReadLabel(event.photo, event.today)))

                is Event.DidReadLabel -> when {
                    !processing -> Step(state) // stale result
                    event.result.isEmpty -> Step(state.copy(status = Status.Failed(Failure.NOTHING_FOUND)))
                    else -> startReview(state.copy(status = Status.Idle), event.result)
                }

                is Event.DidFailToReadLabel ->
                    if (processing) Step(state.copy(status = Status.Failed(event.failure))) else Step(state)

                Event.DidDismissError ->
                    if (state.status is Status.Failed) Step(state.copy(status = Status.Idle)) else Step(state)

                is Event.DidFinishLiveScan ->
                    if (state.backStack.last() != ScanRoute.Live || event.result.isEmpty) Step(state) else startReview(state, event.result)

                is Event.DidChangeName -> editReview(state) { it.copy(name = event.name) }
                is Event.DidChangeCategory -> editReview(state) { it.copy(category = event.category) }
                is Event.DidSelectCandidate -> editReview(state) { it.copy(selectedIndex = event.index) }
                Event.DidToggleLines -> editReview(state) { it.copy(showsLines = !it.showsLines) }

                is Event.DidLookUpProduct -> {
                    val review = state.review
                    if (review == null || !review.isLookingUp) {
                        Step(state) // left review, or already done
                    } else {
                        val info = event.info
                        Step(
                            state.copy(
                                review = if (info == null) {
                                    review.copy(isLookingUp = false)
                                } else {
                                    review.copy(
                                        isLookingUp = false,
                                        product = info,
                                        // A catalogue name beats an OCR guess; keep anything the user has already typed.
                                        name = if (review.name.isEmpty() || review.name == review.result.suggestedName) info.name else review.name,
                                        category = info.category ?: review.category,
                                    )
                                },
                            ),
                        )
                    }
                }

                is Event.DidTapContinue -> {
                    val review = state.review
                    if (review == null || !review.canContinue || state.backStack.last() != ScanRoute.Review) {
                        Step(state)
                    } else {
                        Step(state.copy(backStack = push(state.backStack, ScanRoute.Form(review.makeDraft(event.today)))))
                    }
                }

                Event.DidTapBack -> {
                    val backStack = pop(state.backStack)
                    val review = state.review
                    // Left review mid-lookup: stop it. A late result is then ignored (isLookingUp is false).
                    if (ScanRoute.Review !in backStack && review != null && review.isLookingUp) {
                        Step(state.copy(backStack = backStack, review = review.copy(isLookingUp = false)), listOf(Command.WillCancelLookUp))
                    } else {
                        Step(state.copy(backStack = backStack))
                    }
                }

                Event.DidSaveItem, Event.DidTapClose ->
                    Step(state.copy(isFinished = true), listOf(Command.WillCancelReadLabel, Command.WillCancelLookUp, Command.WillFinish))

                Event.DidDismiss ->
                    Step(state.copy(isFinished = true), listOf(Command.WillCancelReadLabel, Command.WillCancelLookUp))
            }
        }

        private fun startReview(state: State, result: ScanResult): Step<State, Command> {
            val review = Review.of(result)
            return Step(
                state.copy(review = review, backStack = push(state.backStack, ScanRoute.Review)),
                listOfNotNull(review.barcode?.let { Command.WillLookUpProduct(it) }),
            )
        }

        private fun editReview(state: State, edit: (Review) -> Review): Step<State, Command> =
            state.review?.let { Step(state.copy(review = edit(it))) } ?: Step(state)

        /** A double tap must not stack the same screen twice. */
        private fun push(backStack: List<ScanRoute>, route: ScanRoute) = if (backStack.last() == route) backStack else backStack + route
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        is Command.WillReadLabel -> CommandTask.Run(TaskId.READ_LABEL) {
            try {
                Event.DidReadLabel(scanner.scan(command.photo, command.today))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnreadablePhoto) {
                Event.DidFailToReadLabel(Failure.UNREADABLE)
            } catch (e: Exception) {
                // Whatever went wrong (I/O, recogniser), the user's next step is the same.
                Event.DidFailToReadLabel(Failure.LOAD_FAILED)
            }
        }

        Command.WillCancelReadLabel -> CommandTask.Cancel(TaskId.READ_LABEL)

        is Command.WillLookUpProduct -> CommandTask.Run(TaskId.LOOK_UP) {
            val info = try {
                productLookup.lookup(command.barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // Offline, timed out or an unreadable reply: keep what was read.
            }
            Event.DidLookUpProduct(info)
        }

        Command.WillCancelLookUp -> CommandTask.Cancel(TaskId.LOOK_UP)

        Command.WillFinish -> CommandTask.Perform { parent?.eventLoop(MainCoordinator.Event.DidFinishScan) }
    }
}
