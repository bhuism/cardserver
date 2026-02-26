package nl.appsource.cardserver.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ScreenOrientation {

    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    final String value;
}
