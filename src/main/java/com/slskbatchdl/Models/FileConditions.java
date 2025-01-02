package com.slskbatchdl.Models;

import com.slskbatchdl.Soulseek.SearchResponse;
import com.slskbatchdl.Utils;
import com.slskbatchdl.Enums.TrackType;
import com.slskbatchdl.Enums.TrackState;
import com.slskbatchdl.Models.Track;
import com.slskbatchdl.Models.SimpleFile;

import java.util.Arrays;
import java.util.Objects;

public class FileConditions {
    public Integer LengthTolerance;
    public Integer MinBitrate;
    public Integer MaxBitrate;
    public Integer MinSampleRate;
    public Integer MaxSampleRate;
    public Integer MinBitDepth;
    public Integer MaxBitDepth;
    public Boolean StrictTitle;
    public Boolean StrictArtist;
    public Boolean StrictAlbum;
    public String[] Formats;
    public String[] BannedUsers;
    public Boolean AcceptNoLength;
    public Boolean AcceptMissingProps;

    public FileConditions() { }

    public FileConditions(FileConditions other) {
        this.LengthTolerance = other.LengthTolerance;
        this.MinBitrate = other.MinBitrate;
        this.MaxBitrate = other.MaxBitrate;
        this.MinSampleRate = other.MinSampleRate;
        this.MaxSampleRate = other.MaxSampleRate;
        this.MinBitDepth = other.MinBitDepth;
        this.MaxBitDepth = other.MaxBitDepth;
        this.StrictTitle = other.StrictTitle;
        this.StrictArtist = other.StrictArtist;
        this.StrictAlbum = other.StrictAlbum;
        this.Formats = other.Formats != null ? Arrays.copyOf(other.Formats, other.Formats.length) : null;
        this.BannedUsers = other.BannedUsers != null ? Arrays.copyOf(other.BannedUsers, other.BannedUsers.length) : null;
        this.AcceptNoLength = other.AcceptNoLength;
        this.AcceptMissingProps = other.AcceptMissingProps;
    }

    public FileConditions with(FileConditions other) {
        FileConditions res = new FileConditions(this);
        res.addConditions(other);
        return res;
    }

    public FileConditions addConditions(FileConditions mod) {
        FileConditions undoMod = new FileConditions();

        if (mod.LengthTolerance != null) {
            undoMod.LengthTolerance = this.LengthTolerance;
            this.LengthTolerance = mod.LengthTolerance;
        }
        if (mod.MinBitrate != null) {
            undoMod.MinBitrate = this.MinBitrate;
            this.MinBitrate = mod.MinBitrate;
        }
        if (mod.MaxBitrate != null) {
            undoMod.MaxBitrate = this.MaxBitrate;
            this.MaxBitrate = mod.MaxBitrate;
        }
        if (mod.MinSampleRate != null) {
            undoMod.MinSampleRate = this.MinSampleRate;
            this.MinSampleRate = mod.MinSampleRate;
        }
        if (mod.MaxSampleRate != null) {
            undoMod.MaxSampleRate = this.MaxSampleRate;
            this.MaxSampleRate = mod.MaxSampleRate;
        }
        if (mod.MinBitDepth != null) {
            undoMod.MinBitDepth = this.MinBitDepth;
            this.MinBitDepth = mod.MinBitDepth;
        }
        if (mod.MaxBitDepth != null) {
            undoMod.MaxBitDepth = this.MaxBitDepth;
            this.MaxBitDepth = mod.MaxBitDepth;
        }
        if (mod.StrictTitle != null) {
            undoMod.StrictTitle = this.StrictTitle;
            this.StrictTitle = mod.StrictTitle;
        }
        if (mod.StrictArtist != null) {
            undoMod.StrictArtist = this.StrictArtist;
            this.StrictArtist = mod.StrictArtist;
        }
        if (mod.StrictAlbum != null) {
            undoMod.StrictAlbum = this.StrictAlbum;
            this.StrictAlbum = mod.StrictAlbum;
        }
        if (mod.Formats != null) {
            undoMod.Formats = this.Formats;
            this.Formats = mod.Formats;
        }
        if (mod.BannedUsers != null) {
            undoMod.BannedUsers = this.BannedUsers;
            this.BannedUsers = mod.BannedUsers;
        }
        if (mod.AcceptNoLength != null) {
            undoMod.AcceptNoLength = this.AcceptNoLength;
            this.AcceptNoLength = mod.AcceptNoLength;
        }
        if (mod.AcceptMissingProps != null) {
            undoMod.AcceptMissingProps = this.AcceptMissingProps;
            this.AcceptMissingProps = mod.AcceptMissingProps;
        }

        return undoMod;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        FileConditions other = (FileConditions) obj;

        return Objects.equals(LengthTolerance, other.LengthTolerance)
                && Objects.equals(MinBitrate, other.MinBitrate)
                && Objects.equals(MaxBitrate, other.MaxBitrate)
                && Objects.equals(MinSampleRate, other.MinSampleRate)
                && Objects.equals(MaxSampleRate, other.MaxSampleRate)
                && Objects.equals(MinBitDepth, other.MinBitDepth)
                && Objects.equals(MaxBitDepth, other.MaxBitDepth)
                && Objects.equals(StrictTitle, other.StrictTitle)
                && Objects.equals(StrictArtist, other.StrictArtist)
                && Objects.equals(StrictAlbum, other.StrictAlbum)
                && Objects.equals(AcceptNoLength, other.AcceptNoLength)
                && Objects.equals(AcceptMissingProps, other.AcceptMissingProps)
                && Arrays.equals(Formats, other.Formats)
                && Arrays.equals(BannedUsers, other.BannedUsers);
    }

    public void unsetClientSpecificFields() {
        this.MinBitrate = null;
        this.MaxBitrate = null;
        this.MinSampleRate = null;
        this.MaxSampleRate = null;
        this.MinBitDepth = null;
        this.MaxBitDepth = null;
    }

    public boolean fileSatisfies(com.slskbatchdl.Soulseek.File file, Track track, SearchResponse response) {
        return formatSatisfies(file.Filename)
                && lengthToleranceSatisfies(file, track.Length) && bitrateSatisfies(file) && sampleRateSatisfies(file)
                && strictTitleSatisfies(file.Filename, track.Title) && strictArtistSatisfies(file.Filename, track.Artist)
                && strictAlbumSatisfies(file.Filename, track.Album) && bannedUsersSatisfies(response) && bitDepthSatisfies(file);
    }

    public boolean fileSatisfies(TagLib.File file, Track track, boolean filenameChecks) {
        return formatSatisfies(file.getName())
                && lengthToleranceSatisfies(file, track.Length) && bitrateSatisfies(file) && sampleRateSatisfies(file)
                && bitDepthSatisfies(file) && (!filenameChecks || strictTitleSatisfies(file.getName(), track.Title)
                && strictArtistSatisfies(file.getName(), track.Artist) && strictAlbumSatisfies(file.getName(), track.Album));
    }

    public boolean fileSatisfies(SimpleFile file, Track track, boolean filenameChecks) {
        return formatSatisfies(file.Path)
                && lengthToleranceSatisfies(file, track.Length) && bitrateSatisfies(file) && sampleRateSatisfies(file)
                && bitDepthSatisfies(file) && (!filenameChecks || strictTitleSatisfies(file.Path, track.Title)
                && strictArtistSatisfies(file.Path, track.Artist) && strictAlbumSatisfies(file.Path, track.Album));
    }

    public boolean strictTitleSatisfies(String fname, String tname, boolean noPath) {
        if (StrictTitle == null || !StrictTitle || tname.isEmpty()) {
            return true;
        }

        fname = noPath ? Utils.getFileNameWithoutExtSlsk(fname) : fname;
        return strictString(fname, tname, true, true);
    }

    public boolean strictArtistSatisfies(String fname, String aname) {
        if (StrictArtist == null || !StrictArtist || aname.isEmpty()) {
            return true;
        }

        return strictString(fname, aname, true, true, false);
    }

    public boolean strictAlbumSatisfies(String fname, String alname) {
        if (StrictAlbum == null || !StrictAlbum || alname.isEmpty()) {
            return true;
        }

        return strictString(Utils.getDirectoryNameSlsk(fname), alname, true, true, true);
    }

    public static String strictStringPreprocess(String str, boolean diacrRemove) {
        str = str.replace('_', ' ').replaceAll("[^a-zA-Z0-9\\s]", " ");
        str = diacrRemove ? Utils.removeDiacritics(str) : str;
        str = str.trim().replaceAll("\\s+", " ");
        return str;
    }

    public static boolean strictString(String fname, String tname, boolean diacrRemove, boolean ignoreCase, boolean boundarySkipWs) {
        if (tname.isEmpty()) {
            return true;
        }

        fname = strictStringPreprocess(fname, diacrRemove);
        tname = strictStringPreprocess(tname, diacrRemove);

        if (boundarySkipWs) {
            return Utils.containsWithBoundaryIgnoreWs(fname, tname, ignoreCase, true);
        } else {
            return Utils.containsWithBoundary(fname, tname, ignoreCase);
        }
    }

    public static boolean bracketCheck(Track track, Track other) {
        String t1 = track.Title.replace("ft.", "").replace('[', '(');
        if (t1.contains("(")) {
            return true;
        }

        String t2 = other.Title.replace("ft.", "").replace('[', '(');
        if (!t2.contains("(")) {
            return true;
        }

        return false;
    }

    public boolean formatSatisfies(String fname) {
        if (Formats == null || Formats.length == 0) {
            return true;
        }

        String ext = Utils.getFileExtension(fname).toLowerCase();
        return ext.length() > 0 && Arrays.stream(Formats).anyMatch(f -> f.equals(ext));
    }

    public boolean lengthToleranceSatisfies(com.slskbatchdl.Soulseek.File file, int wantedLength) {
        return lengthToleranceSatisfies(file.Length, wantedLength);
    }

    public boolean lengthToleranceSatisfies(TagLib.File file, int wantedLength) {
        return lengthToleranceSatisfies((int) file.getLength(), wantedLength);
    }

    public boolean lengthToleranceSatisfies(SimpleFile file, int wantedLength) {
        return lengthToleranceSatisfies(file.Length, wantedLength);
    }

    public boolean lengthToleranceSatisfies(Integer length, int wantedLength) {
        if (LengthTolerance == null || LengthTolerance < 0 || wantedLength < 0) {
            return true;
        }
        if (length == null || length < 0) {
            return AcceptNoLength == null || AcceptNoLength;
        }
        return Math.abs(length - wantedLength) <= LengthTolerance;
    }

    public boolean bitrateSatisfies(com.slskbatchdl.Soulseek.File file) {
        return bitrateSatisfies(file.BitRate);
    }

    public boolean bitrateSatisfies(TagLib.File file) {
        return bitrateSatisfies(file.getProperties().getAudioBitrate());
    }

    public boolean bitrateSatisfies(SimpleFile file) {
        return bitrateSatisfies(file.Bitrate);
    }

    public boolean bitrateSatisfies(Integer bitrate) {
        return boundCheck(bitrate, MinBitrate, MaxBitrate);
    }

    public boolean sampleRateSatisfies(com.slskbatchdl.Soulseek.File file) {
        return sampleRateSatisfies(file.SampleRate);
    }

    public boolean sampleRateSatisfies(TagLib.File file) {
        return sampleRateSatisfies(file.getProperties().getAudioSampleRate());
    }

    public boolean sampleRateSatisfies(SimpleFile file) {
        return sampleRateSatisfies(file.Samplerate);
    }

    public boolean sampleRateSatisfies(Integer sampleRate) {
        return boundCheck(sampleRate, MinSampleRate, MaxSampleRate);
    }

    public boolean bitDepthSatisfies(com.slskbatchdl.Soulseek.File file) {
        return bitDepthSatisfies(file.BitDepth);
    }

    public boolean bitDepthSatisfies(TagLib.File file) {
        return bitDepthSatisfies(file.getProperties().getBitsPerSample());
    }

    public boolean bitDepthSatisfies(SimpleFile file) {
        return bitDepthSatisfies(file.Bitdepth);
    }

    public boolean bitDepthSatisfies(Integer bitdepth) {
        return boundCheck(bitdepth, MinBitDepth, MaxBitDepth);
    }

    public boolean boundCheck(Integer num, Integer min, Integer max) {
        if (max == null && min == null) {
            return true;
        }
        if (num == null) {
            return AcceptMissingProps == null || AcceptMissingProps;
        }
        if ((min != null && num < min) || (max != null && num > max)) {
            return false;
        }
        return true;
    }

    public boolean bannedUsersSatisfies(SearchResponse response) {
        return response == null || BannedUsers == null || Arrays.stream(BannedUsers).noneMatch(x -> x.equals(response.Username));
    }

    public String getNotSatisfiedName(com.slskbatchdl.Soulseek.File file, Track track, SearchResponse response) {
        if (!bannedUsersSatisfies(response)) {
            return "BannedUsers fails";
        }
        if (!strictTitleSatisfies(file.Filename, track.Title)) {
            return "StrictTitle fails";
        }
        if (track.Type == TrackType.Album && !strictAlbumSatisfies(file.Filename, track.Artist)) {
            return "StrictAlbum fails";
        }
        if (!strictArtistSatisfies(file.Filename, track.Artist)) {
            return "StrictArtist fails";
        }
        if (!lengthToleranceSatisfies(file, track.Length)) {
            return "LengthTolerance fails";
        }
        if (!formatSatisfies(file.Filename)) {
            return "Format fails";
        }
        if (track.Type != TrackType.Album && !strictAlbumSatisfies(file.Filename, track.Artist)) {
            return "StrictAlbum fails";
        }
        if (!bitrateSatisfies(file)) {
            return "Bitrate fails";
        }
        if (!sampleRateSatisfies(file)) {
            return "SampleRate fails";
        }
        if (!bitDepthSatisfies(file)) {
            return "BitDepth fails";
        }
        return "Satisfied";
    }
}
