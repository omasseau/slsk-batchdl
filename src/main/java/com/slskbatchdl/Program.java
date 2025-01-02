package com.slskbatchdl;

import com.slskbatchdl.enums.*;
import com.slskbatchdl.models.*;
import com.slskbatchdl.utils.*;
import com.slskbatchdl.extractors.*;
import com.slskbatchdl.fileskippers.*;

import java.io.*;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;

import static com.slskbatchdl.utils.Printing.*;

public class Program {
    private static final int updateInterval = 100;
    private static boolean initialized = false;
    public static boolean skipUpdate = false;

    public static IExtractor extractor;
    public static TrackLists trackLists;
    public static SoulseekClient client;

    public static final ConcurrentMap<Track, SearchInfo> searches = new ConcurrentHashMap<>();
    public static final ConcurrentMap<String, DownloadWrapper> downloads = new ConcurrentHashMap<>();
    public static final ConcurrentMap<String, Integer> userSuccessCounts = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");
        System.out.flush();
        Console console = System.console();
        if (console != null) {
            console.writer().println("Hello, World!");
            console.flush();
        }

        ConsoleColor.resetColor();
        System.setOut(new PrintStream(System.out, true, "UTF-8"));
        Help.printHelpAndExitIfNeeded(args);

        Config config = new Config(args);

        if (config.input.isEmpty()) {
            throw new IllegalArgumentException("No input provided");
        }

        ExtractorRegistry.ExtractorEntry extractorEntry = ExtractorRegistry.getMatchingExtractor(config.input, config.inputType);
        config.inputType = extractorEntry.getType();
        extractor = extractorEntry.createExtractor();

        writeLineIf("Using extractor: " + config.inputType, config.debugInfo);

        trackLists = extractor.getTracks(config.input, config.maxTracks, config.offset, config.reverse, config).get();

        writeLineIf("Got tracks", config.debugInfo);

        config.postProcessArgs();

        trackLists.upgradeListTypes(config.aggregate, config.album);
        trackLists.setListEntryOptions();

        mainLoop(config);

        writeLineIf("Mainloop done", config.debugInfo);
    }

    public static void initClientAndUpdateIfNeeded(Config config) throws Exception {
        if (initialized) {
            return;
        }

        boolean needLogin = !config.printTracks();
        if (needLogin) {
            ConnectionOptions connectionOptions = new ConnectionOptions(socket -> {
                socket.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                socket.setOption(StandardSocketOptions.TCP_KEEPCOUNT, 3);
                socket.setOption(StandardSocketOptions.TCP_KEEPIDLE, 15);
                socket.setOption(StandardSocketOptions.TCP_KEEPINTERVAL, 15);
            });

            SoulseekClientOptions clientOptions = new SoulseekClientOptions(
                connectionOptions,
                connectionOptions,
                config.listenPort
            );

            client = new SoulseekClient(clientOptions);

            if (!config.useRandomLogin && (config.username.isEmpty() || config.password.isEmpty())) {
                throw new IllegalArgumentException("No soulseek username or password");
            }

            login(config, config.useRandomLogin);

            Search.searchSemaphore = new RateLimitedSemaphore(config.searchesPerTime, Duration.ofSeconds(config.searchRenewTime));
        }

        boolean needUpdate = needLogin;
        if (needUpdate) {
            CompletableFuture.runAsync(() -> update(config));
            writeLineIf("Update started", config.debugInfo);
        }

        initialized = true;
    }

    static void initEditors(TrackListEntry tle, Config config) {
        tle.playlistEditor = new M3uEditor(trackLists, config.writePlaylist ? M3uOption.Playlist : M3uOption.None, config.offset);
        tle.indexEditor = new M3uEditor(trackLists, config.writeIndex ? M3uOption.Index : M3uOption.None);
    }

    static void initFileSkippers(TrackListEntry tle, Config config) {
        if (config.skipExisting) {
            FileConditions cond = null;

            if (config.skipCheckPrefCond) {
                cond = config.necessaryCond.addConditions(config.preferredCond);
            } else if (config.skipCheckCond) {
                cond = config.necessaryCond;
            }

            tle.outputDirSkipper = FileSkipperRegistry.getSkipper(config.skipMode, config.parentDir, cond, tle.indexEditor);

            if (!config.skipMusicDir.isEmpty()) {
                if (!Files.exists(Paths.get(config.skipMusicDir))) {
                    System.out.println("Error: Music directory does not exist");
                } else {
                    tle.musicDirSkipper = FileSkipperRegistry.getSkipper(config.skipModeMusicDir, config.skipMusicDir, cond, tle.indexEditor);
                }
            }
        }
    }

    static void initConfigs(Config defaultConfig) {
        if (trackLists.isEmpty()) {
            return;
        }

        for (TrackListEntry tle : trackLists.lists) {
            tle.config = defaultConfig.copy();
            tle.config.updateProfiles(tle);

            if (tle.extractorCond != null) {
                tle.config.necessaryCond = tle.config.necessaryCond.addConditions(tle.extractorCond);
                tle.extractorCond = null;
            }
            if (tle.extractorPrefCond != null) {
                tle.config.preferredCond = tle.config.preferredCond.addConditions(tle.extractorPrefCond);
                tle.extractorPrefCond = null;
            }

            initEditors(tle, tle.config);
            initFileSkippers(tle, tle.config);
        }

        defaultConfig.updateProfiles(trackLists.lists.get(0));
        trackLists.lists.get(0).config = defaultConfig;
        initEditors(trackLists.lists.get(0), defaultConfig);
        initFileSkippers(trackLists.lists.get(0), defaultConfig);

        Map<Config, TrackListEntry> configs = new HashMap<>();
        configs.put(defaultConfig, trackLists.lists.get(0));

        for (TrackListEntry tle : trackLists.lists.subList(1, trackLists.lists.size())) {
            boolean needUpdate = true;

            for (Map.Entry<Config, TrackListEntry> entry : configs.entrySet()) {
                Config config = entry.getKey();
                TrackListEntry exampleTle = entry.getValue();

                if (!config.needUpdateProfiles(tle)) {
                    tle.config = config;

                    if (exampleTle == null) {
                        initEditors(tle, config);
                        initFileSkippers(tle, config);
                        configs.put(config, tle);
                    } else {
                        tle.playlistEditor = exampleTle.playlistEditor;
                        tle.indexEditor = exampleTle.indexEditor;
                        tle.outputDirSkipper = exampleTle.outputDirSkipper;
                        tle.musicDirSkipper = exampleTle.musicDirSkipper;
                    }

                    needUpdate = false;
                    break;
                }
            }

            if (!needUpdate) {
                continue;
            }

            Config newConfig = defaultConfig.copy();
            newConfig.updateProfiles(tle);
            configs.put(newConfig, tle);

            tle.config = newConfig;

            initEditors(tle, newConfig);
            initFileSkippers(tle, newConfig);
        }
    }

    static void preprocessTracks(Config config, TrackListEntry tle) {
        preprocessTrack(config, tle.source);

        for (List<Track> ls : tle.list) {
            for (Track track : ls) {
                preprocessTrack(config, track);
            }
        }
    }

    static void preprocessTrack(Config config, Track track) {
        if (config.removeFt) {
            track.setTitle(track.getTitle().replaceAll("(?i)\\s*\\(feat\\..*?\\)", ""));
            track.setArtist(track.getArtist().replaceAll("(?i)\\s*\\(feat\\..*?\\)", ""));
        }
        if (config.removeBrackets) {
            track.setTitle(track.getTitle().replaceAll("\\[.*?\\]", ""));
        }
        if (!config.regexToReplace.getTitle().isEmpty() || !config.regexToReplace.getArtist().isEmpty() || !config.regexToReplace.getAlbum().isEmpty()) {
            track.setTitle(track.getTitle().replaceAll(config.regexToReplace.getTitle(), config.regexReplaceBy.getTitle()));
            track.setArtist(track.getArtist().replaceAll(config.regexToReplace.getArtist(), config.regexReplaceBy.getArtist()));
            track.setAlbum(track.getAlbum().replaceAll(config.regexToReplace.getAlbum(), config.regexReplaceBy.getAlbum()));
        }
        if (config.artistMaybeWrong) {
            track.setArtistMaybeWrong(true);
        }

        track.setArtist(track.getArtist().trim());
        track.setAlbum(track.getAlbum().trim());
        track.setTitle(track.getTitle().trim());
    }

    static void prepareListEntry(Config prevConfig, TrackListEntry tle) {
        tle.config = prevConfig.copy();
        tle.config.updateProfiles(tle);

        if (tle.extractorCond != null) {
            tle.config.necessaryCond = tle.config.necessaryCond.addConditions(tle.extractorCond);
            tle.extractorCond = null;
        }
        if (tle.extractorPrefCond != null) {
            tle.config.preferredCond = tle.config.preferredCond.addConditions(tle.extractorPrefCond);
            tle.extractorPrefCond = null;
        }

        initEditors(tle, tle.config);
        initFileSkippers(tle, tle.config);

        String m3uPath, indexPath;

        if (!tle.config.m3uFilePath.isEmpty()) {
            m3uPath = tle.config.m3uFilePath;
        } else {
            m3uPath = Paths.get(tle.config.parentDir, tle.defaultFolderName, "_playlist.m3u8").toString();
        }

        if (!tle.config.indexFilePath.isEmpty()) {
            indexPath = tle.config.indexFilePath;
        } else {
            indexPath = Paths.get(tle.config.parentDir, tle.defaultFolderName, "_index.sldl").toString();
        }

        if (tle.config.writePlaylist) {
            tle.playlistEditor.setPathAndLoad(m3uPath);
        }
        if (tle.config.writeIndex) {
            tle.indexEditor.setPathAndLoad(indexPath);
        }

        preprocessTracks(tle.config, tle);
    }

    static void mainLoop(Config defaultConfig) throws Exception {
        if (trackLists.isEmpty()) {
            return;
        }

        prepareListEntry(defaultConfig, trackLists.lists.get(0));
        Config firstConfig = trackLists.lists.get(0).config;

        boolean enableParallelSearch = firstConfig.parallelAlbumSearch && !firstConfig.printResults() && !firstConfig.printTracks() && trackLists.lists.stream().anyMatch(x -> x.canParallelSearch);
        List<ParallelSearch> parallelSearches = new ArrayList<>();
        Semaphore parallelSearchSemaphore = new Semaphore(firstConfig.parallelAlbumSearchProcesses);

        for (int i = 0; i < trackLists.lists.size(); i++) {
            if (!enableParallelSearch) {
                System.out.println();
            }

            if (i > 0) {
                prepareListEntry(trackLists.lists.get(i - 1).config, trackLists.lists.get(i));
            }

            TrackListEntry tle = trackLists.lists.get(i);
            Config config = tle.config;

            List<Track> existing = new ArrayList<>();
            List<Track> notFound = new ArrayList<>();

            if (config.skipNotFound && !config.printResults()) {
                if (tle.sourceCanBeSkipped && setNotFoundLastTime(config, tle.source, tle.indexEditor)) {
                    notFound.add(tle.source);
                }

                if (tle.source.getState() != TrackState.NotFoundLastTime && !tle.needSourceSearch) {
                    for (List<Track> tracks : tle.list) {
                        notFound.addAll(doSkipNotFound(config, tracks, tle.indexEditor));
                    }
                }
            }

            if (config.skipExisting && !config.printResults() && tle.source.getState() != TrackState.NotFoundLastTime) {
                if (tle.sourceCanBeSkipped && setExisting(tle, config, tle.source)) {
                    existing.add(tle.source);
                }

                if (tle.source.getState() != TrackState.AlreadyExists && !tle.needSourceSearch) {
                    for (List<Track> tracks : tle.list) {
                        existing.addAll(doSkipExisting(tle, config, tracks));
                    }
                }
            }

            if (config.printTracks()) {
                if (tle.source.getType() == TrackType.Normal) {
                    printTracksTbd(tle.list.get(0).stream().filter(t -> t.getState() == TrackState.Initial).collect(Collectors.toList()), existing, notFound, tle.source.getType(), config);
                } else {
                    List<Track> tl = new ArrayList<>();
                    if (tle.source.getState() == TrackState.Initial) {
                        tl.add(tle.source);
                    }
                    printTracksTbd(tl, existing, notFound, tle.source.getType(), config, false);
                }
                continue;
            }

            if (tle.sourceCanBeSkipped) {
                if (tle.source.getState() == TrackState.AlreadyExists) {
                    System.out.println(tle.source.getType() + " download '" + tle.source.toString(true) + "' already exists at " + tle.source.getDownloadPath() + ", skipping");
                    continue;
                }

                if (tle.source.getState() == TrackState.NotFoundLastTime) {
                    System.out.println(tle.source.getType() + " download '" + tle.source.toString(true) + "' was not found during a prior run, skipping");
                    continue;
                }
            }

            if (tle.needSourceSearch) {
                initClientAndUpdateIfNeeded(config);

                ProgressBar progress = null;

                CompletableFuture<SourceSearchResult> sourceSearch = CompletableFuture.supplyAsync(() -> {
                    try {
                        parallelSearchSemaphore.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    progress = enableParallelSearch ? Printing.getProgressBar(config) : null;
                    Printing.refreshOrPrint(progress, 0, "  " + tle.source.getType() + " download: " + tle.source.toString(true) + ", searching..", true);

                    boolean foundSomething = false;
                    ResponseData responseData = new ResponseData();

                    try {
                        if (tle.source.getType() == TrackType.Album) {
                            tle.list = Search.getAlbumDownloads(tle.source, responseData, config).get();
                            foundSomething = !tle.list.isEmpty() && !tle.list.get(0).isEmpty();
                        } else if (tle.source.getType() == TrackType.Aggregate) {
                            tle.list.add(0, Search.getAggregateTracks(tle.source, responseData, config).get());
                            foundSomething = !tle.list.isEmpty() && !tle.list.get(0).isEmpty();
                        } else if (tle.source.getType() == TrackType.AlbumAggregate) {
                            List<List<Track>> res = Search.getAggregateAlbums(tle.source, responseData, config).get();

                            for (List<Track> item : res) {
                                Track newSource = new Track(tle.source);
                                newSource.setType(TrackType.Album);
                                TrackListEntry albumTle = new TrackListEntry(item, newSource, false, true);
                                albumTle.defaultFolderName = tle.defaultFolderName;
                                trackLists.addEntry(albumTle);
                            }

                            foundSomething = !res.isEmpty();
                        }

                        tle.needSourceSearch = false;

                        if (!foundSomething) {
                            String lockedFiles = responseData.getLockedFilesCount() > 0 ? " (Found " + responseData.getLockedFilesCount() + " locked files)" : "";
                            String str = progress != null ? ": " + tle.source : "";
                            Printing.refreshOrPrint(progress, 0, "No results" + str + lockedFiles, true);
                        } else if (progress != null) {
                            Printing.refreshOrPrint(progress, 0, "Found results: " + tle.source, true);
                        }

                        parallelSearchSemaphore.release();

                        return new SourceSearchResult(foundSomething, responseData);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                if (!enableParallelSearch || !tle.canParallelSearch) {
                    SourceSearchResult result = sourceSearch.get();

                    if (!result.foundSomething) {
                        if (!config.printResults()) {
                            tle.source.setState(TrackState.Failed);
                            tle.source.setFailureReason(FailureReason.NoSuitableFileFound);
                            tle.indexEditor.update();
                        }

                        continue;
                    }

                    if (config.skipExisting && tle.needSkipExistingAfterSearch) {
                        for (List<Track> tracks : tle.list) {
                            existing.addAll(doSkipExisting(tle, config, tracks));
                        }
                    }

                    if (tle.gotoNextAfterSearch) {
                        continue;
                    }
                } else {
                    parallelSearches.add(new ParallelSearch(tle, sourceSearch));
                    continue;
                }
            }

            if (config.printResults()) {
                printResults(tle, existing, notFound, config);
                continue;
            }

            if (!enableParallelSearch || !tle.canParallelSearch) {
                download(tle, config, notFound, existing);
            }
        }

        if (!trackLists.lists.get(trackLists.lists.size() - 1).config.doNotDownload() && (!trackLists.lists.isEmpty() || trackLists.flattened(false, false).skip(1).findAny().isPresent())) {
            printComplete(trackLists);
        }

        if (!parallelSearches.isEmpty()) {
            parallelDownloads(parallelSearches);
        }
    }

    static List<Track> doSkipExisting(TrackListEntry tle, Config config, List<Track> tracks) {
        List<Track> existing = new ArrayList<>();
        for (Track track : tracks) {
            if (setExisting(tle, config, track)) {
                existing.add(track);
            }
        }
        return existing;
    }

    static boolean setExisting(TrackListEntry tle, Config config, Track track) {
        String path = null;

        if (tle.outputDirSkipper != null) {
            if (!tle.outputDirSkipper.indexIsBuilt()) {
                tle.outputDirSkipper.buildIndex();
            }

            tle.outputDirSkipper.trackExists(track, path);
        }

        if (path == null && tle.musicDirSkipper != null) {
            if (!tle.musicDirSkipper.indexIsBuilt()) {
                System.out.println("Building music directory index..");
                tle.musicDirSkipper.buildIndex();
            }

            tle.musicDirSkipper.trackExists(track, path);
        }

        if (path != null) {
            track.setState(TrackState.AlreadyExists);
            track.setDownloadPath(path);
        }

        return path != null;
    }

    static List<Track> doSkipNotFound(Config config, List<Track> tracks, M3uEditor indexEditor) {
        List<Track> notFound = new ArrayList<>();
        for (Track track : tracks) {
            if (setNotFoundLastTime(config, track, indexEditor)) {
                notFound.add(track);
            }
        }
        return notFound;
    }

    static boolean setNotFoundLastTime(Config config, Track track, M3uEditor indexEditor) {
        Track prevTrack = indexEditor.getPreviousRunResult(track);
        if (prevTrack != null && (prevTrack.getFailureReason() == FailureReason.NoSuitableFileFound || prevTrack.getState() == TrackState.NotFoundLastTime)) {
            track.setState(TrackState.NotFoundLastTime);
            return true;
        }
        return false;
    }

    static void downloadNormal(Config config, TrackListEntry tle) throws Exception {
        List<Track> tracks = tle.list.get(0);

        Semaphore semaphore = new Semaphore(config.concurrentProcesses);

        FileManager organizer = new FileManager(tle, config);

        List<CompletableFuture<Void>> downloadTasks = tracks.stream()
            .map(track -> CompletableFuture.runAsync(() -> {
                try (CancellationTokenSource cts = new CancellationTokenSource()) {
                    downloadTask(config, tle, track, semaphore, organizer, cts, false, true, true);
                    tle.indexEditor.update();
                    tle.playlistEditor.update();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }))
            .collect(Collectors.toList());

        CompletableFuture.allOf(downloadTasks.toArray(new CompletableFuture[0])).join();

        if (config.removeTracksFromSource && tracks.stream().allMatch(t -> t.getState() == TrackState.Downloaded || t.getState() == TrackState.AlreadyExists)) {
            extractor.removeTrackFromSource(tle.source);
        }
    }

    static void downloadAlbum(Config config, TrackListEntry tle) throws Exception {
        FileManager organizer = new FileManager(tle, config);
        List<Track> tracks = null;
        Set<String> retrievedFolders = new HashSet<>();
        AtomicBoolean succeeded = new AtomicBoolean(false);
        String soulseekDir = null;
        int index = 0;

        while (!tle.list.isEmpty() && !config.albumArtOnly) {
            boolean wasInteractive = config.interactiveMode;
            boolean retrieveCurrent = true;
            index = 0;

            if (config.interactiveMode) {
                InteractiveModeAlbumResult result = interactiveModeAlbum(config, tle.list, !config.noBrowseFolder, retrievedFolders);
                index = result.index;
                tracks = result.tracks;
                retrieveCurrent = result.retrieveFolder;
                if (index == -1) {
                    break;
                }
            } else {
                tracks = tle.list.get(index);
            }

            soulseekDir = Utils.greatestCommonDirectorySlsk(tracks.stream().map(Track::getFirstDownload).map(SlFile::getFilename).collect(Collectors.toList()));

            organizer.setRemoteCommonDir(soulseekDir);

            if (!config.interactiveMode && !wasInteractive) {
                System.out.println();
                printAlbum(tracks);
            }

            Semaphore semaphore = new Semaphore(999);
            try (CancellationTokenSource cts = new CancellationTokenSource()) {
                runAlbumDownloads(config, tle, organizer, tracks, semaphore, cts);

                if (!config.noBrowseFolder && retrieveCurrent && !retrievedFolders.contains(soulseekDir)) {
                    System.out.println("Getting all files in folder...");

                    int newFilesFound = Search.completeFolder(tracks, tracks.get(0).getFirstResponse(), soulseekDir).get();
                    retrievedFolders.add(tracks.get(0).getFirstUsername() + '\\' + soulseekDir);

                    if (newFilesFound > 0) {
                        System.out.println("Found " + newFilesFound + " more files in the directory, downloading:");
                        runAlbumDownloads(config, tle, organizer, tracks, semaphore, cts);
                    } else {
                        System.out.println("No more files found.");
                    }
                }

                succeeded.set(true);
                break;
            } catch (OperationCanceledException e) {
                onAlbumFail(config, tracks);
            }

            organizer.setRemoteCommonDir(null);
            tle.list.remove(index);
        }

        if (succeeded.get()) {
            onAlbumSuccess(config, tle, tracks);
        }

        List<Track> additionalImages = null;

        if (config.albumArtOnly || (succeeded.get() && config.albumArtOption != AlbumArtOption.Default)) {
            System.out.println("\nDownloading additional images:");
            additionalImages = downloadImages(config, tle, tle.list, config.albumArtOption, tle.list.get(index));
            if (tracks != null) {
                tracks.addAll(additionalImages);
            }
        }

        if (tracks != null && !tle.source.getDownloadPath().isEmpty()) {
            organizer.organizeAlbum(tracks, additionalImages);
        }

        tle.indexEditor.update();
        tle.playlistEditor.update();
    }

    static void runAlbumDownloads(Config config, TrackListEntry tle, FileManager organizer, List<Track> tracks, Semaphore semaphore, CancellationTokenSource cts) throws Exception {
        List<CompletableFuture<Void>> downloadTasks = tracks.stream()
            .map(track -> CompletableFuture.runAsync(() -> {
                try {
                    downloadTask(config, tle, track, semaphore, organizer, cts, true, true, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }))
            .collect(Collectors.toList());

        CompletableFuture.allOf(downloadTasks.toArray(new CompletableFuture[0])).join();
    }

    static void onAlbumSuccess(Config config, TrackListEntry tle, List<Track> tracks) throws Exception {
        if (tracks == null) {
            return;
        }

        List<Track> downloadedAudio = tracks.stream()
            .filter(t -> !t.isNotAudio() && t.getState() == TrackState.Downloaded && !t.getDownloadPath().isEmpty())
            .collect(Collectors.toList());

        if (!downloadedAudio.isEmpty()) {
            tle.source.setState(TrackState.Downloaded);
            tle.source.setDownloadPath(Utils.greatestCommonDirectory(downloadedAudio.stream().map(Track::getDownloadPath).collect(Collectors.toList())));

            if (config.removeTracksFromSource) {
                extractor.removeTrackFromSource(tle.source);
            }
        }
    }

    static void onAlbumFail(Config config, List<Track> tracks) {
        if (tracks == null || config.ignoreAlbumFail()) {
            return;
        }

        for (Track track : tracks) {
            if (!track.getDownloadPath().isEmpty() && Files.exists(Paths.get(track.getDownloadPath()))) {
                try {
                    if (config.deleteAlbumOnFail()) {
                        Files.delete(Paths.get(track.getDownloadPath()));
                    } else if (!config.failedAlbumPath.isEmpty()) {
                        String newPath = Paths.get(config.failedAlbumPath, Paths.get(config.parentDir).relativize(Paths.get(track.getDownloadPath())).toString()).toString();
                        Files.createDirectories(Paths.get(newPath).getParent());
                        Utils.move(track.getDownloadPath(), newPath);
                    }

                    Utils.deleteAncestorsIfEmpty(Paths.get(track.getDownloadPath()).getParent().toString(), config.parentDir);
                } catch (Exception e) {
                    Printing.writeLine("Error: Unable to move or delete file '" + track.getDownloadPath() + "' after album fail: " + e);
                }
            }
        }
    }

    static List<Track> downloadImages(Config config, TrackListEntry tle, List<List<Track>> downloads, AlbumArtOption option, List<Track> chosenAlbum) throws Exception {
        List<Track> downloadedImages = new ArrayList<>();
        long mSize = 0;
        int mCount = 0;

        FileManager fileManager = new FileManager(tle, config);

        if (chosenAlbum != null) {
            String dir = Utils.greatestCommonDirectorySlsk(chosenAlbum.stream().map(Track::getFirstDownload).map(SlFile::getFilename).collect(Collectors.toList()));
            fileManager.setDefaultFolderName(Paths.get(Utils.normalizedPath(dir)).getFileName().toString());
        }

        if (option == AlbumArtOption.Default) {
            return downloadedImages;
        }

        int[] sortedLengths = null;

        if (chosenAlbum != null && chosenAlbum.stream().anyMatch(t -> !t.isNotAudio())) {
            sortedLengths = chosenAlbum.stream().filter(t -> !t.isNotAudio()).mapToInt(Track::getLength).sorted().toArray();
        }

        List<List<Track>> albumArts = downloads.stream()
            .filter(ls -> chosenAlbum == null || Search.albumsAreSimilar(chosenAlbum, ls, sortedLengths))
            .map(ls -> ls.stream().filter(t -> Utils.isImageFile(t.getFirstDownload().getFilename())).collect(Collectors.toList()))
            .filter(ls -> !ls.isEmpty())
            .collect(Collectors.toList());

        if (albumArts.isEmpty()) {
            System.out.println("No images found");
            return downloadedImages;
        } else if (albumArts.size() == 1 && albumArts.get(0).stream().allMatch(y -> y.getState() != TrackState.Initial)) {
            System.out.println("No additional images found");
            return downloadedImages;
        }

        if (option == AlbumArtOption.Largest) {
            albumArts.sort(Comparator.comparingLong((List<Track> tracks) -> tracks.stream().mapToLong(t -> t.getFirstDownload().getSize()).max().orElse(0) / 1024 / 100)
                .thenComparingLong(tracks -> tracks.get(0).getFirstResponse().getUploadSpeed() / 1024 / 300)
                .thenComparingLong(tracks -> tracks.stream().mapToLong(t -> t.getFirstDownload().getSize()).sum() / 1024 / 100).reversed());

            if (chosenAlbum != null) {
                mSize = chosenAlbum.stream()
                    .filter(t -> t.getState() == TrackState.Downloaded && Utils.isImageFile(t.getDownloadPath()))
                    .mapToLong(t -> t.getFirstDownload().getSize())
                    .max()
                    .orElse(0);
            }
        } else if (option == AlbumArtOption.Most) {
            albumArts.sort(Comparator.comparingInt(List::size)
                .thenComparingLong(tracks -> tracks.get(0).getFirstResponse().getUploadSpeed() / 1024 / 300)
                .thenComparingLong(tracks -> tracks.stream().mapToLong(t -> t.getFirstDownload().getSize()).sum() / 1024 / 100).reversed());

            if (chosenAlbum != null) {
                mCount = (int) chosenAlbum.stream()
                    .filter(t -> t.getState() == TrackState.Downloaded && Utils.isImageFile(t.getDownloadPath()))
                    .count();
            }
        }

        List<List<Track>> albumArtLists = new ArrayList<>(albumArts);

        boolean needImageDownload(List<Track> list) {
            if (list.stream().allMatch(t -> t.getState() == TrackState.Downloaded || t.getState() == TrackState.AlreadyExists)) {
                return false;
            } else if (option == AlbumArtOption.Most) {
                return mCount < list.size();
            } else if (option == AlbumArtOption.Largest) {
                return mSize < list.stream().mapToLong(t -> t.getFirstDownload().getSize()).max().orElse(0) - 1024 * 50;
            }
            return true;
        }

        while (!albumArtLists.isEmpty()) {
            int index = 0;
            boolean wasInteractive = config.interactiveMode;
            List<Track> tracks;

            if (config.interactiveMode) {
                InteractiveModeAlbumResult result = interactiveModeAlbum(config, albumArtLists, false, null);
                index = result.index;
                tracks = result.tracks;
                if (index == -1) {
                    break;
                }
            } else {
                tracks = albumArtLists.get(index);
            }

            albumArtLists.remove(index);

            if (!needImageDownload(tracks)) {
                System.out.println("Image requirements already satisfied.");
                return downloadedImages;
            }

            if (!config.interactiveMode && !wasInteractive) {
                System.out.println();
                printAlbum(tracks);
            }

            fileManager.setRemoteCommonDir(Utils.greatestCommonDirectorySlsk(tracks.stream().map(Track::getFirstDownload).map(SlFile::getFilename).collect(Collectors.toList())));

            boolean allSucceeded = true;
            Semaphore semaphore = new Semaphore(1);

            for (Track track : tracks) {
                try (CancellationTokenSource cts = new CancellationTokenSource()) {
                    downloadTask(config, tle, track, semaphore, fileManager, cts, false, false, false);

                    if (track.getState() == TrackState.Downloaded) {
                        downloadedImages.add(track);
                    } else {
                        allSucceeded = false;
                    }
                }
            }

            if (allSucceeded) {
                break;
            }
        }

        return downloadedImages;
    }

    static void downloadTask(Config config, TrackListEntry tle, Track track, Semaphore semaphore, FileManager organizer, CancellationTokenSource cts, boolean cancelOnFail, boolean removeFromSource, boolean organize) throws Exception {
        if (track.getState() != TrackState.Initial) {
            return;
        }

        semaphore.acquire();

        int tries = config.unknownErrorRetries;
        String savedFilePath = "";
        SlFile chosenFile = null;

        while (tries > 0) {
            waitForLogin(config);

            cts.getToken().throwIfCancellationRequested();

            try {
                SearchAndDownloadResult result = Search.searchAndDownload(track, organizer, config, cts);
                savedFilePath = result.savedFilePath;
                chosenFile = result.chosenFile;
            } catch (Exception ex) {
                writeLineIf("Error: " + ex, config.debugInfo);
                if (!isConnectedAndLoggedIn()) {
                    continue;
                } else if (ex instanceof SearchAndDownloadException) {
                    synchronized (trackLists) {
                        track.setState(TrackState.Failed);
                        track.setFailureReason(((SearchAndDownloadException) ex).reason);
                    }

                    if (cancelOnFail) {
                        cts.cancel();
                        throw new OperationCanceledException();
                    }
                } else {
                    tries--;
                    continue;
                }
            }

            break;
        }

        if (tries == 0 && cancelOnFail) {
            cts.cancel();
            throw new OperationCanceledException();
        }

        if (!savedFilePath.isEmpty()) {
            synchronized (trackLists) {
                track.setState(TrackState.Downloaded);
                track.setDownloadPath(savedFilePath);
            }

            if (removeFromSource && config.removeTracksFromSource) {
                try {
                    extractor.removeTrackFromSource(track);
                } catch (Exception ex) {
                    writeLine("\n" + ex.getMessage() + "\n" + ex.getStackTrace() + "\n", ConsoleColor.DarkYellow, true);
                }
            }
        }

        if (track.getState() == TrackState.Downloaded && organize) {
            synchronized (trackLists) {
                organizer.organizeAudio(track, chosenFile);
            }
        }

        if (!config.onComplete.isEmpty()) {
            onComplete(config, config.onComplete, track);
        }

        semaphore.release();
    }

    static InteractiveModeAlbumResult interactiveModeAlbum(Config config, List<List<Track>> list, boolean retrieveFolder, Set<String> retrievedFolders) throws Exception {
        int aidx = 0;

        void writeHelp() {
            String retrieveAll1 = retrieveFolder ? "| [r]            " : "";
            String retrieveAll2 = retrieveFolder ? "| Load All Files " : "";
            System.out.println();
            writeLine(" [Up/p] | [Down/n] | [Enter] | [q]                       " + retrieveAll1 + "| [Esc/s]", ConsoleColor.Green);
            writeLine(" Prev   | Next     | Accept  | Accept & Quit Interactive " + retrieveAll2 + "| Skip", ConsoleColor.Green);
            System.out.println();
            writeLine(" d:1,2,3 or d:start:end to download individual files", ConsoleColor.Green);
            System.out.println();
        }

        writeHelp();

        while (true) {
            List<Track> tracks = list.get(aidx);
            SearchResponse response = tracks.get(0).getFirstResponse();
            String username = tracks.get(0).getFirstUsername();

            writeLine("[" + (aidx + 1) + " / " + list.size() + "]", ConsoleColor.DarkGray);

            printAlbum(tracks, true);
            System.out.println();

            String userInput = interactiveModeLoop().trim().toLowerCase();
            String options = "";

            if (userInput.startsWith("d:")) {
                options = userInput.substring(2).trim();
                userInput = "d";
            }

            switch (userInput) {
                case "p":
                    aidx = (aidx + list.size() - 1) % list.size();
                    break;
                case "n":
                    aidx = (aidx + 1) % list.size();
                    break;
                case "s":
                    return new InteractiveModeAlbumResult(-1, new ArrayList<>(), false);
                case "q":
                    config.interactiveMode = false;
                    return new InteractiveModeAlbumResult(aidx, tracks, true);
                case "r":
                    if (!retrieveFolder) {
                        break;
                    }
                    String folder = Utils.greatestCommonDirectorySlsk(tracks.stream().map(Track::getFirstDownload).map(SlFile::getFilename).collect(Collectors.toList()));
                    if (retrieveFolder && !retrievedFolders.contains(username + '\\' + folder)) {
                        System.out.println("Getting all files in folder...");
                        int newFiles = Search.completeFolder(tracks, response, folder).get();
                        retrievedFolders.add(username + '\\' + folder);
                        if (newFiles == 0) {
                            System.out.println("No more files found.");
                        } else {
                            System.out.println("Found " + newFiles + " more files in the folder:");
                        }
                    }
                    break;
                case "d":
                    if (options.isEmpty()) {
                        return new InteractiveModeAlbumResult(aidx, tracks, true);
                    }
                    try {
                        int[] indices = Arrays.stream(options.split(","))
                            .flatMap(option -> {
                                if (option.contains(":")) {
                                    String[] parts = option.split(":");
                                    int start = parts[0].isEmpty() ? 1 : Integer.parseInt(parts[0]);
                                    int end = parts[1].isEmpty() ? tracks.size() : Integer.parseInt(parts[1]);
                                    return IntStream.rangeClosed(start, end).boxed();
                                }
                                return Stream.of(Integer.parseInt(option));
                            })
                            .distinct()
                            .mapToInt(Integer::intValue)
                            .toArray();
                        return new InteractiveModeAlbumResult(aidx, Arrays.stream(indices).mapToObj(i -> tracks.get(i - 1)).collect(Collectors.toList()), false);
                    } catch (Exception e) {
                        writeHelp();
                        break;
                    }
                case "":
                    return new InteractiveModeAlbumResult(aidx, tracks, true);
                default:
                    writeHelp();
                    break;
            }
        }
    }

    static void update(Config config) {
        while (true) {
            if (!skipUpdate) {
                try {
                    if (isConnectedAndLoggedIn()) {
                        searches.entrySet().removeIf(entry -> entry.getValue() == null);

                        downloads.entrySet().removeIf(entry -> {
                            DownloadWrapper val = entry.getValue();
                            if (val != null) {
                                synchronized (val) {
                                    if (Duration.between(val.getUpdateLastChangeTime(), Instant.now()).toMillis() > config.maxStaleTime) {
                                        val.setStalled(true);
                                        val.updateText();

                                        try {
                                            val.getCts().cancel();
                                        } catch (Exception ignored) {
                                        }
                                        return true;
                                    } else {
                                        val.updateText();
                                    }
                                }
                            }
                            return val == null;
                        });
                    } else {
                        if (!client.getState().contains(SoulseekClientStates.LoggedIn)
                            && !client.getState().contains(SoulseekClientStates.LoggingIn)
                            && !client.getState().contains(SoulseekClientStates.Connecting)) {
                            writeLine("\nDisconnected, logging in\n", ConsoleColor.DarkYellow, true);
                            try {
                                login(config, config.useRandomLogin);
                            } catch (Exception ex) {
                                String banMsg = config.useRandomLogin ? "" : " (possibly a 30-minute ban caused by frequent searches)";
                                writeLine(ex.getMessage() + banMsg, ConsoleColor.DarkYellow, true);
                            }
                        }

                        downloads.forEach((key, val) -> {
                            if (val != null) {
                                synchronized (val) {
                                    val.updateLastChangeTime(false, true);
                                }
                            }
                        });
                    }
                } catch (Exception ex) {
                    writeLine("\n" + ex.getMessage() + "\n", ConsoleColor.DarkYellow, true);
                }
            }

            try {
                Thread.sleep(updateInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void login(Config config, boolean random) throws Exception {
        String user = config.username;
        String pass = config.password;
        if (random) {
            Random r = new Random();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            user = r.ints(10, 0, chars.length())
                .mapToObj(i -> String.valueOf(chars.charAt(i)))
                .collect(Collectors.joining());
            pass = r.ints(10, 0, chars.length())
                .mapToObj(i -> String.valueOf(chars.charAt(i)))
                .collect(Collectors.joining());
        }

        writeLine("Login " + user);

        while (true) {
            try {
                writeLineIf("Connecting " + user, config.debugInfo);
                client.connectAsync(user, pass);
                if (!config.noModifyShareCount) {
                    writeLineIf("Setting share count", config.debugInfo);
                    client.setSharedCountsAsync(20, 100);
                }
                break;
            } catch (Exception e) {
                writeLineIf("Exception while logging in: " + e, config.debugInfo);
                if (!(e instanceof Soulseek.AddressException || e instanceof TimeoutException) && --tries == 0) {
                    throw e;
                }
            }
            Thread.sleep(500);
            writeLineIf("Retry login " + user, config.debugInfo);
        }

        writeLineIf("Logged in " + user, config.debugInfo);
    }

    static void onComplete(Config config, String onComplete, Track track) {
        if (onComplete.isEmpty()) {
            return;
        }

        boolean useShellExecute = false;
        int count = 0;

        while (onComplete.length() > 2 && count++ < 2) {
            if (onComplete.startsWith("s:")) {
                useShellExecute = true;
            } else if (Character.isDigit(onComplete.charAt(0)) && onComplete.charAt(1) == ':') {
                if (track.getState().ordinal() != Integer.parseInt(onComplete.substring(0, 1))) {
                    return;
                }
            } else {
                break;
            }
            onComplete = onComplete.substring(2);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> command = new ArrayList<>();

        onComplete = onComplete.replace("{title}", track.getTitle())
            .replace("{artist}", track.getArtist())
            .replace("{album}", track.getAlbum())
            .replace("{uri}", track.getURI())
