package services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import models.CurrentResponse


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

    suspend fun getCurrentByLatLong(latitude: Double, longitude: Double): CurrentResponse
}