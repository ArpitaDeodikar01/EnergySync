package smartgrid.model;

/**
 * User roles for the SmartGrid application.
 *
 * ADMIN — full control: fault injection, weight tuning, adaptive mode, reset
 * USER  — read-only observer: can run distribution, view paths, view predictions
 */
public enum UserRole {
    ADMIN("Administrator", "Full control — fault injection, weight tuning, adaptive mode"),
    USER ("Operator",      "Read-only — view distribution, query paths, monitor zones");

    public final String displayName;
    public final String description;

    UserRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
