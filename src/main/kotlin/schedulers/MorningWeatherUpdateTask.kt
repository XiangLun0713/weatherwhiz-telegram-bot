package schedulers

class MorningWeatherUpdateTask(private val callback: Callback) : DailyTask {
    override fun execute() {
        callback.onTimeForMorningTask()
    }

    interface Callback {
        fun onTimeForMorningTask()
    }
}