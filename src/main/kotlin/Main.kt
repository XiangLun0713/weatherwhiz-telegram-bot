import bot.WeatherWhizBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import services.WeatherService


fun main() {
    println("Starting bot")
    // create WeatherServiceImpl instance
    val weatherService = WeatherService.create()

    try {
        // use default bot session
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        // register bot
        botsApi.registerBot(WeatherWhizBot(weatherService))
        println("Bot started!")
    } catch (e: TelegramApiException) {
        e.printStackTrace()
    }
}