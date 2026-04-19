package smartgrid.model;

/**
 * Weight vector for multi-objective edge cost calculation.
 *
 * effectiveCost = w1 * baseCost
 *               + w2 * carbonCost
 *               + w3 * failureRisk
 *               + w4 * latency
 *
 * Default weights (1, 0, 0, 0) reproduce the original single-objective
 * behavior where only baseCost matters.
 */
public class CostWeights {

    public double w1; // weight for baseCost
    public double w2; // weight for carbonCost
    public double w3; // weight for failureRisk
    public double w4; // weight for latency

    /** Default: pure baseCost — identical to original behavior */
    public CostWeights() {
        this.w1 = 0.1;
        this.w2 = 0.2;
        this.w3 = 0.25;
        this.w4 = 0.15;
    }

    public CostWeights(double w1, double w2, double w3, double w4) {
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
        this.w4 = w4;
    }

    @Override
    public String toString() {
        return String.format("CostWeights(w1=%.2f, w2=%.2f, w3=%.2f, w4=%.2f)", w1, w2, w3, w4);
    }
}
