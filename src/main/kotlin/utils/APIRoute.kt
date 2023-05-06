package utils

object APIRoute {
    private const val BASE_URL = "https://api.weatherapi.com/v1"
    const val CURRENT = "$BASE_URL/current.json"
    const val FORECAST = "$BASE_URL/forecast.json"
    const val TIME_ZONE = "$BASE_URL/timezone.json"
}