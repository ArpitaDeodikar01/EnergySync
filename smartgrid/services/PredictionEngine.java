package smartgrid.services;

import smartgrid.model.*;

import java.util.*;

/**
 * Maintains a sliding window of past failure events and assigns risk scores
 * to nodes, enabling prediction of the next most-likely failure.
 *
 * Data structures:
 *   - Queue<FailureEvent>          : sliding window (FIFO), evicts oldest when full
 *   - Map<Integer, Double>         : accumulated risk score per node ID
 *   - PriorityQueue (max-heap)     : ranks nodes by risk score for O(log n) top-k
 *
 * Risk score formula per node (accumulated over the window):
 *   riskScore += event.severity * decayFactor(age)
 *
 * where decayFactor = e^(-DECAY * ageInSeconds) so recent failures weigh more.
 */
public class PredictionEngine {

    /** Maximum number of failure events retained in the sliding window */
    private static final int WINDOW_SIZE = 20;

    /**
     * Exponential decay constant for time-weighted risk.
     * Higher = recent failures dominate more strongly.
     */
    private static final double DECAY = 0.01;

    /** Sliding window of recent failure events — oldest evicted when full */
    private final Queue<FailureEvent> window = new LinkedList<>();

    /**
     * Accumulated risk score per node ID.
     * Recomputed from scratch on each call to refreshRiskScores()
     * to correctly apply decay based on current time.
     */
    private final Map<Integer, Double> riskScores = new HashMap<>();

    /** Node labels for display — populated from graph nodes */
    private final Map<Integer, String> nodeLabels = new HashMap<>();

    // -------------------------------------------------------------------------
    // Event recording
    // -------------------------------------------------------------------------

    /**
     * Records a new failure event into the sliding window.
     * Evicts the oldest event if the window is full.
     * Refreshes all risk scores after insertion.
     *
     * @param nodeId    ID of the node that failed
     * @param nodeLabel display label of the node
     * @param severity  [0.0, 1.0] — fraction of total zones affected
     */
    public void recordFailure(int nodeId, String nodeLabel, double severity) {
        if (window.size() >= WINDOW_SIZE) {
            window.poll(); // evict oldest
        }
        window.offer(new FailureEvent(nodeId, nodeLabel, System.currentTimeMillis(), severity));
        nodeLabels.put(nodeId, nodeLabel);
        refreshRiskScores();
    }

    // -------------------------------------------------------------------------
    // Risk score computation
    // -------------------------------------------------------------------------

    /**
     * Recomputes risk scores for all nodes from the current window contents.
     * Uses exponential decay so older events contribute less.
     *
     * Time complexity: O(W) where W = window size (≤ WINDOW_SIZE)
     */
    public void refreshRiskScores() {
        riskScores.clear();
        long now = System.currentTimeMillis();

        for (FailureEvent event : window) {
            double ageSeconds = (now - event.timestampMs) / 1000.0;
            double decayFactor = Math.exp(-DECAY * ageSeconds);
            double contribution = event.severity * decayFactor;

            riskScores.merge(event.nodeId, contribution, Double::sum);
        }
    }

    // -------------------------------------------------------------------------
    // Prediction
    // -------------------------------------------------------------------------

    /**
     * Predicts the node most likely to fail next based on accumulated risk scores.
     *
     * Uses a max-heap (PriorityQueue with reversed comparator) to extract the
     * highest-risk node in O(W log W) time.
     *
     * @return the node ID with the highest risk score, or -1 if no data
     */
    public int predictNextFailureNode() {
        if (riskScores.isEmpty()) return -1;

        // Max-heap ordered by risk score descending
        PriorityQueue<Map.Entry<Integer, Double>> maxHeap = new PriorityQueue<>(
                (a, b) -> Double.compare(b.getValue(), a.getValue())
        );
        maxHeap.addAll(riskScores.entrySet());

        return maxHeap.peek().getKey();
    }

    /**
     * Returns the top-k highest-risk node IDs in descending order of risk score.
     *
     * @param k number of nodes to return
     * @return list of node IDs, highest risk first
     */
    public List<Integer> topKRiskyNodes(int k) {
        PriorityQueue<Map.Entry<Integer, Double>> maxHeap = new PriorityQueue<>(
                (a, b) -> Double.compare(b.getValue(), a.getValue())
        );
        maxHeap.addAll(riskScores.entrySet());

        List<Integer> result = new ArrayList<>();
        int count = Math.min(k, maxHeap.size());
        for (int i = 0; i < count; i++) {
            result.add(maxHeap.poll().getKey());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the risk score for a specific node, or 0.0 if not in the window. */
    public double getRiskScore(int nodeId) {
        return riskScores.getOrDefault(nodeId, 0.0);
    }

    /** Returns an unmodifiable view of all current risk scores. */
    public Map<Integer, Double> getAllRiskScores() {
        return Collections.unmodifiableMap(riskScores);
    }

    /** Returns the current failure event window (oldest first). */
    public Collection<FailureEvent> getWindow() {
        return Collections.unmodifiableCollection(window);
    }

    public int getWindowSize()  { return window.size(); }
    public int getMaxWindowSize() { return WINDOW_SIZE; }

    /** Clears all history — called on simulation reset. */
    public void reset() {
        window.clear();
        riskScores.clear();
        nodeLabels.clear();
    }
}
