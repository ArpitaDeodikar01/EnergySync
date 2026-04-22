package smartgrid.model;

/**
 * Enum discriminator for Graph vertices.
 * Classifies each Node as an energy producer, intermediate router, or consumer sink.
 */
public enum NodeType {
    ENERGY_SOURCE,
    SUBSTATION,
    CITY_ZONE
}
