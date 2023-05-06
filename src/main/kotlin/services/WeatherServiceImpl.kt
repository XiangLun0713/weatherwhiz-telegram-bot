package services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import models.CurrentResponse
import utils.APIRoute
import utils.Secret

class WeatherServiceImpl(
    private val client: HttpClient
) : WeatherService {
    override suspend fun getCurrentByLatLong(latitude: Double, longitude: Double): CurrentResponse {
        return client.get {
            url(APIRoute.CURRENT)
            parameter("key", Secret.WEATHER_API_KEY)
            parameter("q", "$latitude,$longitude")
        }.body<CurrentResponse>()
    }
}