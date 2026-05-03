"""
Server for comparing route search algorithms (Dijkstra and A*) on Belarus map.
Uses Flask for compatibility with Python 3.14.
Loads graph from belarus_graph.json and provides REST API endpoints.
"""

import json
import math
import heapq
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)  # Enable CORS for Android client

# Global graph storage
nodes = {}
adjacency = {}

# Speed settings
SPEEDS = {
    "car": 60.0,   # 60 km/h
    "fast": 110.0  # 110 km/h
}


def haversine(lat1, lon1, lat2, lon2):
    """Calculate haversine distance between two points in kilometers."""
    R = 6371.0  # Earth radius in km
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)

    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


def find_nearest_node(lat, lon):
    """Find nearest node in graph to given coordinates."""
    min_dist = float("inf")
    nearest = None

    for node_id, node in nodes.items():
        dist = haversine(lat, lon, node["lat"], node["lon"])
        if dist < min_dist:
            min_dist = dist
            nearest = node_id

    return nearest


def reconstruct_path(prev, start, end):
    """Reconstruct path from predecessors dictionary."""
    path = []
    cur = end
    while cur:
        node = nodes[cur]
        path.append([node["lat"], node["lon"]])
        cur = prev.get(cur)
    path.reverse()
    return path


def dijkstra(start, end):
    """Dijkstra's algorithm."""
    dist = {node: float("inf") for node in nodes}
    prev = {}

    dist[start] = 0
    pq = [(0, start)]

    visited_count = 0

    while pq:
        current_dist, u = heapq.heappop(pq)
        visited_count += 1

        if u == end:
            break

        for edge in adjacency.get(u, []):
            v = str(edge["target"])
            weight = edge["weight"]

            alt = current_dist + weight
            if alt < dist[v]:
                dist[v] = alt
                prev[v] = u
                heapq.heappush(pq, (alt, v))

    return dist[end], reconstruct_path(prev, start, end), visited_count


def a_star(start, end):
    """A* algorithm with haversine heuristic."""
    open_set = []
    heapq.heappush(open_set, (0, start))

    g_score = {node: float("inf") for node in nodes}
    g_score[start] = 0

    prev = {}
    visited_count = 0

    end_node = nodes[end]

    while open_set:
        _, current = heapq.heappop(open_set)
        visited_count += 1

        if current == end:
            break

        for edge in adjacency.get(current, []):
            neighbor = str(edge["target"])
            tentative_g = g_score[current] + edge["weight"]

            if tentative_g < g_score[neighbor]:
                g_score[neighbor] = tentative_g
                prev[neighbor] = current

                h = haversine(
                    nodes[neighbor]["lat"],
                    nodes[neighbor]["lon"],
                    end_node["lat"],
                    end_node["lon"]
                )

                f = tentative_g + h
                heapq.heappush(open_set, (f, neighbor))

    return g_score[end], reconstruct_path(prev, start, end), visited_count


@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "nodes_loaded": len(nodes)})


@app.route('/route/<algorithm>', methods=['POST'])
def route(algorithm):
    data = request.get_json()

    start_lat = data.get('start_lat')
    start_lon = data.get('start_lon')
    end_lat = data.get('end_lat')
    end_lon = data.get('end_lon')
    transport = data.get('transport', 'car')
    route_type = data.get('route_type', 'all')

    start = find_nearest_node(start_lat, start_lon)
    end = find_nearest_node(end_lat, end_lon)

    if not start or not end:
        return jsonify({"error": "Invalid points"}), 400

    if algorithm == 'dijkstra':
        dist, path, visited = dijkstra(start, end)
    elif algorithm == 'a_star':
        dist, path, visited = a_star(start, end)
    else:
        return jsonify({"error": "Unknown algorithm"}), 400

    speed = SPEEDS.get(transport, 60)
    time_minutes = (dist / speed) * 60

    return jsonify({
        "distance_km": dist,
        "time_minutes": time_minutes,
        "nodes_visited": visited,
        "path": path,
        "algorithm": algorithm
    })


@app.route('/route/compare', methods=['POST'])
def compare():
    data = request.get_json()

    start_lat = data.get('start_lat')
    start_lon = data.get('start_lon')
    end_lat = data.get('end_lat')
    end_lon = data.get('end_lon')
    transport = data.get('transport', 'car')

    start = find_nearest_node(start_lat, start_lon)
    end = find_nearest_node(end_lat, end_lon)

    if not start or not end:
        return jsonify({"error": "Invalid points"}), 400

    d_dist, d_path, d_visited = dijkstra(start, end)
    a_dist, a_path, a_visited = a_star(start, end)

    speed = SPEEDS.get(transport, 60)
    d_time = (d_dist / speed) * 60
    a_time = (a_dist / speed) * 60

    # Calculate difference percentage
    if d_visited > 0:
        diff_percent = ((d_visited - a_visited) / d_visited) * 100
    else:
        diff_percent = 0

    return jsonify({
        "dijkstra": {
            "distance_km": d_dist,
            "time_minutes": d_time,
            "nodes_visited": d_visited,
            "path": d_path,
            "algorithm": "dijkstra"
        },
        "a_star": {
            "distance_km": a_dist,
            "time_minutes": a_time,
            "nodes_visited": a_visited,
            "path": a_path,
            "algorithm": "a_star"
        },
        "difference_percent": round(diff_percent, 2)
    })


if __name__ == '__main__':
    # Load graph on startup
    try:
        # Put belarus_graph.json in same folder as server.py
        graph_path = "belarus_graph.json"
        print(f"Loading graph from {graph_path}...")
        with open(graph_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            nodes = data["nodes"]
            adjacency = data["adjacencyList"]
        print(f"Graph loaded: {len(nodes)} nodes")
    except Exception as e:
        print(f"Error loading graph: {e}")

    app.run(host='0.0.0.0', port=8000, debug=True)
