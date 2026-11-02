package com.thalzeronlabs.shelf.data.sync

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.thalzeronlabs.shelf.data.FakeApi
import com.thalzeronlabs.shelf.data.TestClock
import com.thalzeronlabs.shelf.data.httpError
import com.thalzeronlabs.shelf.data.inMemoryDatabase
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** The worker's retry decisions, and how the scheduler hands it to WorkManager (network constraint, backoff). */
@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDatabase()
    private val api = FakeApi()
    private val syncer = ShelfSyncer(db, api, TestClock())

    /** What HiltWorkerFactory does in the app: builds the worker with its dependencies. */
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            SyncWorker(appContext, workerParameters, syncer)
    }

    @After fun tearDown() = db.close()

    private suspend fun run(attempt: Int = 0): Result {
        db.books().upsert(BookEntity("OL1W", "Dune", "Frank Herbert", 1965, null, ReadingStatus.WANT, 1))
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(attempt)
            .build()
            .doWork()
    }

    @Test
    fun `a finished sync is a success`() = runTest {
        assertEquals(Result.success(), run())
    }

    @Test
    fun `a dropped connection is retried`() = runTest {
        api.goOffline()
        assertEquals(Result.retry(), run())
    }

    @Test
    fun `server errors are retried, client errors are not`() = runTest {
        api.goOffline(httpError(503))
        assertEquals(Result.retry(), run())
        api.goOffline(httpError(429))
        assertEquals(Result.retry(), run())
        api.goOffline(httpError(400))
        assertEquals(Result.failure(), run())
    }

    @Test
    fun `after the last attempt it gives up until the next periodic sync`() = runTest {
        api.goOffline()
        assertEquals(Result.retry(), run(attempt = SyncWorker.MAX_ATTEMPTS - 2))
        assertEquals(Result.failure(), run(attempt = SyncWorker.MAX_ATTEMPTS - 1))
    }

    @Test
    fun `both requests need a network and back off exponentially`() {
        for (spec in listOf(SyncScheduler.oneTimeRequest().workSpec, SyncScheduler.periodicRequest().workSpec)) {
            assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
            assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
            assertEquals(TimeUnit.SECONDS.toMillis(SyncScheduler.BACKOFF_SECONDS), spec.backoffDelayDuration)
        }
        assertEquals(TimeUnit.HOURS.toMillis(SyncScheduler.PERIOD_HOURS), SyncScheduler.periodicRequest().workSpec.intervalDuration)
    }

    @Test
    fun `a requested sync waits for the network, then runs`() {
        val workManager = testWorkManager()
        SyncScheduler(context).requestSync()

        val waiting = workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_NOW).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, waiting.state) // no network yet: it does not run
        assertEquals(NetworkType.CONNECTED, waiting.constraints.requiredNetworkType)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(waiting.id)
        // A CoroutineWorker runs on Dispatchers.Default even with the synchronous test executor.
        val done = runBlocking {
            withTimeout(10_000) { workManager.getWorkInfoByIdFlow(waiting.id).first { it?.state?.isFinished == true } }
        }
        assertEquals(WorkInfo.State.SUCCEEDED, done!!.state)
    }

    @Test
    fun `asking again replaces the pending sync, and the periodic sync is scheduled once`() {
        val workManager = testWorkManager()
        val scheduler = SyncScheduler(context)
        scheduler.requestSync()
        scheduler.requestSync()
        val pending = workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_NOW).get()
        assertEquals(1, pending.count { it.state == WorkInfo.State.ENQUEUED })

        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()
        val periodic = workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_PERIODIC).get()
        assertEquals(1, periodic.size)
        assertNotNull(periodic.single().periodicityInfo)
    }

    private fun testWorkManager(): WorkManager {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        return WorkManager.getInstance(context)
    }
}
