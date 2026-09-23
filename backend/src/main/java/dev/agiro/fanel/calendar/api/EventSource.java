package dev.agiro.fanel.calendar.api;

/** Where a calendar event comes from. */
public enum EventSource {
    /** Created/edited locally. */
    LOCAL,
    /** Imported from an external ICS subscription; read-only. */
    ICS
}
