package com.iphone.launcher

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

data class WeatherInfo(
    val city: String,
    val temp: Int,
    val high: Int,
    val low: Int,
    val desc: String
)

fun weatherDesc(code: Int): String {
    return when (code) {
        0 -> "Clear"
        1, 2 -> "Mostly clear"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather"
    }
}

private fun httpGet(u: String): String? {
    return try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        text
    } catch (e: Exception) {
        null
    }
}

fun fetchWeather(city: String): WeatherInfo? {
    return try {
        val geo = httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
                URLEncoder.encode(city, "UTF-8")
        ) ?: return null
        val results = JSONObject(geo).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        val lat = r.getDouble("latitude")
        val lon = r.getDouble("longitude")
        val name = r.optString("name", city)
        val body = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=" + lat +
                "&longitude=" + lon +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        ) ?: return null
        val j = JSONObject(body)
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        WeatherInfo(
            city = name,
            temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            desc = weatherDesc(cur.getInt("weather_code"))
        )
    } catch (e: Exception) {
        null
    }
}
