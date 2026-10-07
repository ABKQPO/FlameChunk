package com.hfstudio.flamechunk.client.integration;

import java.util.Collections;

public class ClientMapOverlayState {

    public static volatile MapOverlayModel model = emptyModel();

    public static void publish(MapOverlayModel value) {
        model = value == null ? emptyModel() : value;
    }

    public static void clear() {
        model = emptyModel();
    }

    public static MapOverlayModel get() {
        return model;
    }

    public static MapOverlayModel emptyModel() {
        return new MapOverlayModel(Collections.emptyList());
    }
}
