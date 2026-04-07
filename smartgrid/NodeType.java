package smartgrid;

/**
 * DSA Role: Enum discriminator for Graph vertices.
 * Classifies each Node as an energy producer, intermediate router, or consumer sink.
 * Used by MCMF to identify SuperSource/SuperSink attachment points,
 * by BFS/DFS to determine fault scope, and by GridUI for color coding.
 */
public enum NodeType {
    /** Produces energy — Solar, Wind, or Hydro */
    ENERGY_SOURCE,
    /** Routes energy between sources and zones */
    SUBSTATION,
    /** Consumes energy — final sink in the flow network */
    CITY_ZONE
}
