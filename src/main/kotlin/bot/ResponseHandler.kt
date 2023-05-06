package bot

import models.CurrentResponse
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.objects.MessageContext
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.abilitybots.api.util.AbilityUtils
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import services.WeatherService
import utils.ChatState
import utils.DBConstant


class ResponseHandler(private val sender: MessageSender, db: DBContext) {
    private val chatStateDB: MutableMap<Long, ChatState>
    private val latitudeDB: MutableMap<Long, Double>
    private val longitudeDB: MutableMap<Long, Double>

    init {
        chatStateDB = db.getMap(DBConstant.CHAT_STATES)
        latitudeDB = db.getMap(DBConstant.LATITUDE)
        longitudeDB = db.getMap(DBConstant.LONGITUDE)
    }

    fun replyToStart(ctx: MessageContext) {
        try {
            // prepare the welcome message to be sent
            val message = SendMessage()
            message.text =
                "Hello ${ctx.user().userName}!\n\nI'm WeatherWhiz, your personal weather assistant.\n\nPlease enter /config to configure your current location and receive real-time weather updates and forecasts right away!"
            message.chatId = ctx.chatId().toString()
            // sent the welcome message
            sender.execute(message)
            // set the chat state as location not configured
            chatStateDB[ctx.chatId()] = ChatState.CHAT_STATE_LOCATION_NOT_CONFIGURED
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    fun replyOnLocationReceived(upd: Update?) {
        if (upd != null) {
            try {
                // store the user's location in locationDB
                val chatID = upd.message.chatId
                latitudeDB[chatID] = upd.message.location.latitude
                longitudeDB[chatID] = upd.message.location.longitude
                // set the chat state as location configured
                chatStateDB[chatID] = ChatState.CHAT_STATE_LOCATION_CONFIGURED
                // prepare the message to be sent
                val message = SendMessage()
                message.text = "Location received!\n\nPlease enter /help to get you started!"
                message.chatId = AbilityUtils.getChatId(upd).toString()
                // send the message
                sender.execute(message)
            } catch (e: TelegramApiException) {
                e.printStackTrace()
            }
        }
    }

    fun replyToLocation(ctx: MessageContext) {
        try {
            val chatID = ctx.chatId()
            val lat = latitudeDB[chatID]
            val long = longitudeDB[chatID]
            val message = SendMessage()
            message.text =
                "Location for chat $chatID is latitude:${lat ?: "Not found"}, longitude:${long ?: "Not found"} "
            message.chatId = chatID.toString()
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    suspend fun replyToWeather(ctx: MessageContext, weatherService: WeatherService) {
        println("called")
        try {
            val chatID = ctx.chatId()
            val lat: Double? = latitudeDB[chatID]
            val long: Double? = longitudeDB[chatID]
            // if the user has not configured their location yet, send the message to ask for it
            if (chatStateDB[chatID] == null || chatStateDB[chatID] == ChatState.CHAT_STATE_LOCATION_NOT_CONFIGURED) {
                val message = SendMessage()
                message.text = """
                    You have not configured your location yet.
                    
                    Please enter /config to configure your location!
                """.trimIndent()
                message.chatId = chatID.toString()
                sender.execute(message)
                return
            }
            // if the user has configured their location before, send their current weather information
            val currentResponse: CurrentResponse = weatherService.getCurrentByLatLong(lat!!, long!!)
            val message = SendMessage()
            message.text = """
                ${getEmojiForConditionCode(currentResponse.current.condition.code)} ${currentResponse.current.condition.text}
                                
                🌡️ Temperature: ${currentResponse.current.temp_c}°C / ${currentResponse.current.temp_f}°F
                
                💨 Wind speed: ${currentResponse.current.wind_kph}km/h
                
                💧 Humidity: ${currentResponse.current.humidity}%
                
                ☀️ UV Index: ${currentResponse.current.uv}
                
                Last updated by ${currentResponse.current.last_updated}
            """.trimIndent()
            message.chatId = chatID.toString()
            sender.execute(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // utils
    private fun getEmojiForConditionCode(code: Int): String = when (code) {
        1000 -> "☀️"
        1003, 1006, 1009 -> "☁️"
        1063, 1069, 1072, 1150, 1153, 1180, 1183, 1186, 1189, 1192, 1195, 1240, 1243, 1246 -> "🌧️"
        1168, 1171, 1198, 1201, 1204, 1207, 1249, 1252 -> "🌧️❄️"
        1087, 1273, 1276 -> "⛈️"
        1279, 1282 -> "⛈️❄️"
        1030, 1135 -> "🌫️"
        1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1237, 1255, 1258, 12611, 1264 -> "❄️"
        1147 -> "🌫️❄️"
        else -> ""
    }
}