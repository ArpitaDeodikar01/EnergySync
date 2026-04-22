package smartgrid.model;

import java.awt.Color;

/**
 * Zone classification with exact weight vectors per competition spec.
 *
 * effectiveCost = w1*baseCost + w2*carbonCost + w3*failureRisk + w4*latency
 *
 * Hospital    (0.10, 0.20, 0.70, 0.00) — reliability dominates
 * Industrial  (0.15, 0.60, 0.15, 0.10) — carbon dominates
 * Residential (0.65, 0.15, 0.10, 0.10) — cost dominates
 * Commercial  (0.25, 0.25, 0.25, 0.25) — balanced
 */
public enum ZoneClassification {

    HOSPITAL(
        "Hospital",
        new CostWeights(0.10, 0.20, 0.70, 0.00),
        new Color(220, 53, 69),
        "Reliability-first  w=(0.10, 0.20, 0.70, 0.00)"
    ),
    INDUSTRY(
        "Industry",
        new CostWeights(0.15, 0.60, 0.15, 0.10),
        new Color(255, 193, 7),
        "Carbon-first  w=(0.15, 0.60, 0.15, 0.10)"
    ),
    RESIDENTIAL(
        "Residential",
        new CostWeights(0.65, 0.15, 0.10, 0.10),
        new Color(40, 167, 69),
        "Cost-first  w=(0.65, 0.15, 0.10, 0.10)"
    ),
    COMMERCIAL(
        "Commercial",
        new CostWeights(0.25, 0.25, 0.25, 0.25),
        new Color(0, 188, 212),
        "Balanced  w=(0.25, 0.25, 0.25, 0.25)"
    ),
    UNCLASSIFIED(
        "Unclassified",
        new CostWeights(0.25, 0.25, 0.25, 0.25),
        new Color(108, 117, 125),
        "Balanced  w=(0.25, 0.25, 0.25, 0.25)"
    );

    public final String displayName;
    public final CostWeights weights;
    public final Color color;
    public final String description;

    ZoneClassification(String displayName, CostWeights weights, Color color, String description) {
        this.displayName = displayName;
        this.weights = weights;
        this.color = color;
        this.description = description;
    }

    public String getPriorityBreakdown() {
        return String.format(
            "%s | Cost:%.0f%% Carbon:%.0f%% Risk:%.0f%% Lat:%.0f%%",
            displayName,
            weights.w1 * 100, weights.w2 * 100,
            weights.w3 * 100, weights.w4 * 100
        );
    }
}
