package com.smarttoolfactory.tutorial2_1unit_testing.coroutines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesTest3 {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    @Test
    fun testHotFakeRepository() = runTest {
        val fakeRepository = FakeRepository()
        val viewModel = MyViewModel(fakeRepository)

        assertEquals(0, viewModel.score.value) // Assert on the initial value

        // Start collecting values from the Repository
        viewModel.initialize()

        // Then we can send in values one by one, which the ViewModel will collect
        fakeRepository.emit(1)
        assertEquals(1, viewModel.score.value)

        fakeRepository.emit(2)
        fakeRepository.emit(3)
        assertEquals(3, viewModel.score.value) // Assert on the latest value
    }

    class CustomViewModel : ViewModel() {

        val flow = MutableSharedFlow<Int>()

        val stateFlow = (flow as Flow<Int>).stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    }

    @Test
    fun stateInTest() = runTest {

        val customViewModel = CustomViewModel()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            customViewModel.stateFlow.collect {}
        }

        // 🔥 Fails after 10s timeout
//        launch(UnconfinedTestDispatcher(testScheduler)) {
//            customViewModel.stateFlow.collect {}
//        }

        assertEquals(0, customViewModel.stateFlow.value) // Can assert initial value

        // Trigger-assert like before
        customViewModel.flow.emit(1)
        assertEquals(1, customViewModel.stateFlow.value)

    }

    @Test
    fun testLazilySharingViewModel() = runTest {
        val fakeRepository = FakeRepository()
        val viewModel = MyViewModelWithStateIn(fakeRepository)

        // Create an empty collector for the StateFlow
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.score.collect {}
        }

        // 🔥 Times out after 10 seconds
//        launch(UnconfinedTestDispatcher(testScheduler)) {
//            viewModel.score.collect {}
//        }

        assertEquals(0, viewModel.score.value) // Can assert initial value

        // Trigger-assert like before
        fakeRepository.emit(1)
        assertEquals(1, viewModel.score.value)

        fakeRepository.emit(2)
        fakeRepository.emit(3)
        assertEquals(3, viewModel.score.value)
    }

}

/**
 * Caution: When testing a StateFlow created with such options,
 * there must be at least one collector present during the test. Otherwise the stateIn
 * operator doesn't start collecting the underlying flow,
 * and the StateFlow's value will never be updated.
 */
class MyViewModelWithStateIn(myRepository: MyRepository) : ViewModel() {
    val score: StateFlow<Int> = myRepository.scores()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )
}

class MyViewModel(private val myRepository: MyRepository) : ViewModel() {
    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            myRepository.scores().collect { score ->
                _score.value = score
            }
        }
    }
}

class FakeRepository : MyRepository {
    private val flow = MutableSharedFlow<Int>()
    suspend fun emit(value: Int) = flow.emit(value)
    override fun scores(): Flow<Int> = flow
}

interface MyRepository {
    fun scores(): Flow<Int>
}