package com.hfstudio.flamechunk.client.integration;

import com.hfstudio.flamechunk.common.data.ScanSnapshot;

public interface MapOverlaySink {

    void publish(MapOverlayModel model);

    void clear();

    default void publishSnapshot(ScanSnapshot snapshot) {
        publish(MapOverlayModel.from(snapshot));
    }
}
