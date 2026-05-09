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
import java.util.Calendar

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
        City("Бобруйск", 53.156, 29.247),
        City("Барановичи", 53.130, 26.014),
        City("Борисов", 54.227, 28.506),
        City("Пинск", 52.115, 26.101),
        City("Орша", 54.513, 30.425),
        City("Мозырь", 52.045, 29.253),
        City("Солигорск", 52.8, 27.533),
        City("Новополоцк", 55.533, 28.65),
        City("Лида", 53.891, 25.3),
        City("Молодечно", 54.312, 26.85),
        City("Жлобин", 52.823, 30.016),
        City("Светлогорск", 52.63, 29.73),
        City("Речица", 52.366, 30.383),
        City("Слоним", 53.083, 25.316),
        City("Жодино", 54.103, 28.35),
        City("Кобрин", 52.216, 24.366),
        City("Полоцк", 55.483, 28.8),
        City("Слуцк", 53.016, 27.55),
        City("Калинковичи", 52.116, 29.333),
        City("Горки", 54.283, 30.983),
        City("Волковыск", 53.15, 24.466),
        City("Сморгонь", 54.483, 26.4),
        City("Рогачёв", 53.083, 30.05),
        City("Дзержинск", 53.683, 27.133),
        City("Кричев", 53.767, 31.633),
        City("Лепель", 54.883, 28.683),
        City("Мосты", 53.45, 24.533),
        City("Несвиж", 53.22, 26.683),
        City("Чаусы", 53.783, 31.15),
        City("Столбцы", 53.467, 26.733),
        City("Глубокое", 55.15, 27.7),
        City("Добруш", 52.416, 31.316),
        City("Лунинец", 52.25, 26.8),
        City("Ивацевичи", 52.733, 25.333),
        City("Шклов", 54.383, 30.283),
        City("Берёза", 52.533, 24.983),
        City("Поставы", 55.15, 27.133),
        City("Логойск", 54.2, 27.833),
        City("Смолевичи", 54.033, 27.983),
        City("Вилейка", 54.5, 26.883),
        City("Докшицы", 54.883, 27.95),
        City("Славгород", 53.417, 31.117),
        City("Крупки", 54.333, 29.117),
        City("Чериков", 53.583, 31.4),
        City("Быхов", 53.517, 30.25),
        City("Климовичи", 53.617, 31.967),
        City("Толочин", 54.5, 29.8),
        City("Хойники", 52.15, 30.05),
        City("Петриков", 52.133, 28.467),
        City("Ельск", 51.817, 29.117),
        City("Наровля", 51.783, 29.85),
        City("Лельчицы", 52.283, 28.35),
        City("Брагин", 52.05, 30.283),
        City("Хотимск", 53.433, 32.083),
        City("Краснополье", 53.583, 31.383),
        City("Костюковичи", 53.35, 32.067),
        City("Славгород", 53.417, 31.117),
        City("Чечерск", 53.167, 30.917),
        City("Рославль", 53.95, 32.85)
    )

    private var selectedStartCity: City? = null
    private var selectedEndCity: City? = null
    private var selectedRouteType: String = "all"

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

        val spinnerRouteType = findViewById<Spinner>(R.id.spinnerRouteType)
        val routeTypes = listOf("Все дороги" to "all", "Только магистрали" to "motorway")
        val routeTypeLabels = routeTypes.map { it.first }
        val adapterRouteType = ArrayAdapter(this, android.R.layout.simple_spinner_item, routeTypeLabels)
        adapterRouteType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRouteType.adapter = adapterRouteType

        spinnerRouteType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                selectedRouteType = routeTypes[pos].second
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedRouteType = "all"
            }
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
            end_lon = end.longitude,
            route_type = selectedRouteType
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
                    val distKm = (response.distance_km ?: 0.0).toInt()
                    val arrival = calcArrival(response.travel_hours ?: 0.0)
                    tvInfo.text = "${distKm} км, прибытие: ${arrival}"
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
            end_lon = end.longitude,
            route_type = selectedRouteType
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
                    val dKm = (response.dijkstra?.distance_km ?: 0.0).toInt()
                    val aKm = (response.a_star?.distance_km ?: 0.0).toInt()
                    val dArrival = calcArrival(response.dijkstra?.travel_hours ?: 0.0)
                    val aArrival = calcArrival(response.a_star?.travel_hours ?: 0.0)
                    tvInfo.text = "Дейкстра: ${dKm} км, прибытие: ${dArrival}\nA*: ${aKm} км, прибытие: ${aArrival}\nУзлов: ${response.dijkstra?.nodes_visited ?: 0} vs ${response.a_star?.nodes_visited ?: 0}"

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

    private fun calcArrival(travelHours: Double): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, (travelHours * 60).toInt())
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mm = cal.get(Calendar.MINUTE)
        return "${"%02d".format(hh)}:${"%02d".format(mm)}"
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
