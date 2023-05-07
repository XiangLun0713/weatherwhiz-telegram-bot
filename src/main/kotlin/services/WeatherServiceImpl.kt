package services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import models.CurrentResponse
import models.ForecastResponse
import models.TimezoneResponse
import utils.APIRoute
import utils.Secret

class WeatherServiceImpl(
    private val client: HttpClient
) : WeatherService {
    override suspend fun getCurrentResponseByLatLong(latitude: Double, longitude: Double): CurrentResponse {
        return client.get {
            url(APIRoute.CURRENT)
            parameter("key", Secret.WEATHER_API_KEY)
            parameter("q", "$latitude,$longitude")
        }.body<CurrentResponse>()
    }

    override suspend fun getTimezoneResponseByLatLong(latitude: Double, longitude: Double): TimezoneResponse {
        return client.get {
            url(APIRoute.TIME_ZONE)
            parameter("key", Secret.WEATHER_API_KEY)
            parameter("q", "$latitude,$longitude")
        }.body<TimezoneResponse>()
    }

    override suspend fun getTimezoneResponseByCityName(cityName: String): TimezoneResponse {
        return client.get {
            url(APIRoute.TIME_ZONE)
            parameter("key", Secret.WEATHER_API_KEY)
            parameter("q", cityName)
        }.body<TimezoneResponse>()
    }

    override suspend fun getForecastResponseByLatLong(
        latitude: Double,
        longitude: Double,
        days: Int
    ): ForecastResponse {
        return client.get {
            url(APIRoute.FORECAST)
            parameter("key", Secret.WEATHER_API_KEY)
            parameter("q", "$latitude, $longitude")
            parameter("days", days)
            parameter("alerts", "yes")
        }.body<ForecastResponse>()
    }
}