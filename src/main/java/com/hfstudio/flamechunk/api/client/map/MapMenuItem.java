package com.hfstudio.flamechunk.api.client.map;

import java.util.Objects;

public record MapMenuItem(String label, Runnable action) {

    public MapMenuItem {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(action, "action");
    }
}
