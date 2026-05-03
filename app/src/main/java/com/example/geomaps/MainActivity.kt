package com.example.geomaps

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

data class City(val name: String, val lat: Double, val lon: Double)

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private val service = RouteService()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val routeLines = mutableListOf<Polyline>()

    private val cities = listOf(
        City("Минск", 53.9, 27.55),
        City("Брест", 52.097, 23.734),
        City("Гродно", 53.669, 23.834),
        City("Витебск", 55.184, 30.204),
        City("Гомель", 52.431, 30.992),
        City("Могилёв", 53.916, 30.344),
        City("Барановичи", 53.130, 26.014),
        City("Борисов", 54.227, 28.506),
        City("Пинск", 52.115, 26.101),
        City("Орша", 54.513, 30.425)
    )

    private var selectedStartCity: City? = null
    private var selectedEndCity: City? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)

        map.controller.setZoom(7.0)
        map.controller.setCenter(GeoPoint(53.9, 27.55))
        map.setMultiTouchControls(true)

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false

                if (startPoint == null) {
                    startPoint = p
                    addMarker(p, Color.GREEN, "Старт")
                } else if (endPoint == null) {
                    endPoint = p
                    addMarker(p, Color.RED, "Финиш")
                } else {
                    clearRoute()
                    startPoint = p
                    addMarker(p, Color.GREEN, "Старт")
                }
                return true
            }
        })
        map.overlays.add(mapEventsOverlay)

        val spinnerStart = findViewById<Spinner>(R.id.spinnerStart)
        val spinnerEnd = findViewById<Spinner>(R.id.spinnerEnd)
        val btnSwap = findViewById<Button>(R.id.btnSwap)
        val btnDijkstra = findViewById<Button>(R.id.btnDijkstra)
        val btnAstar = findViewById<Button>(R.id.btnAstar)
        val btnCompare = findViewById<Button>(R.id.btnCompare)

        val cityNames = listOf("Выберите город (старт)") + cities.map { it.name }
        val adapterStart = ArrayAdapter(this, android.R.layout.simple_spinner_item, cityNames)
        adapterStart.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStart.adapter = adapterStart

        val cityNamesEnd = listOf("Выберите город (финиш)") + cities.map { it.name }
        val adapterEnd = ArrayAdapter(this, android.R.layout.simple_spinner_item, cityNamesEnd)
        adapterEnd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEnd.adapter = adapterEnd

        spinnerStart.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos == 0) return
                val city = cities[pos - 1]
                selectedStartCity = city
                setStartPoint(GeoPoint(city.lat, city.lon))
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerEnd.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos == 0) return
                val city = cities[pos - 1]
                selectedEndCity = city
                setEndPoint(GeoPoint(city.lat, city.lon))
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnSwap.setOnClickListener {
            val temp = selectedStartCity
            selectedStartCity = selectedEndCity
            selectedEndCity = temp

            val startPos = if (selectedStartCity != null) cities.indexOf(selectedStartCity) + 1 else 0
            val endPos = if (selectedEndCity != null) cities.indexOf(selectedEndCity) + 1 else 0

            spinnerStart.setSelection(startPos)
            spinnerEnd.setSelection(endPos)

            if (selectedStartCity != null) setStartPoint(GeoPoint(selectedStartCity!!.lat, selectedStartCity!!.lon))
            if (selectedEndCity != null) setEndPoint(GeoPoint(selectedEndCity!!.lat, selectedEndCity!!.lon))
        }

        btnDijkstra.setOnClickListener {
            requestRoute("dijkstra")
        }

        btnAstar.setOnClickListener {
            requestRoute("a_star")
        }

        btnCompare?.setOnClickListener {
            requestCompare()
        }
    }

    private fun setStartPoint(point: GeoPoint) {
        clearRoute()
        startPoint = point
        addMarker(point, Color.GREEN, "Старт")
    }

    private fun setEndPoint(point: GeoPoint) {
        endPoint = point
        addMarker(point, Color.RED, "Финиш")
    }

    private fun addMarker(point: GeoPoint, color: Int, title: String) {
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun clearRoute() {
        for (line in routeLines) {
            map.overlays.remove(line)
        }
        routeLines.clear()
        map.invalidate()
    }

    private fun clearMarkers() {
        map.overlays.clear()
        startPoint = null
        endPoint = null
        routeLines.clear()
        map.invalidate()
    }

    private fun requestRoute(algorithm: String) {
        val start = startPoint
        val end = endPoint

        if (start == null || end == null) {
            Toast.makeText(this, "Сначала выберите точки на карте", Toast.LENGTH_SHORT).show()
            return
        }

        val btnDijkstra = findViewById<Button>(R.id.btnDijkstra)
        val btnAstar = findViewById<Button>(R.id.btnAstar)
        val btnCompare = findViewById<Button>(R.id.btnCompare)
        val tvInfo = findViewById<TextView>(R.id.tvInfo)

        btnDijkstra.isEnabled = false
        btnAstar.isEnabled = false
        btnCompare.isEnabled = false
        tvInfo.text = "Загрузка маршрута..."

        val req = RouteRequest(
            start_lat = start.latitude,
            start_lon = start.longitude,
            end_lat = end.latitude,
            end_lon = end.longitude
        )

        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    service.getRoute(algorithm, req)
                }
                btnDijkstra.isEnabled = true
                btnAstar.isEnabled = true
                btnCompare.isEnabled = true

                if (response != null) {
                    drawRoute(response, Color.BLUE)
                    tvInfo.text = "Маршрут: %.1f км, %.1f мин, узлов: %d".format(
                        response.distance_km ?: 0.0,
                        response.time_minutes ?: 0.0,
                        response.nodes_visited ?: 0
                    )
                } else {
                    tvInfo.text = "Ошибка построения маршрута"
                    Toast.makeText(this@MainActivity, "Ошибка построения маршрута", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnDijkstra.isEnabled = true
                btnAstar.isEnabled = true
                btnCompare.isEnabled = true
                tvInfo.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestCompare() {
        val start = startPoint
        val end = endPoint

        if (start == null || end == null) {
            Toast.makeText(this, "Сначала выберите точки на карте", Toast.LENGTH_SHORT).show()
            return
        }

        val btnDijkstra = findViewById<Button>(R.id.btnDijkstra)
        val btnAstar = findViewById<Button>(R.id.btnAstar)
        val btnCompare = findViewById<Button>(R.id.btnCompare)
        val tvInfo = findViewById<TextView>(R.id.tvInfo)

        btnDijkstra.isEnabled = false
        btnAstar.isEnabled = false
        btnCompare.isEnabled = false
        tvInfo.text = "Сравнение алгоритмов..."

        val req = RouteRequest(
            start_lat = start.latitude,
            start_lon = start.longitude,
            end_lat = end.latitude,
            end_lon = end.longitude
        )

        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    service.getCompare(req)
                }
                btnDijkstra.isEnabled = true
                btnAstar.isEnabled = true
                btnCompare.isEnabled = true

                if (response != null) {
                    clearRoute()

                    response.dijkstra?.let {
                        drawRoute(it, Color.GREEN)
                    }
                    response.a_star?.let {
                        drawRoute(it, Color.RED)
                    }

                    val diff = response.difference_percent ?: 0.0
                    tvInfo.text = "Дейкстра: %.1f км, %d узлов\nA*: %.1f км, %d узлов\nРазница: %.1f%%".format(
                        response.dijkstra?.distance_km ?: 0.0,
                        response.dijkstra?.nodes_visited ?: 0,
                        response.a_star?.distance_km ?: 0.0,
                        response.a_star?.nodes_visited ?: 0,
                        diff
                    )

                    Toast.makeText(this@MainActivity, "Сравнение выполнено. Разница: ${"%.1f".format(diff)}%", Toast.LENGTH_SHORT).show()
                } else {
                    tvInfo.text = "Ошибка сравнения"
                    Toast.makeText(this@MainActivity, "Ошибка сравнения", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnDijkstra.isEnabled = true
                btnAstar.isEnabled = true
                btnCompare.isEnabled = true
                tvInfo.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawRoute(response: RouteResponse, color: Int) {
        val path = response.path ?: return
        if (path.isEmpty()) return

        val points = mutableListOf<GeoPoint>()
        for (coord in path) {
            if (coord.size >= 2) {
                points.add(GeoPoint(coord[0], coord[1]))
            }
        }

        if (points.isEmpty()) return

        val line = Polyline()
        line.setPoints(points)
        line.setColor(color)
        line.setWidth(5.0f)
        map.overlays.add(line)
        routeLines.add(line)
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
