package bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import models.*
import org.telegram.abilitybots.api.db.DBContext
import org.telegram.abilitybots.api.objects.MessageContext
import org.telegram.abilitybots.api.sender.MessageSender
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import services.WeatherService
import utils.CommandConstant
import utils.DBConstant
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class ResponseHandler(
    private val weatherService: WeatherService,
    private val sender: MessageSender,
    db: DBContext
) {
    private val latitudeDB: MutableMap<Long, Double>
    private val longitudeDB: MutableMap<Long, Double>
    private val locationNameDB: MutableMap<Long, String>
    private val subscribeToWeatherUpdateDB: MutableSet<Long>
    private val timezoneOffsetInMillisDB: MutableMap<Long, Long>

    init {
        latitudeDB = db.getMap(DBConstant.LATITUDE)
        longitudeDB = db.getMap(DBConstant.LONGITUDE)
        locationNameDB = db.getMap(DBConstant.LOCATION_NAME)
        subscribeToWeatherUpdateDB = db.getSet(DBConstant.SUBSCRIBE_TO_WEATHER_UPDATE)
        timezoneOffsetInMillisDB = db.getMap(DBConstant.TIMEZONE_OFFSET)
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
                
                2.  By sending me your city's name using /${CommandConstant.CITY}. To do that, please type /${CommandConstant.CITY} followed by the name of your city. For example, /${CommandConstant.CITY} Paris
                
                3.  By sending me your location's latitude and longitude using /${CommandConstant.LAT_LONG}. To do that, please type /${CommandConstant.LAT_LONG} followed by your latitude and longitude in the format <latitude>${"\u00A0"}<longitude>. For example, /${CommandConstant.LAT_LONG} 48.8567${"\u00A0"}2.3508
            """.trimIndent()
            message.chatId = ctx.chatId().toString()
            // sent the welcome message
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    fun replyToHelp(ctx: MessageContext) {
        try {
            // prepare the help message to be sent
            val message = SendMessage()
            message.text = """
                Here's a quick guide to help you use our bot effectively:

                1. To see the current configured location, type /${CommandConstant.LOCATION} and you will receive the name of the location.

                2. To configure your location using latitude and longitude, type /${CommandConstant.LAT_LONG} followed by your latitude and longitude in the format <latitude>${"\u00A0"}<longitude>. For example, /${CommandConstant.LAT_LONG} 48.8567${"\u00A0"}2.3508

                3. To configure your location using city name, type /${CommandConstant.CITY} followed by the name of your city. For example, /${CommandConstant.CITY} Paris

                4. To get today's weather information for your configured location, type /${CommandConstant.TODAY} and you will receive a summary of today's weather.

                5. To get the current weather information for your configured location, type /${CommandConstant.WEATHER} and you will receive the current weather information.

                6. To subscribe to daily weather updates for your configured location, type /${CommandConstant.SUBSCRIBE} and you will receive daily weather updates.

                7. To unsubscribe from daily weather updates, type /${CommandConstant.UNSUBSCRIBE}.
            """.trimIndent()
            message.chatId = ctx.chatId().toString()
            // sent the help message
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
        val userInput = ctx.update().message.text.substring("/${CommandConstant.CITY}".length).trim()
        val chatID = ctx.chatId()
        // if user does not enter anything
        if (userInput.isEmpty()) {
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                Mal-formatted input.
                Format: /${CommandConstant.CITY} <city name> 
                For example, /${CommandConstant.CITY} Paris
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
        val userInput = ctx.update().message.text.substring("/${CommandConstant.LAT_LONG}".length).trim()
        val chatID = ctx.chatId()
        if (userInput.isEmpty() || !userInput.matches("^[+-]?\\d+(?:\\.\\d+)?\\s[+-]?\\d+(?:\\.\\d+)?\$".toRegex())) {
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                Mal-formatted input.
                Format: /${CommandConstant.LAT_LONG} <latitude>${"\u00A0"}<longitude> 
                For example, /${CommandConstant.LAT_LONG} 48.8567${"\u00A0"}2.3508
            """.trimIndent()
            sender.execute(message)
            return
        }
        // extract latitude and longitude
        val indexOfSpace: Int = userInput.indexOf(' ')
        val lat: Double = userInput.substring(0, indexOfSpace).toDouble()
        val long: Double = userInput.substring(indexOfSpace + 1).toDouble()
        // configure user's location
        configureLocation(chatID, lat = lat, long = long)
        // send location configured success message
        sendLocationConfiguredSuccessfulMessage(chatID)
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

    suspend fun replyToToday(chatID: Long) {
        // retrieve the user's lat and long from db
        val lat: Double? = latitudeDB[chatID]
        val long: Double? = longitudeDB[chatID]
        // if the user has not configured their location yet, send the message to ask for it
        if (lat == null || long == null) {
            sendLocationNotConfiguredMessage(chatID)
            return
        }
        try {
            // get today's weather info
            val forecastResponse = weatherService.getForecastResponseByLatLong(lat, long)
            val day: Day = forecastResponse.forecast.forecastday[0].day
            val hours: List<Hour> = forecastResponse.forecast.forecastday[0].hour
            val localTime =
                if (forecastResponse.location.localtime.substring("yyyy-MM-dd ".length).length == "h:mm".length)
                    "0".plus(forecastResponse.location.localtime.substring("yyyy-MM-dd ".length))
                else forecastResponse.location.localtime.substring("yyyy-MM-dd ".length)
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                Today's Weather Information
                
                ${getEmojiForConditionCode(day.condition.code)} ${day.condition.text}
                
                🌧️ Rain: ${if (day.daily_will_it_rain == 1) "Yes" else "No"} (${day.daily_chance_of_rain}% chance)
                
                🌡️ Average Temperature: ${day.avgtemp_c}°C / ${day.avgtemp_f}°F
                
                💧 Average Humidity: ${day.avghumidity.toInt()}%
                
                ☀️ UV Index: ${day.uv}
                
                Hourly Weather Information
            
            """.trimIndent().plus(getFutureHourInformationByHourList(hours, localTime))
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
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

    fun replyToSubscribe(ctx: MessageContext) {
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
            // add this user to subscribe list
            subscribeToWeatherUpdateDB.add(chatID)
            // send success message
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                You have successfully subscribed to daily weather updates in the morning.
                
                Please note that due to timezone differences, it may take up to a day for the change in your subscription status to take effect, and you may not receive your first weather update notification on the same day as your subscription.                
                
                To unsubscribe, please enter /${CommandConstant.UNSUBSCRIBE}
            """.trimIndent()
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    fun replyToUnsubscribe(ctx: MessageContext) {
        try {
            val chatID = ctx.chatId()
            // remove this user from subscribe list
            val existsInDB: Boolean = subscribeToWeatherUpdateDB.remove(chatID)
            // send success message
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = if (existsInDB) """
                You have successfully unsubscribed from daily weather updates in the morning.
                
                Please note that due to timezone differences, you may still receive one final weather update notification tomorrow before the change in your subscription status takes effect.
            """.trimIndent() else """
                You are not subscribed to the daily weather updates.
                
                To subscribe, please enter /${CommandConstant.SUBSCRIBE}
            """.trimIndent()
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    suspend fun replyToForecast(ctx: MessageContext) {
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
            // get forecast response
            val forecastResponse: ForecastResponse = weatherService.getForecastResponseByLatLong(lat, long)
            val day1 = forecastResponse.forecast.forecastday[0]
            val day2 = forecastResponse.forecast.forecastday[1]
            val day3 = forecastResponse.forecast.forecastday[2]
            // send message
            val message = SendMessage()
            message.chatId = chatID.toString()
            message.text = """
                ${day1.date} - ${day1.day.condition.text} ${getEmojiForConditionCode(day1.day.condition.code)}
                ${day2.date} - ${day2.day.condition.text} ${getEmojiForConditionCode(day2.day.condition.code)}
                ${day3.date} - ${day3.day.condition.text} ${getEmojiForConditionCode(day3.day.condition.code)}
            """.trimIndent()
            sender.execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    fun sendMorningWeatherUpdateMessage() {
        for (chatID in subscribeToWeatherUpdateDB) {
            val timezoneOffsetInMillis = timezoneOffsetInMillisDB[chatID] ?: return
            val reminderTask = Runnable {
                Thread.sleep(if (timezoneOffsetInMillis < 0) ((24 * 60 * 60 * 1000) + timezoneOffsetInMillis) else timezoneOffsetInMillis)
                CoroutineScope(Dispatchers.Default).launch {
                    replyToToday(chatID)
                }
            }
            Thread(reminderTask).start()
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
        val timezoneResponse = weatherService.getTimezoneResponseByLatLong(lat, long)
        val locationName = getLocationNameFromTimezoneResponse(timezoneResponse)
        saveLocation(chatID, lat, long, locationName, getUtcOffset(timezoneResponse.location.localtime))
    }

    private suspend fun configureLocation(chatID: Long, cityName: String) {
        val timezoneResponse = weatherService.getTimezoneResponseByCityName(cityName)
        val lat = timezoneResponse.location.lat
        val long = timezoneResponse.location.lon
        val locationName = getLocationNameFromTimezoneResponse(timezoneResponse)
        saveLocation(chatID, lat, long, locationName, getUtcOffset(timezoneResponse.location.localtime))
    }

    private fun getLocationNameFromTimezoneResponse(timezoneResponse: TimezoneResponse) =
        if (timezoneResponse.location.region.isEmpty()) {
            "${timezoneResponse.location.name}, ${timezoneResponse.location.country}"
        } else {
            "${timezoneResponse.location.name}, ${timezoneResponse.location.region}, ${timezoneResponse.location.country}"
        }

    private fun getUtcOffset(dateTimeString: String): TimeZoneOffset {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val date = dateFormat.parse(dateTimeString)
        val timeZone = TimeZone.getDefault()
        val offsetInMillis = timeZone.getOffset(date.time)
        val offsetHours = TimeUnit.MILLISECONDS.toHours(offsetInMillis.toLong())
        val offsetMinutes = TimeUnit.MILLISECONDS.toMinutes(offsetInMillis.toLong()) % 60
        return TimeZoneOffset(offsetHours = offsetHours, offsetMinutes = offsetMinutes)
    }

    private fun saveLocation(
        chatID: Long,
        lat: Double,
        long: Double,
        locationName: String,
        timezoneOffset: TimeZoneOffset
    ) {
        latitudeDB[chatID] = lat
        longitudeDB[chatID] = long
        locationNameDB[chatID] = locationName
        timezoneOffsetInMillisDB[chatID] = timezoneOffset.totalOffsetInMillis
    }

    private fun sendLocationNotConfiguredMessage(chatID: Long) {
        val message = SendMessage()
        message.text = """
            You have not configured your location yet. 
            
            Please configure your location through the following approaches:
            
            1. By sending me your location directly
            
            2. By sending me your city's name using the /${CommandConstant.CITY} command using the format /${CommandConstant.CITY} <city name>. For example, /${CommandConstant.CITY} Paris
            
            3. By sending me your location's latitude and longitude using the /${CommandConstant.LAT_LONG} command using the format /${CommandConstant.LAT_LONG} <latitude>${"\u00A0"}<longitude>. For example, /${CommandConstant.LAT_LONG} 48.8567${"\u00A0"}2.3508
        """.trimIndent()
        message.chatId = chatID.toString()
        sender.execute(message)
    }

    private fun sendLocationConfiguredSuccessfulMessage(chatID: Long) {
        val message = SendMessage()
        message.text = """
            Location configured successfully!
            Your location is ${locationNameDB[chatID]}
            
            Type /${CommandConstant.HELP} to see a list of features that I can provide.
        """.trimIndent()
        message.chatId = chatID.toString()
        sender.execute(message)
    }

    private fun getFutureHourInformationByHourList(hours: List<Hour>, localTime: String): String {
        var result = ""
        for (hour in hours) {
            val time = hour.time.substring("yyyy-mm-dd ".length)
            println("time ${time.substring(0, "hh".length)}")
            println("localtime ${localTime.substring(0, "hh".length)}")
            if (localTime.substring(0, "hh".length) <= time.substring(0, "hh".length)) {
                println("called")
                result = result.plus(
                    "$time - ${hour.condition.text} ${
                        getEmojiForConditionCode(hour.condition.code)
                    }\n"
                )
            }
        }
        return result
    }
}