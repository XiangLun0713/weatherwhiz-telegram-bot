package bot

import models.CurrentResponse
import models.Day
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.objects.MessageContext
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import services.WeatherService
import utils.CommandConstant
import utils.DBConstant


class ResponseHandler(
    private val weatherService: WeatherService,
    private val sender: MessageSender,
    db: DBContext
) {
    private val latitudeDB: MutableMap<Long, Double>
    private val longitudeDB: MutableMap<Long, Double>
    private val locationNameDB: MutableMap<Long, String>

    init {
        latitudeDB = db.getMap(DBConstant.LATITUDE)
        longitudeDB = db.getMap(DBConstant.LONGITUDE)
        locationNameDB = db.getMap(DBConstant.LOCATION_NAME)
    }

    fun replyToStart(ctx: MessageContext) {
        try {
            // prepare the welcome message to be sent
            val message = SendMessage()
            message.text = """
                Hello ${ctx.user().userName}!

                I'm WeatherWhiz, your personal weather assistant.

                You have to configure your current location before you can use any of my available features.

                Please configure your location through one of the following approaches:
                
                1.  By sending me your location directly
                
                2.  By sending me your city's name using /city
                        Format: /city <city name> 
                        For example, /city Paris
                
                3.  By sending me your location's latitude and longitude using /latlong
                        Format: /latlong <latitude> <longitude>
                        For example, /latlong 48.8567 2.3508
            """.trimIndent()
            message.chatId = ctx.chatId().toString()
            // sent the welcome message
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    suspend fun replyOnLocationReceived(upd: Update?) {
        if (upd == null) return
        try {
            val chatID = upd.message.chatId
            val lat = upd.message.location.latitude
            val long = upd.message.location.longitude
            // store the user's location in locationDB
            configureLocation(chatID, lat, long)
            // send location configured success message
            sendLocationConfiguredSuccessfulMessage(chatID)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    suspend fun replyToCity(ctx: MessageContext) {
        val userInput = ctx.update().message.text
        val chatID = ctx.chatId()
        // if user does not enter anything
        if (userInput.trim() == "/${CommandConstant.CITY}") {
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                Mal-formatted input.
                Format: /city <city name> 
                For example, /city Paris
            """.trimIndent()
            sender.execute(message)
            return
        }
        // configure user's location
        configureLocation(chatID, cityName = userInput)
        // send location configured success message
        sendLocationConfiguredSuccessfulMessage(chatID)
    }

    suspend fun replyToLatLong(ctx: MessageContext) {
        val userInput = ctx.update().message.text.substring("/latlong ".length)
        val chatID = ctx.chatId()
        // extract latitude and longitude
        val indexOfSpace: Int = userInput.indexOf(' ')
        val lat: Double = userInput.substring(0, indexOfSpace).toDouble()
        val long: Double = userInput.substring(indexOfSpace + 1).toDouble()
        // configure user's location
        configureLocation(chatID, lat = lat, long = long)
        // send location configured success message
        sendLocationConfiguredSuccessfulMessage(chatID)
    }

    fun replyToLocation(ctx: MessageContext) {
        try {
            val chatID = ctx.chatId()
            val locationName = locationNameDB[chatID]
            // if location not configured
            if (locationName == null) {
                sendLocationNotConfiguredMessage(chatID)
                return
            }
            // if location exists
            val message = SendMessage()
            message.text = "Your location is $locationName"
            message.chatId = chatID.toString()
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    suspend fun replyToWeather(ctx: MessageContext) {
        try {
            val chatID = ctx.chatId()
            // retrieve the user's lat and long from db
            val lat: Double? = latitudeDB[chatID]
            val long: Double? = longitudeDB[chatID]
            // if the user has not configured their location yet, send the message to ask for it
            if (lat == null || long == null) {
                sendLocationNotConfiguredMessage(chatID)
                return
            }
            // if the user has configured their location before,
            // make API call
            val currentResponse: CurrentResponse = weatherService.getCurrentResponseByLatLong(lat, long)
            // send them their current weather information
            val message = SendMessage()
            message.text = """
                Current Weather Information
                
                ${getEmojiForConditionCode(currentResponse.current.condition.code)} ${currentResponse.current.condition.text}
                                
                🌡️ Temperature: ${currentResponse.current.temp_c}°C / ${currentResponse.current.temp_f}°F
                
                💨 Wind Speed: ${currentResponse.current.wind_kph}km/h
                
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

    suspend fun replyToToday(ctx: MessageContext) {
        try {
            val chatID = ctx.chatId()
            // retrieve the user's lat and long from db
            val lat: Double? = latitudeDB[chatID]
            val long: Double? = longitudeDB[chatID]
            // if the user has not configured their location yet, send the message to ask for it
            if (lat == null || long == null) {
                sendLocationNotConfiguredMessage(chatID)
                return
            }
            // get today's weather info
            val forecastResponse = weatherService.getForecastResponseByLatLong(lat, long)
            val day: Day = forecastResponse.forecast.forecastday[0].day
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                Today's Weather Information
                
                ${getEmojiForConditionCode(day.condition.code)} ${day.condition.text}
                
                🌧️ Rain: ${if (day.daily_will_it_rain == 1) "Yes" else "No"} (${day.daily_chance_of_rain}% chance)
                
                🌡️ Average Temperature: ${day.avgtemp_c}°C / ${day.avgtemp_f}°F
                
                💧 Average Humidity: ${day.avghumidity.toInt()}%
                
                ☀️ UV Index: ${day.uv}
            """.trimIndent()
            sender.execute(message)
        } catch (e: TelegramApiException) {
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

    private suspend fun configureLocation(chatID: Long, lat: Double, long: Double) {
        latitudeDB[chatID] = lat
        longitudeDB[chatID] = long
        val timezoneResponse = weatherService.getTimezoneResponseByLatLong(lat, long)
        locationNameDB[chatID] = if (timezoneResponse.location.region.isEmpty()) {
            "${timezoneResponse.location.name}, ${timezoneResponse.location.country}"
        } else {
            "${timezoneResponse.location.name}, ${timezoneResponse.location.region}, ${timezoneResponse.location.country}"
        }
    }

    private suspend fun configureLocation(chatID: Long, cityName: String) {
        val timezoneResponse = weatherService.getTimezoneResponseByCityName(cityName)
        latitudeDB[chatID] = timezoneResponse.location.lat
        longitudeDB[chatID] = timezoneResponse.location.lon
        locationNameDB[chatID] = if (timezoneResponse.location.region.isEmpty()) {
            "${timezoneResponse.location.name}, ${timezoneResponse.location.country}"
        } else {
            "${timezoneResponse.location.name}, ${timezoneResponse.location.region}, ${timezoneResponse.location.country}"
        }
    }

    private fun sendLocationNotConfiguredMessage(chatID: Long) {
        val message = SendMessage()
        message.text = """
            You have not configured your location yet. 
            
            Please configure your location through the following approaches:
            
            1. By sending me your location directly
            
            2. By sending me your city's name using the /city command using the format /city <city name>. For example, /city Paris
            
            3. By sending me your location's latitude and longitude using the /latlong command using the format /latlong <latitude> <longitude>. For example, /latlong 48.8567 2.3508
        """.trimIndent()
        message.chatId = chatID.toString()
        sender.execute(message)
    }

    private fun sendLocationConfiguredSuccessfulMessage(chatID: Long) {
        val message = SendMessage()
        message.text = """
            Location configured successfully!
            Your location is ${locationNameDB[chatID]}
        """.trimIndent()
        message.chatId = chatID.toString()
        sender.execute(message)
    }
}