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
import utils.Secret
import java.util.function.BiConsumer

class WeatherWhizBot(
    private val weatherService: WeatherService
) : AbilityBot(Secret.BOT_TOKEN, Secret.BOT_USERNAME, toggle) {

    companion object {
        private val toggle = BareboneToggle()
    }

    private val responseHandler: ResponseHandler = ResponseHandler(sender, db)

    override fun creatorId(): Long = Secret.CREATOR_ID

    // allow user to configure their location using send location feature in Telegram
    fun handleOnLocationReceived(): Reply {
        val action =
            BiConsumer { _: BaseAbilityBot?, upd: Update? -> run { responseHandler.replyOnLocationReceived(upd) } }
        return Reply.of(action, Flag.LOCATION)
    }

    // welcome the user with greeting message, and prompt user to go to /config
    fun handleStartCommand(): Ability =
        Ability.builder()
            .name("start")
            .locality(Locality.ALL)
            .privacy(Privacy.PUBLIC)
            .action { ctx -> responseHandler.replyToStart(ctx) }
            .build()

    // display available options and features in details
    fun handleHelpCommand(): Ability =
        Ability.builder()
            .name("help")
            .locality(Locality.ALL)
            .privacy(Privacy.PUBLIC)
            .action { ctx ->
                silent.send(
                    "",
                    ctx.chatId()
                )
            }.build()

    // get the current weather information for the configured location
    fun handleWeatherCommand(): Ability = Ability.builder()
        .name("weather")
        .locality(Locality.ALL)
        .privacy(Privacy.PUBLIC)
        .action { ctx ->
            CoroutineScope(Dispatchers.Default).launch {
                responseHandler.replyToWeather(ctx, weatherService)
            }
        }
        .build()

    // get location (testing)
    fun handleLocationCommand(): Ability = Ability.builder()
        .name("location")
        .locality(Locality.ALL)
        .privacy(Privacy.PUBLIC)
        .action { ctx ->
            responseHandler.replyToLocation(ctx)
        }.build()
}
