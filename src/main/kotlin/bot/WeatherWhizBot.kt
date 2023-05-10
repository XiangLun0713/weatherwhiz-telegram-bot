package bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.bot.BaseAbilityBot
import org.telegram.abilitybots.api.objects.*
import org.telegram.abilitybots.api.toggle.BareboneToggle
import org.telegram.telegrambots.meta.api.objects.Update
import schedulers.DailyTaskExecutor
import schedulers.MorningWeatherUpdateTask
import services.WeatherService
import utils.CommandConstant
import utils.Secret
import java.util.function.BiConsumer


class WeatherWhizBot(
    weatherService: WeatherService,
) : AbilityBot(Secret.BOT_TOKEN, Secret.BOT_USERNAME, toggle),
    MorningWeatherUpdateTask.Callback {

    // instance of the daily task executor
    private val dailyTaskExecutor: DailyTaskExecutor = DailyTaskExecutor(MorningWeatherUpdateTask(this))

    init {
        dailyTaskExecutor.startExecutionAt(7, 0, 0)
    }

    companion object {
        private val toggle = BareboneToggle()
    }

    private val responseHandler: ResponseHandler = ResponseHandler(weatherService, sender, db)

    override fun creatorId(): Long = Secret.CREATOR_ID

    // allow user to configure their location using send location feature in Telegram
    fun handleOnLocationReceived(): Reply {
        val action =
            BiConsumer { _: BaseAbilityBot?, upd: Update? ->
                CoroutineScope(Dispatchers.Default).launch {
                    responseHandler.replyOnLocationReceived(upd)
                }
            }
        return Reply.of(action, Flag.LOCATION)
    }

    // welcome the user with greeting message, and prompt user to go to /config
    fun handleStartCommand() = generateAbility(CommandConstant.START) { ctx -> responseHandler.replyToStart(ctx) }

    // display available options and features in details
    fun handleHelpCommand() = generateAbility(CommandConstant.HELP) { ctx -> responseHandler.replyToHelp(ctx) }

    // get the current weather information for the configured location
    fun handleWeatherCommand() = generateAbility(CommandConstant.WEATHER) { ctx ->
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.replyToWeather(ctx)
        }
    }

    fun handleTodayCommand() = generateAbility(CommandConstant.TODAY) { ctx ->
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.replyToToday(ctx.chatId())
        }
    }

    // allow user to subscribe to daily weather updates in the morning
    fun handleSubscribeCommand() = generateAbility(CommandConstant.SUBSCRIBE) { ctx ->
        responseHandler.replyToSubscribe(ctx)
    }

    // allow user to unsubscribe to daily weather updates in the morning
    fun handleUnSubscribeCommand() = generateAbility(CommandConstant.UNSUBSCRIBE) { ctx ->
        responseHandler.replyToUnsubscribe(ctx)
    }

    // get the configured location
    fun handleLocationCommand() =
        generateAbility(CommandConstant.LOCATION) { ctx -> responseHandler.replyToLocation(ctx) }

    // allow user to configure location by city name
    fun handleCityCommand() = generateAbility(CommandConstant.CITY) { ctx: MessageContext ->
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.replyToCity(ctx)
        }
    }

    // allow user to configure location by latitude and longitude
    fun handleLatLongCommand() = generateAbility(CommandConstant.LAT_LONG) { ctx: MessageContext ->
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.replyToLatLong(ctx)
        }
    }

    // get the next 3 day's weather forecast information
    fun handleForecastCommand() = generateAbility(CommandConstant.FORECAST) { ctx: MessageContext ->
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.replyToForecast(ctx)
        }
    }

    private fun generateAbility(name: String, action: (MessageContext) -> Unit): Ability = Ability.builder()
        .name(name)
        .locality(Locality.ALL)
        .privacy(Privacy.PUBLIC)
        .action(action)
        .build()

    override fun onTimeForMorningTask() {
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.sendMorningWeatherUpdateMessage()
        }
    }
}
