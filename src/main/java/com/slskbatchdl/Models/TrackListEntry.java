package com.slskbatchdl.Models;

import com.slskbatchdl.Enums.TrackType;
import com.slskbatchdl.Config;
import com.slskbatchdl.FileSkipper;
import com.slskbatchdl.M3uEditor;
import com.slskbatchdl.FileConditions;

import java.util.List;

public class TrackListEntry {
    public List<List<Track>> list;
    public Track source;
    public boolean needSourceSearch = false;
    public boolean sourceCanBeSkipped = false;
    public boolean needSkipExistingAfterSearch = false;
    public boolean gotoNextAfterSearch = false;
    public boolean enablesIndexByDefault = false;
    public String defaultFolderName = null;

    public Config config = null;
    public FileConditions extractorCond = null;
    public FileConditions extractorPrefCond = null;
    public M3uEditor playlistEditor = null;
    public M3uEditor indexEditor = null;
    public FileSkipper outputDirSkipper = null;
    public FileSkipper musicDirSkipper = null;

    public boolean CanParallelSearch() {
        return source.Type == TrackType.Album || source.Type == TrackType.Aggregate;
    }

    public TrackListEntry(TrackType trackType) {
        list = new java.util.ArrayList<>();
        this.source = new Track();
        this.source.Type = trackType;
        SetDefaults();
    }

    public TrackListEntry(Track source) {
        list = new java.util.ArrayList<>();
        this.source = source;
        SetDefaults();
    }

    public TrackListEntry(List<List<Track>> list, Track source) {
        this.list = list;
        this.source = source;
        SetDefaults();
    }

    public TrackListEntry(List<List<Track>> list, Track source, boolean needSourceSearch, boolean sourceCanBeSkipped,
                          boolean needSkipExistingAfterSearch, boolean gotoNextAfterSearch, String defaultFolderName) {
        this.list = list;
        this.source = source;
        this.needSourceSearch = needSourceSearch;
        this.sourceCanBeSkipped = sourceCanBeSkipped;
        this.needSkipExistingAfterSearch = needSkipExistingAfterSearch;
        this.gotoNextAfterSearch = gotoNextAfterSearch;
        this.defaultFolderName = defaultFolderName;
    }

    public void SetDefaults() {
        needSourceSearch = source.Type != TrackType.Normal;
        needSkipExistingAfterSearch = source.Type == TrackType.Aggregate;
        gotoNextAfterSearch = source.Type == TrackType.AlbumAggregate;
        sourceCanBeSkipped = source.Type != TrackType.Normal
                && source.Type != TrackType.Aggregate
                && source.Type != TrackType.AlbumAggregate;
    }

    public void AddTrack(Track track) {
        if (list == null) {
            list = new java.util.ArrayList<>();
            list.add(new java.util.ArrayList<>());
            list.get(0).add(track);
        } else if (list.size() == 0) {
            list.add(new java.util.ArrayList<>());
            list.get(0).add(track);
        } else {
            list.get(0).add(track);
        }
    }
}
