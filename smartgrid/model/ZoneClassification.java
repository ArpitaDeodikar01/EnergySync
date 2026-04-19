package smartgrid.model;

import java.awt.Color;

/**
 * Zone classification enum defining priority-based cost weight profiles.
 * Each zone type has different optimization priorities reflected in its weight vector.
 */
public enum ZoneClassification {

    /**
     * Zone 1: Hospitals / Critical Infrastructure
     * Priority: Reliability (minimize failure risk)
     * Don't care about: CO2 emissions, energy cost
     */
    HOSPITAL(
        "Hospital",
        new CostWeights(0.1, 0.05, 0.7, 0.15),
        new Color(220, 53, 69),
        "Reliability-focused: Minimize failure risk"
    ),

    /**
     * Zone 2: Industrial Areas
     * Priority: CO2 minimization (environmental compliance)
     */
    INDUSTRY(
        "Industry",
        new CostWeights(0.2, 0.6, 0.15, 0.05),
        new Color(255, 193, 7),
        "Carbon-focused: Minimize CO\u2082 emissions"
    ),

    /**
     * Zone 3: Residential Areas
     * Priority: Minimum energy cost (consumer savings)
     */
    RESIDENTIAL(
        "Residential",
        new CostWeights(0.65, 0.1, 0.2, 0.05),
        new Color(40, 167, 69),
        "Cost-focused: Minimize energy cost"
    ),

    /** Default/Unclassified zone — balanced weights */
    UNCLASSIFIED(
        "Unclassified",
        new CostWeights(0.25, 0.25, 0.25, 0.25),
        new Color(108, 117, 125),
        "Balanced: Equal priority on all factors"
    ),

    /**
     * Zone 5: Commercial / Business Districts
     * Priority: Balanced — cost matters but so does reliability
     */
    COMMERCIAL(
        "Commercial",
        new CostWeights(0.35, 0.25, 0.3, 0.1),
        new Color(0, 188, 212),
        "Balanced: Cost + reliability for business"
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

    /** Returns a formatted string showing the zone's priority breakdown. */
    public String getPriorityBreakdown() {
        return String.format(
            "%s | Cost: %.0f%% | Carbon: %.0f%% | Reliability: %.0f%% | Latency: %.0f%%",
            displayName,
            weights.w1 * 100,
            weights.w2 * 100,
            weights.w3 * 100,
            weights.w4 * 100
        );
    }
}
