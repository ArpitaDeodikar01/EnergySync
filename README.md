# SmartGrid — Adaptive Energy Distribution Simulator

A real-time energy network simulator built for a DSA competition. Every algorithm is implemented from scratch — no libraries. The UI lets you watch the network route power, break it, and recover, all live.

---

## What is this?

Imagine a city powered by Solar, Wind, Hydro, and Nuclear sources. Energy flows through substations into zones — a Hospital, an Industrial area, Residential neighbourhoods, and a Commercial district. This simulator models that entire network as a weighted directed graph and uses 10 DSA components to manage, optimise, and recover energy distribution in real time.

---

## How to Run

**Requirements:** Java 8+

```bash
# Compile
javac -cp . smartgrid/model/*.java smartgrid/ds/*.java smartgrid/algorithms/*.java smartgrid/services/*.java smartgrid/ui/*.java smartgrid/Main.java

# Run
java -cp . smartgrid.Main
```

---

## The Network Topology

```
SOURCES          SUBSTATIONS       CITY ZONES
Solar    ──────► Sub-A ──────────► Zone-1  [Hospital]     🔴 Reliability-first
Wind     ──────► Sub-B ──────────► Zone-2  [Industry]     🟡 Carbon-first
Hydro    ──────► Sub-C ──────────► Zone-3  [Residential]  🟢 Cost-first
Nuclear  ──────► Sub-D ──────────► Zone-4  [Residential]  🟢 Cost-first
                 Sub-E ──────────► Zone-5  [Commercial]   🔵 Balanced

Cross-links: Sub-A ↔ Sub-B ↔ Sub-C  (backup rerouting paths)
Nuclear → Sub-D → Zone-1  (dedicated zero-carbon Hospital feeder)
```

**14 nodes, 22 edges.** Every zone has at least 2 supply paths for redundancy.

---

## Zone Priority System

Each zone type has a different optimization goal, encoded as a weight vector `(w1, w2, w3, w4)` applied to routing decisions:

| Zone | Type | Priority | Weights |
|------|------|----------|---------|
| Zone-1 | Hospital | Reliability — never go dark | w3 (failure risk) = 0.70 |
| Zone-2 | Industry | CO₂ minimisation | w2 (carbon) = 0.60 |
| Zone-3/4 | Residential | Cheapest energy | w1 (cost) = 0.65 |
| Zone-5 | Commercial | Balanced | equal weights |

When you **click a zone**, Dijkstra runs from every active source *to that zone* using that zone's own weights — so clicking Hospital shows the most reliable path, clicking Industry shows the lowest-carbon path.

---

## Features

### 1. Run Distribution
Runs **Min-Cost Max-Flow (MCMF)** across the entire network. Energy flows from all sources through substations to all zones simultaneously. Flow amounts appear on every edge (`flow/capacity`). Animated green dots show energy moving in real time.

### 2. Inject Fault
Select any node (click it on the canvas, or pick from the dropdown). The node is removed from the network. **BFS** propagates to find which zones lose power. **DFS** isolates the damaged subgraph. Affected zones turn red.

### 3. Auto Reroute
After a fault, re-runs MCMF on the surviving network. Energy finds alternate paths through the cross-substation backup links. Recovered zones turn orange. The stats bar shows recovery time and flow loss percentage.

### 4. Dijkstra Path (button)
Pick any source and destination from dropdowns. Highlights the minimum-cost path on the canvas with a glowing blue trail. Shows path cost in the stats bar.

### 5. Click Any Node
- **Click a source/substation** → shows the shortest path to the nearest zone
- **Click a zone** → shows the best incoming supply path using that zone's priority weights (Hospital gets reliability-optimised path, Industry gets carbon-optimised path, etc.)

### 6. Run Prediction
Uses a **sliding window + exponential decay** model to predict which node is most likely to fail next, based on fault history. More recent faults weigh more heavily. Shows the highest-risk node and its risk score.

### 7. Adaptive Mode (toggle)
When ON, edge costs update automatically after each run based on observed performance — congested or frequently-failing edges become more expensive, efficient edges become cheaper. The network learns over time.

### 8. Cost Weight Sliders
Four sliders control the global routing formula:
```
effectiveCost = w1×baseCost + w2×carbonCost + w3×failureRisk + w4×latency
```
Drag them live and re-run distribution to see how routing changes.

### 9. Live Fenwick Prefix Sums
The control panel shows cumulative energy loads `Z1..1`, `Z1..2`, ... `Z1..5` queried from a **Fenwick Tree (BIT)** in O(log n). Updates after every distribution run.

### 10. Risk Bars
Every node has a small coloured bar below it showing its current risk score. After faults, bars fill red. They decay over time as the sliding window ages out old events.

### 11. Load Bars
Every city zone shows a green/yellow/red bar indicating how full its capacity is. Turns red when over 75% loaded.

### 12. Heatmap Bands
Edges glow blue→red based on congestion (flow/capacity ratio). Heavily loaded transmission lines are immediately visible.

---

## DSA Components — What Each One Does

| Component | Where Used | Why |
|-----------|-----------|-----|
| **Min-Cost Max-Flow (SPFA)** | Run Distribution, Auto Reroute | Optimally routes energy across the whole network at minimum cost |
| **Dijkstra (min-heap)** | Click routing, Dijkstra button | Finds shortest/cheapest path between any two nodes |
| **BFS** | Fault injection | Finds all zones cut off by a fault |
| **DFS** | Fault injection | Isolates the damaged subgraph |
| **Segment Tree** | Zone load queries | Range sum and max-load zone in O(log n) |
| **Fenwick Tree (BIT)** | Cumulative energy tracking | Prefix sums of zone loads in O(log n) |
| **Union-Find (path compression + union-by-rank)** | Connectivity detection | Checks if nodes are still connected after faults in O(α(n)) |
| **Min-Heap (PriorityQueue)** | Load balancing | Orders energy sources by cost |
| **Sliding window + max-heap** | Prediction Engine | Time-weighted failure prediction |
| **Weighted directed graph** | Everything | The core data structure the entire simulation runs on |

---

## How a Typical Demo Flows

1. **Launch** → see the 14-node network
2. **Run Distribution** → watch energy flow to all 5 zones, load bars fill, Fenwick sums update
3. **Click Zone-1 (Hospital)** → see the Nuclear→Sub-D→Zone-1 reliability path highlighted
4. **Click Zone-2 (Industry)** → see the lowest-carbon Solar path highlighted
5. **Inject Fault on Sub-A** → Hospital and Industry zones go red
6. **Auto Reroute** → network recovers via backup paths, zones turn orange, stats show recovery time
7. **Run Prediction** → system predicts Sub-A is high risk (it just failed)
8. **Toggle Adaptive OFF/ON** → explain how edge costs self-update
9. **Drag w2 (Carbon) slider to max** → re-run distribution, routing shifts to Solar/Wind paths

---

## Project Structure

```
smartgrid/
├── model/          — Node, Edge, Graph, ZoneClassification, CostWeights, SimStats
├── algorithms/     — MCMFAlgorithm, DijkstraAlgorithm, BFSAlgorithm, DFSAlgorithm
├── ds/             — FenwickTree, SegmentTree, UnionFind
├── services/       — SimulationEngine, WeightUpdateService, PredictionEngine
├── ui/             — GridUI, GraphCanvas, NodeDetailPanel
└── Main.java       — entry point
```
