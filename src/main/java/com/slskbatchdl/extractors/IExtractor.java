package com.slskbatchdl.extractors;

import com.slskbatchdl.Config;
import com.slskbatchdl.models.Track;
import com.slskbatchdl.models.TrackLists;

import java.util.concurrent.CompletableFuture;

public interface IExtractor {
    CompletableFuture<TrackLists> getTracks(String input, int maxTracks, int offset, boolean reverse, Config config);
    default CompletableFuture<Void> removeTrackFromSource(Track track) {
        return CompletableFuture.completedFuture(null);
    }
}
