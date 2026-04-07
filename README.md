# EnergySync — SmartGrid

Hey! So basically what we built here is a simulation of a city's power grid — think solar panels, wind turbines, and hydro plants all feeding electricity into a city through a bunch of substations.

The whole thing is modeled as a graph (nodes = power sources / substations / city zones, edges = transmission lines). We then run a bunch of classic DSA algorithms on top of it:

- **Min-Cost Max-Flow** — figures out the most efficient way to route electricity so every zone gets power at the lowest possible cost
- **Dijkstra** — click any node in the UI and it shows you the cheapest path to the nearest city zone
- **BFS + DFS** — when a power source fails, BFS finds all the zones that lost power, and DFS maps out exactly which part of the grid is broken
- **Fenwick Tree** — tracks how much energy each zone has consumed over time, super efficiently
- **Min-Heap** — always picks the cheapest available energy source first (solar before wind before hydro)

## The demo flow

1. Hit **Run Normal Distribution** → grid powers up, all zones go green
2. Hit **Inject Fault** → Solar node dies, affected zones turn red
3. Hit **Auto Reroute** → system finds alternate paths, zones turn orange, stats show recovery time
4. Hit **Reset** → back to the start

## How to run

```bash
# Compile
javac smartgrid/*.java

# Run
java smartgrid.Main
```

Just needs Java 8+. No external libraries, no build tools — pure Java.

## Files

```
smartgrid/
  Main.java          ← entry point
  GridSimulator.java ← all the algorithms live here
  GridUI.java        ← Swing UI (graph canvas + buttons + stats bar)
  Graph.java         ← adjacency list graph
  Node.java          ← graph vertex (source / substation / zone)
  Edge.java          ← directed weighted edge
  FenwickTree.java   ← BIT for energy tracking
  NodeType.java      ← enum: ENERGY_SOURCE, SUBSTATION, CITY_ZONE
  NodeStatus.java    ← enum: ACTIVE, FAULT, ISOLATED, REROUTED
  SimStats.java      ← value object for stats bar metrics
```
