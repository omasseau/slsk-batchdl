package com.slskbatchdl.Models;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.slskbatchdl.utils.ProgressBar;
import com.slskbatchdl.utils.SearchResponse;
import com.slskbatchdl.utils.SoulseekFile;

public class SearchInfo {
    public ConcurrentMap<String, SearchResponse> results;
    public ProgressBar progress;

    public SearchInfo(ConcurrentMap<String, SearchResponse> results, ProgressBar progress) {
        this.results = results;
        this.progress = progress;
    }
}
