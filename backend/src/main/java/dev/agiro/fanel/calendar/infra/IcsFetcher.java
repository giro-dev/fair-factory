package dev.agiro.fanel.calendar.infra;

/** Fetches an ICS document from a URL. */
public interface IcsFetcher {
    String fetch(String url);
}
