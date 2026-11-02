package com.thalzeronlabs.shelf.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs [ShelfSyncer] in the background. A dropped connection or a server error is a [Result.retry], which
 * WorkManager reschedules with exponential backoff; a client error (4xx) will not fix itself and fails.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: ShelfSyncer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        syncer.sync()
        Result.success()
    } catch (e: IOException) {
        retryOrGiveUp()
    } catch (e: HttpException) {
        if (e.code() >= 500 || e.code() == 429) retryOrGiveUp() else Result.failure()
    }

    /** Backoff already spaces the attempts out; after [MAX_ATTEMPTS] the periodic sync tries again later. */
    private fun retryOrGiveUp() = if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        const val MAX_ATTEMPTS = 6
    }
}

/** Asks for a shelf sync. An interface so the repository can be tested without WorkManager. */
fun interface SyncRequester {
    fun requestSync()
}

/**
 * Schedules [SyncWorker]: once soon after every change to the shelf, and every [PERIOD_HOURS] hours.
 * Both only run with a network connection and back off exponentially when they fail.
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncRequester {

    private val workManager get() = WorkManager.getInstance(context)

    /** Replaces a pending one-off sync: the sync reads the shelf when it runs, so one run covers every change. */
    override fun requestSync() {
        workManager.enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.REPLACE, oneTimeRequest())
    }

    /** Idempotent: an already scheduled periodic sync keeps its schedule. */
    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodicRequest())
    }

    companion object {
        const val SYNC_NOW = "shelf-sync"
        const val SYNC_PERIODIC = "shelf-sync-periodic"
        const val PERIOD_HOURS = 12L
        const val BACKOFF_SECONDS = 30L

        val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        fun periodicRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
