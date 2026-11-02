package com.thalzeronlabs.shelf.feature.shelf

import app.cash.turbine.test
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.testing.FakeShelfRepository
import com.thalzeronlabs.shelf.testing.book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the shelf is grouped by status in a fixed order, empty groups hidden`() = runTest(dispatcher) {
        val repo = FakeShelfRepository()
        repo.shelf.value = listOf(book("a", ReadingStatus.DONE), book("b", ReadingStatus.WANT), book("c", ReadingStatus.DONE))
        val vm = ShelfViewModel(repo)
        vm.state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(listOf(ReadingStatus.WANT, ReadingStatus.DONE), state.sections.map { it.first })
            assertEquals(3, state.total)
            assertEquals(2, state.count(ReadingStatus.DONE))
            assertEquals(0, state.count(ReadingStatus.READING))
        }
    }

    @Test
    fun `moving and removing books updates the shelf`() = runTest(dispatcher) {
        val repo = FakeShelfRepository()
        repo.shelf.value = listOf(book("a", ReadingStatus.WANT))
        val vm = ShelfViewModel(repo)
        vm.state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            vm.setStatus(state.sections.single().second.single(), ReadingStatus.READING)
            advanceUntilIdle()
            assertEquals(ReadingStatus.READING, awaitItem().sections.single().first)
            vm.remove(book("a"))
            advanceUntilIdle()
            assertEquals(0, awaitItem().total)
        }
    }
}
