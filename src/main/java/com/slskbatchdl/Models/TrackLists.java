package com.slskbatchdl.Models;

import com.slskbatchdl.Enums.TrackType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class TrackLists {
    public List<TrackListEntry> lists = new ArrayList<>();
    public int getCount() {
        return lists.size();
    }

    public TrackLists() { }

    public static TrackLists fromFlattened(Iterable<Track> flatList) {
        TrackLists res = new TrackLists();
        Iterator<Track> enumerator = flatList.iterator();

        while (enumerator.hasNext()) {
            Track track = enumerator.next();

            if (track.getType() != TrackType.Normal) {
                res.addEntry(new TrackListEntry(track));
            } else {
                res.addEntry(new TrackListEntry(TrackType.Normal));
                res.addTrackToLast(track);

                boolean hasNext;
                while (true) {
                    hasNext = enumerator.hasNext();
                    if (!hasNext || enumerator.next().getType() != TrackType.Normal)
                        break;
                    res.addTrackToLast(enumerator.next());
                }

                if (hasNext)
                    res.addEntry(new TrackListEntry(enumerator.next()));
                else break;
            }
        }

        return res;
    }

    public TrackListEntry get(int index) {
        return lists.get(index);
    }

    public void set(int index, TrackListEntry value) {
        lists.set(index, value);
    }

    public void addEntry(TrackListEntry tle) {
        lists.add(tle);
    }

    public void addTrackToLast(Track track) {
        if (lists.size() == 0) {
            addEntry(new TrackListEntry(new ArrayList<>(Collections.singletonList(new ArrayList<>(Collections.singletonList(track)))), new Track()));
            return;
        }

        int i = lists.size() - 1;

        if (lists.get(i).list.size() == 0) {
            lists.get(i).list.add(new ArrayList<>(Collections.singletonList(track)));
            return;
        }

        int j = lists.get(i).list.size() - 1;
        lists.get(i).list.get(j).add(track);
    }

    public void reverse() {
        Collections.reverse(lists);
        for (TrackListEntry tle : lists) {
            for (List<Track> ls : tle.list) {
                Collections.reverse(ls);
            }
        }
    }

    public void upgradeListTypes(boolean aggregate, boolean album) {
        if (!aggregate && !album)
            return;

        List<TrackListEntry> newLists = new ArrayList<>();

        for (TrackListEntry tle : lists) {
            if (tle.source.getType() == TrackType.Album && aggregate) {
                tle.source.setType(TrackType.AlbumAggregate);
                tle.setDefaults();
                newLists.add(tle);
            } else if (tle.source.getType() == TrackType.Aggregate && album) {
                tle.source.setType(TrackType.AlbumAggregate);
                tle.setDefaults();
                newLists.add(tle);
            } else if (tle.source.getType() == TrackType.Normal && (album || aggregate)) {
                for (Track track : tle.list.get(0)) {
                    if (album && aggregate)
                        track.setType(TrackType.AlbumAggregate);
                    else if (album)
                        track.setType(TrackType.Album);
                    else if (aggregate)
                        track.setType(TrackType.Aggregate);

                    TrackListEntry newTle = new TrackListEntry(track);
                    newTle.defaultFolderName = tle.defaultFolderName;
                    newLists.add(newTle);
                }
            } else {
                newLists.add(tle);
            }
        }

        lists = newLists;
    }

    public void setListEntryOptions() {
        for (TrackListEntry tle : lists) {
            if (tle.source.getType() == TrackType.Aggregate || tle.source.getType() == TrackType.AlbumAggregate)
                tle.defaultFolderName = Path.of(tle.defaultFolderName, tle.source.toString(true)).toString();
        }
    }

    public Iterable<Track> flattened(boolean addSources, boolean addSpecialSourceTracks, boolean sourcesOnly) {
        List<Track> result = new ArrayList<>();
        for (TrackListEntry tle : lists) {
            if ((addSources || sourcesOnly) && tle.source != null && tle.source.getType() != TrackType.Normal)
                result.add(tle.source);
            if (!sourcesOnly && tle.list.size() > 0 && (tle.source.getType() == TrackType.Normal || addSpecialSourceTracks)) {
                result.addAll(tle.list.get(0));
            }
        }
        return result;
    }
}
