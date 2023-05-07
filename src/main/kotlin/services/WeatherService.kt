package services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import models.CurrentResponse
import models.ForecastResponse
import models.TimezoneResponse


interface WeatherService {

    companion object {
        fun create(): WeatherService {
            return WeatherServiceImpl(
                client = HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(Json{
                            ignoreUnknownKeys = true
                        })
                    }
                }
            )
        }
    }

    suspend fun getCurrentResponseByLatLong(latitude: Double, longitude: Double): CurrentResponse
    suspend fun getTimezoneResponseByLatLong(latitude: Double, longitude: Double): TimezoneResponse
    suspend fun getTimezoneResponseByCityName(cityName: String): TimezoneResponse
    suspend fun getForecastResponseByLatLong(latitude: Double, longitude: Double): ForecastResponse
}