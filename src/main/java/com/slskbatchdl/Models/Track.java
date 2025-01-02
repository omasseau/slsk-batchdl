package com.slskbatchdl.Models;

import com.slskbatchdl.Enums.FailureReason;
import com.slskbatchdl.Enums.TrackState;
import com.slskbatchdl.Enums.TrackType;
import com.slskbatchdl.Utils;
import com.slskbatchdl.Soulseek.SearchResponse;
import com.slskbatchdl.Soulseek.File;

import java.util.List;
import java.util.Objects;

public class Track {
    public String Title = "";
    public String Artist = "";
    public String Album = "";
    public String URI = "";
    public int Length = -1;
    public boolean ArtistMaybeWrong = false;
    public int MinAlbumTrackCount = -1;
    public int MaxAlbumTrackCount = -1;
    public boolean IsNotAudio = false;
    public String DownloadPath = "";
    public String Other = "";
    public int CsvRow = -1;
    public TrackType Type = TrackType.Normal;
    public FailureReason FailureReason = FailureReason.None;
    public TrackState State = TrackState.Initial;
    public List<SearchResponse.File> Downloads = null;

    public boolean OutputsDirectory() {
        return Type != TrackType.Normal;
    }

    public File FirstDownload() {
        return Downloads != null && !Downloads.isEmpty() ? Downloads.get(0).Item2 : null;
    }

    public SearchResponse FirstResponse() {
        return Downloads != null && !Downloads.isEmpty() ? Downloads.get(0).Item1 : null;
    }

    public String FirstUsername() {
        return Downloads != null && !Downloads.isEmpty() ? Downloads.get(0).Item1.Username : null;
    }

    public Track() {
    }

    public Track(Track other) {
        this.Title = other.Title;
        this.Artist = other.Artist;
        this.Album = other.Album;
        this.Length = other.Length;
        this.URI = other.URI;
        this.ArtistMaybeWrong = other.ArtistMaybeWrong;
        this.Downloads = other.Downloads;
        this.Type = other.Type;
        this.IsNotAudio = other.IsNotAudio;
        this.State = other.State;
        this.FailureReason = other.FailureReason;
        this.DownloadPath = other.DownloadPath;
        this.Other = other.Other;
        this.MinAlbumTrackCount = other.MinAlbumTrackCount;
        this.MaxAlbumTrackCount = other.MaxAlbumTrackCount;
        // this.CsvRow = other.CsvRow;
    }

    public String ToKey() {
        if (Type == TrackType.Album) {
            return String.format("%s;%s;%d", Artist, Album, Type.ordinal());
        } else {
            return String.format("%s;%s;%s;%d;%d", Artist, Album, Title, Length, Type.ordinal());
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean noInfo) {
        if (IsNotAudio && Downloads != null && !Downloads.isEmpty()) {
            return Utils.GetFileNameSlsk(Downloads.get(0).Item2.Filename);
        }

        String str = Artist;
        if (Type == TrackType.Normal && Title.isEmpty() && Downloads != null && !Downloads.isEmpty()) {
            str = Utils.GetFileNameSlsk(Downloads.get(0).Item2.Filename);
        } else if (!Title.isEmpty() || !Album.isEmpty()) {
            if (!str.isEmpty()) {
                str += " - ";
            }
            if (Type == TrackType.Album) {
                if (!Album.isEmpty()) {
                    str += Album;
                } else {
                    str += Title;
                }
            } else {
                if (!Title.isEmpty()) {
                    str += Title;
                } else {
                    str += Album;
                }
            }
            if (!noInfo) {
                if (Length > 0) {
                    str += String.format(" (%ds)", Length);
                }
                if (Type == TrackType.Album) {
                    str += " (album)";
                }
            }
        } else if (!noInfo) {
            str += " (artist)";
        }

        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Track track = (Track) o;
        return Length == track.Length &&
                ArtistMaybeWrong == track.ArtistMaybeWrong &&
                MinAlbumTrackCount == track.MinAlbumTrackCount &&
                MaxAlbumTrackCount == track.MaxAlbumTrackCount &&
                IsNotAudio == track.IsNotAudio &&
                CsvRow == track.CsvRow &&
                Objects.equals(Title, track.Title) &&
                Objects.equals(Artist, track.Artist) &&
                Objects.equals(Album, track.Album) &&
                Objects.equals(URI, track.URI) &&
                Type == track.Type &&
                FailureReason == track.FailureReason &&
                State == track.State &&
                Objects.equals(Downloads, track.Downloads) &&
                Objects.equals(DownloadPath, track.DownloadPath) &&
                Objects.equals(Other, track.Other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Title, Artist, Album, URI, Length, ArtistMaybeWrong, MinAlbumTrackCount, MaxAlbumTrackCount, IsNotAudio, DownloadPath, Other, CsvRow, Type, FailureReason, State, Downloads);
    }
}

class TrackComparer implements java.util.Comparator<Track> {
    private boolean ignoreCase = false;
    private int lenTol = -1;

    public TrackComparer(boolean ignoreCase, int lenTol) {
        this.ignoreCase = ignoreCase;
        this.lenTol = lenTol;
    }

    @Override
    public int compare(Track a, Track b) {
        if (a.equals(b)) return 0;

        StringComparison comparer = ignoreCase ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;

        if (stringEquals(a.Title, b.Title, comparer) &&
                stringEquals(a.Artist, b.Artist, comparer) &&
                stringEquals(a.Album, b.Album, comparer) &&
                (lenTol == -1 || (a.Length == -1 && b.Length == -1) || (a.Length != -1 && b.Length != -1 && Math.abs(a.Length - b.Length) <= lenTol))) {
            return 0;
        }

        return -1;
    }

    private boolean stringEquals(String a, String b, StringComparison comparer) {
        return comparer == StringComparison.OrdinalIgnoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
    }
}
