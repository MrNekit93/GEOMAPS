import json
with open('belarus_graph.json', 'r', encoding='utf-8') as f:
    data = json.load(f)
    # Print first adjacency entry
    first_key = list(data['adjacencyList'].keys())[0]
    print("First adjacency entry:")
    print(f"Key: {first_key}")
    print(f"Edges: {data['adjacencyList'][first_key]}")
    # Check if edges have 'type' field
    if data['adjacencyList'][first_key]:
        print(f"Edge keys: {data['adjacencyList'][first_key][0].keys()}")
