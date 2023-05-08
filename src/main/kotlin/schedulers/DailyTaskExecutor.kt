package schedulers

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class DailyTaskExecutor(
    private val dailyTask: DailyTask
) {
    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    fun startExecutionAt(hour: Int, minute: Int, second: Int) {
        val taskWrapper = Runnable {
            // execute scheduled task
            dailyTask.execute()
            // set up next execution
            startExecutionAt(hour, minute, second)
        }
        // calculate delay in seconds before next execution
        val delay: Long = computeNextDelay(hour, minute, second)
        // schedule next execution
        executorService.schedule(taskWrapper, delay, TimeUnit.SECONDS)
    }

    private fun computeNextDelay(hour: Int, minute: Int, second: Int): Long {
        val localNow = LocalDateTime.now()
        val currentZone: ZoneId = ZoneId.systemDefault()
        val zonedNow: ZonedDateTime = ZonedDateTime.of(localNow, currentZone)
        var zonedNextTarget: ZonedDateTime = zonedNow.withHour(hour).withMinute(minute).withSecond(second)
        if (zonedNow >= zonedNextTarget) {
            zonedNextTarget = zonedNextTarget.plusDays(1)
        }
        val duration: Duration = Duration.between(zonedNow, zonedNextTarget)
        return duration.seconds
    }

    fun stop() {
        executorService.shutdown()
        try {
            executorService.awaitTermination(1, TimeUnit.DAYS)
        } catch (e: InterruptedException) {
            Logger.getLogger(DailyTaskExecutor::class.java.name)
                .warning("Interrupted while waiting for executor to terminate")
        }
    }
}