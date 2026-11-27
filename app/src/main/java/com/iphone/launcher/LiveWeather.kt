package com.iphone.launcher

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class WeatherNow(
    val city: String,
    val temp: Int,
    val desc: String,
    val high: Int,
    val low: Int
)

private fun liveWeatherGet(link: String): String? {
    return try {
        val c = URL(link).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.requestMethod = "GET"
        val text = if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        c.disconnect()
        text
    } catch (e: Exception) {
        null
    }
}

private fun weatherWords(code: Int): String {
    return when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather"
    }
}

fun fetchWeatherNow(city: String): WeatherNow? {
    return try {
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(city, "UTF-8") + "&count=1&language=en&format=json"
        val geo = liveWeatherGet(geoUrl) ?: return null
        val results = JSONObject(geo).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val place = results.getJSONObject(0)
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val name = place.optString("name", city)
        val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat +
            "&longitude=" + lon +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val body = liveWeatherGet(forecastUrl) ?: return null
        val root = JSONObject(body)
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        WeatherNow(
            city = name,
            temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
            desc = weatherWords(cur.getInt("weather_code")),
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt()
        )
    } catch (e: Exception) {
        null
    }
}

fun readWeatherCache(prefs: SharedPreferences): WeatherNow? {
    val city = prefs.getString("wx_city", null) ?: return null
    return WeatherNow(
        city = city,
        temp = prefs.getInt("wx_temp", 0),
        desc = prefs.getString("wx_desc", "") ?: "",
        high = prefs.getInt("wx_high", 0),
        low = prefs.getInt("wx_low", 0)
    )
}

fun writeWeatherCache(prefs: SharedPreferences, w: WeatherNow) {
    prefs.edit()
        .putString("wx_city", w.city)
        .putInt("wx_temp", w.temp)
        .putString("wx_desc", w.desc)
        .putInt("wx_high", w.high)
        .putInt("wx_low", w.low)
        .apply()
}
