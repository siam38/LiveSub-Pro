package com.charles.livecaptionn.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.LiveCaptionApp
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!BuildConfig.GITHUB_SELF_UPDATE_ENABLED) return Result.success()
        val checker = UpdateChecker()
        val info = checker.check() ?: return Result.success()
        val ui = (applicationContext as? LiveCaptionApp)?.container?.uiLocalization
        UpdateNotifier(
            applicationContext,
            uiStringsProvider = { ui?.latestStrings() ?: com.charles.livecaptionn.ui.l10n.UiStrings.EMPTY }
        ).notifyIfNew(info)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "livecaption_update_check"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!BuildConfig.GITHUB_SELF_UPDATE_ENABLED) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                12, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
