package smartgrid.model;

/**
 * Immutable record of a single node failure event.
 * Stored in the PredictionEngine's sliding window (Queue).
 *
 * The sliding window evicts events older than WINDOW_SIZE to keep
 * risk scores reflecting recent behavior rather than historical averages.
 */
public class FailureEvent {

    /** ID of the node that failed */
    public final int nodeId;

    /** Label of the node (for logging / UI display) */
    public final String nodeLabel;

    /** System time (ms) when the failure was recorded */
    public final long timestampMs;

    /**
     * Severity of the failure [0.0, 1.0].
     * Derived from how many zones were affected relative to total zones.
     */
    public final double severity;

    public FailureEvent(int nodeId, String nodeLabel, long timestampMs, double severity) {
        this.nodeId = nodeId;
        this.nodeLabel = nodeLabel;
        this.timestampMs = timestampMs;
        this.severity = severity;
    }

    @Override
    public String toString() {
        return String.format("FailureEvent(node=%s, severity=%.2f, t=%d)",
                nodeLabel, severity, timestampMs);
    }
}
