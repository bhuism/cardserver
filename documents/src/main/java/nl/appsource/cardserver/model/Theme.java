package nl.appsource.cardserver.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Theme {

    AUTO("auto"),
    LIGHT("light"),
    DARK("dark");

    final String value;
}
