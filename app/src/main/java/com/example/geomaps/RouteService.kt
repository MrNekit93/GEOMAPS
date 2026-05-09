package com.example.geomaps

import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Модель запроса на построение маршрута
 */
data class RouteRequest(
    val start_lat: Double,
    val start_lon: Double,
    val end_lat: Double,
    val end_lon: Double,
    val transport: String = "car",
    val route_type: String = "all"
)

/**
 * Модель ответа с маршрутом
 */
data class RouteResponse(
    val distance_km: Double? = null,
    val travel_hours: Double? = null,
    val nodes_visited: Int? = null,
    val path: List<List<Double>>? = null,
    val algorithm: String? = null
)

/**
 * Модель ответа для сравнения
 */
data class CompareResponse(
    val dijkstra: RouteResponse? = null,
    val a_star: RouteResponse? = null,
    val difference_percent: Double? = null
)

/**
 * Сервис для работы с API сервера маршрутов
 */
class RouteService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = " http://10.39.35.109:8000"

    /**
     * Получение маршрута по алгоритму (Дейкстра или A*)
     */
    suspend fun getRoute(algorithm: String, request: RouteRequest): RouteResponse? = suspendCoroutine { continuation ->
        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$baseUrl/route/$algorithm")
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val respString = response.body?.string()
                if (respString != null) {
                    try {
                        val result = gson.fromJson(respString, RouteResponse::class.java)
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                } else {
                    continuation.resume(null)
                }
            }
        })
    }

    /**
     * Сравнение алгоритмов
     */
    suspend fun getCompare(request: RouteRequest): CompareResponse? = suspendCoroutine { continuation ->
        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$baseUrl/route/compare")
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val respString = response.body?.string()
                if (respString != null) {
                    try {
                        val result = gson.fromJson(respString, CompareResponse::class.java)
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                } else {
                    continuation.resume(null)
                }
            }
        })
    }
}
