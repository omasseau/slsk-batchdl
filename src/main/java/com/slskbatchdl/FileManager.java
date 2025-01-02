package com.slskbatchdl;

import com.slskbatchdl.models.Track;
import com.slskbatchdl.models.TrackListEntry;
import com.slskbatchdl.utils.Printing;
import com.slskbatchdl.utils.Utils;
import com.slskbatchdl.enums.TrackType;
import com.slskbatchdl.enums.TrackState;
import com.slskbatchdl.enums.AlbumArtOption;
import com.slskbatchdl.enums.InputType;
import com.slskbatchdl.enums.SkipMode;
import com.slskbatchdl.enums.PrintOption;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class FileManager {
    private final TrackListEntry tle;
    private final Set<Track> organized = new HashSet<>();
    private String remoteCommonDir;
    private String defaultFolderName;
    private final Config config;

    public FileManager(TrackListEntry tle, Config config) {
        this.tle = tle;
        this.config = config;
    }

    public String getSavePath(String sourceFname) {
        return getSavePathNoExt(sourceFname) + Utils.getFileExtension(sourceFname);
    }

    public String getSavePathNoExt(String sourceFname) {
        String parent = config.parentDir;
        String name = Utils.getFileNameWithoutExtSlsk(sourceFname);

        if (tle.defaultFolderName != null) {
            parent = Paths.get(parent, tle.defaultFolderName).toString();
        }

        if (tle.source.Type == TrackType.Album && remoteCommonDir != null && !remoteCommonDir.isEmpty()) {
            String dirname = defaultFolderName != null ? defaultFolderName : Paths.get(remoteCommonDir).getFileName().toString();
            String normFname = Utils.normalizedPath(sourceFname);
            String relpath = normFname.startsWith(remoteCommonDir) ? Paths.get(remoteCommonDir).relativize(Paths.get(normFname)).toString() : "";
            parent = Paths.get(parent, dirname, Paths.get(relpath).getParent().toString()).toString();
        }

        return Paths.get(parent, name).toString().replace(config.invalidReplaceStr, "");
    }

    public void setRemoteCommonDir(String remoteCommonDir) {
        this.remoteCommonDir = remoteCommonDir != null ? Utils.normalizedPath(remoteCommonDir) : null;
    }

    public void setDefaultFolderName(String defaultFolderName) {
        this.defaultFolderName = defaultFolderName != null ? Utils.normalizedPath(defaultFolderName) : null;
    }

    public void organizeAlbum(List<Track> tracks, List<Track> additionalImages, boolean remainingOnly) {
        for (Track track : tracks.stream().filter(t -> !t.isNotAudio()).collect(Collectors.toList())) {
            if (remainingOnly && organized.contains(track)) {
                continue;
            }

            organizeAudio(track, track.getFirstDownload());
        }

        boolean onlyAdditionalImages = config.nameFormat.isEmpty();

        List<Track> nonAudioToOrganize = onlyAdditionalImages ? additionalImages : tracks.stream().filter(Track::isNotAudio).collect(Collectors.toList());

        if (nonAudioToOrganize == null || nonAudioToOrganize.isEmpty()) {
            return;
        }

        String parent = Utils.greatestCommonDirectory(
            tracks.stream().filter(t -> !t.isNotAudio() && t.getState() == TrackState.Downloaded && !t.getDownloadPath().isEmpty()).map(Track::getDownloadPath).collect(Collectors.toList())
        );

        for (Track track : nonAudioToOrganize) {
            if (remainingOnly && organized.contains(track)) {
                continue;
            }

            organizeNonAudio(track, parent);
        }
    }

    public void organizeAudio(Track track, com.slskbatchdl.utils.File file) {
        if (track.getDownloadPath().isEmpty() || !Utils.isMusicFile(track.getDownloadPath())) {
            return;
        }

        if (config.nameFormat.isEmpty()) {
            organized.add(track);
            return;
        }

        String pathPart = substituteValues(config.nameFormat, track, file);
        String newFilePath = Paths.get(config.parentDir, pathPart + Utils.getFileExtension(track.getDownloadPath())).toString();

        try {
            moveAndDeleteParent(track.getDownloadPath(), newFilePath);
        } catch (Exception ex) {
            Printing.writeLine("\nFailed to move: " + ex.getMessage() + "\n", ConsoleColor.DarkYellow, true);
            return;
        }

        track.setDownloadPath(newFilePath);

        organized.add(track);
    }

    public void organizeNonAudio(Track track, String parent) {
        if (track.getDownloadPath().isEmpty()) {
            return;
        }

        String part = null;

        if (remoteCommonDir != null && Utils.isInDirectory(Utils.getDirectoryNameSlsk(track.getFirstDownload().getFilename()), remoteCommonDir, true)) {
            part = Utils.getFileNameSlsk(Utils.getDirectoryNameSlsk(track.getFirstDownload().getFilename()));
        }

        String newFilePath = Paths.get(parent, part, Paths.get(track.getDownloadPath()).getFileName().toString()).toString();

        try {
            moveAndDeleteParent(track.getDownloadPath(), newFilePath);
        } catch (Exception ex) {
            Printing.writeLine("\nFailed to move: " + ex.getMessage() + "\n", ConsoleColor.DarkYellow, true);
            return;
        }

        track.setDownloadPath(newFilePath);

        organized.add(track);
    }

    private void moveAndDeleteParent(String oldPath, String newPath) throws IOException {
        if (!Utils.normalizedPath(oldPath).equals(Utils.normalizedPath(newPath))) {
            Files.createDirectories(Paths.get(newPath).getParent());
            Utils.move(oldPath, newPath);
            Utils.deleteAncestorsIfEmpty(Paths.get(oldPath).getParent().toString(), config.parentDir);
        }
    }

    private String substituteValues(String format, Track track, com.slskbatchdl.utils.File slfile) {
        String newName = format;
        TagLib.File file = null;

        try {
            file = TagLib.File.create(track.getDownloadPath());
        } catch (Exception ignored) {
        }

        Pattern regex = Pattern.compile("(\\{(?:\\{??[^\\{]*?\\}))");
        Matcher matches = regex.matcher(newName);

        while (matches.find()) {
            for (int i = 0; i < matches.groupCount(); i++) {
                String inner = matches.group(i);
                inner = inner.substring(1, inner.length() - 1);

                String[] options = inner.split("\\|");
                String chosenOpt = null;

                for (String opt : options) {
                    String[] parts = opt.split("\\([^\\)]*\\)");
                    String[] result = Arrays.stream(parts).filter(part -> !part.isEmpty()).toArray(String[]::new);
                    if (Arrays.stream(result).allMatch(x -> tryGetVarValue(x, file, slfile, track, new StringBuilder()))) {
                        chosenOpt = opt;
                        break;
                    }
                }

                if (chosenOpt == null) {
                    chosenOpt = options[options.length - 1];
                }

                chosenOpt = chosenOpt.replaceAll("\\([^()]*\\)|[^()]+", match -> {
                    if (match.group().startsWith("(") && match.group().endsWith(")")) {
                        return match.group().substring(1, match.group().length() - 1).replaceAll("[^a-zA-Z0-9]", config.invalidReplaceStr);
                    } else {
                        StringBuilder res = new StringBuilder();
                        tryGetVarValue(match.group(), file, slfile, track, res);
                        return res.toString();
                    }
                });

                String old = matches.group(i);
                old = old.startsWith("{{") ? old.substring(1) : old;
                newName = newName.replace(old, chosenOpt);
            }

            matches = regex.matcher(newName);
        }

        if (!newName.equals(format)) {
            char dirsep = File.separatorChar;
            newName = newName.replace('/', dirsep).replace('\\', dirsep);
            String[] x = newName.split(Pattern.quote(String.valueOf(dirsep)), -1);
            newName = Arrays.stream(x).map(s -> s.replaceAll("[^a-zA-Z0-9]", config.invalidReplaceStr).trim()).collect(Collectors.joining(String.valueOf(dirsep)));
            return newName;
        }

        return format;
    }

    private boolean tryGetVarValue(String x, TagLib.File file, com.slskbatchdl.utils.File slfile, Track track, StringBuilder res) {
        switch (x) {
            case "artist":
                res.append(file != null ? file.getTag().getFirstPerformer() : "");
                break;
            case "artists":
                res.append(file != null ? String.join(" & ", file.getTag().getPerformers()) : "");
                break;
            case "albumartist":
                res.append(file != null ? file.getTag().getFirstAlbumArtist() : "");
                break;
            case "albumartists":
                res.append(file != null ? String.join(" & ", file.getTag().getAlbumArtists()) : "");
                break;
            case "title":
                res.append(file != null ? file.getTag().getTitle() : "");
                break;
            case "album":
                res.append(file != null ? file.getTag().getAlbum() : "");
                break;
            case "sartist":
            case "sartists":
                res.append(track.getArtist());
                break;
            case "stitle":
                res.append(track.getTitle());
                break;
            case "salbum":
                res.append(track.getAlbum());
                break;
            case "year":
                res.append(file != null ? String.valueOf(file.getTag().getYear()) : "");
                break;
            case "track":
                res.append(file != null ? String.format("%02d", file.getTag().getTrack()) : "");
                break;
            case "disc":
                res.append(file != null ? String.valueOf(file.getTag().getDisc()) : "");
                break;
            case "filename":
                res.append(Utils.getFileNameWithoutExtSlsk(slfile != null ? slfile.getFilename() : ""));
                break;
            case "foldername":
                if (remoteCommonDir == null || slfile == null) {
                    if (remoteCommonDir != null) {
                        res.append(Paths.get(remoteCommonDir).getFileName().toString());
                    } else {
                        res.append(Paths.get(slfile.getFilename()).getParent().toString());
                    }
                } else {
                    String d = Paths.get(slfile.getFilename()).getParent().toString();
                    String r = Paths.get(remoteCommonDir).getFileName().toString();
                    res.append(Paths.get(r, Paths.get(remoteCommonDir).relativize(Paths.get(d)).toString()).toString());
                }
                return true;
            case "extractor":
                res.append(config.inputType.toString());
                break;
            case "default-folder":
                res.append(tle.defaultFolderName != null ? tle.defaultFolderName : tle.source.toString(false));
                break;
            default:
                res.append(x);
                return false;
        }

        res.replace(0, res.length(), res.toString().replaceAll("[^a-zA-Z0-9]", config.invalidReplaceStr));
        return true;
    }
}
