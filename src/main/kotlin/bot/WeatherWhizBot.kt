package bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.bot.BaseAbilityBot
import org.telegram.abilitybots.api.objects.*
import org.telegram.abilitybots.api.toggle.BareboneToggle
import org.telegram.telegrambots.meta.api.objects.Update
import services.WeatherService
import utils.CommandConstant
import utils.Secret
import java.util.function.BiConsumer


class WeatherWhizBot(
    weatherService: WeatherService
) : AbilityBot(Secret.BOT_TOKEN, Secret.BOT_USERNAME, toggle) {

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

    // process inline keyboard interactions
//    fun replyToButtons(): Reply {
//        val action =
//            BiConsumer { _: BaseAbilityBot?, upd: Update ->
//                responseHandler.replyToButtons(
//                    getChatId(upd),
//                    upd.callbackQuery.data
//                )
//            }
//        return Reply.of(action, Flag.CALLBACK_QUERY)
//    }

    // welcome the user with greeting message, and prompt user to go to /config
    fun handleStartCommand() = generateAbility(CommandConstant.START) { ctx -> responseHandler.replyToStart(ctx) }

    // todo display available options and features in details
    fun handleHelpCommand() = generateAbility(CommandConstant.HELP) { ctx -> silent.send("", ctx.chatId()) }

    // get the current weather information for the configured location
    fun handleWeatherCommand() = generateAbility(CommandConstant.WEATHER) { ctx ->
        CoroutineScope(Dispatchers.Default).launch {
            responseHandler.replyToWeather(ctx)
        }
    }

    // get the configured location
    fun handleLocationCommand() =
        generateAbility(CommandConstant.LOCATION) { ctx -> responseHandler.replyToLocation(ctx) }

    // allow user to configure location by city name
    fun handleCityCommand() =
        Ability.builder()
            .name(CommandConstant.CITY)
            .privacy(Privacy.PUBLIC)
            .locality(Locality.ALL)
            .input(0)
            .action { ctx: MessageContext ->
                CoroutineScope(Dispatchers.Default).launch {
                    responseHandler.replyToCity(ctx)
                }
            }
            .build()

    fun handleLatLongCommand() =
        Ability.builder()
            .name(CommandConstant.LAT_LONG)
            .privacy(Privacy.PUBLIC)
            .locality(Locality.ALL)
            .input(2)
            .action { ctx: MessageContext ->
                CoroutineScope(Dispatchers.Default).launch {
                    responseHandler.replyToLatLong(ctx)
                }
            }
            .build()

    private fun generateAbility(name: String, action: (MessageContext) -> Unit): Ability = Ability.builder()
        .name(name)
        .locality(Locality.ALL)
        .privacy(Privacy.PUBLIC)
        .action(action)
        .build()
}
