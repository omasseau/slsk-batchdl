package com.slskbatchdl;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class Config {
    public FileConditions necessaryCond = new FileConditions(
        new String[] { ".mp3", ".flac", ".ogg", ".m4a", ".opus", ".wav", ".aac", ".alac" }
    );

    public FileConditions preferredCond = new FileConditions(
        new String[] { "mp3" },
        3,
        200,
        2500,
        48000,
        true,
        true,
        false
    );

    public String parentDir = Paths.get("").toAbsolutePath().toString();
    public String input = "";
    public String m3uFilePath = "";
    public String indexFilePath = "";
    public String skipMusicDir = "";
    public String spotifyId = "";
    public String spotifySecret = "";
    public String spotifyToken = "";
    public String spotifyRefresh = "";
    public String ytKey = "";
    public String username = "";
    public String password = "";
    public String artistCol = "";
    public String albumCol = "";
    public String titleCol = "";
    public String ytIdCol = "";
    public String descCol = "";
    public String trackCountCol = "";
    public String lengthCol = "";
    public String timeUnit = "s";
    public String nameFormat = "";
    public String invalidReplaceStr = " ";
    public String ytdlpArgument = "";
    public String onComplete = "";
    public String confPath = "";
    public String profile = "";
    public String failedAlbumPath = "";
    public boolean aggregate = false;
    public boolean album = false;
    public boolean albumArtOnly = false;
    public boolean interactiveMode = false;
    public boolean setAlbumMinTrackCount = true;
    public boolean setAlbumMaxTrackCount = false;
    public boolean skipNotFound = false;
    public boolean desperateSearch = false;
    public boolean noRemoveSpecialChars = false;
    public boolean artistMaybeWrong = false;
    public boolean fastSearch = false;
    public boolean ytParse = false;
    public boolean removeFt = false;
    public boolean removeBrackets = false;
    public boolean reverse = false;
    public boolean useYtdlp = false;
    public boolean removeTracksFromSource = false;
    public boolean getDeleted = false;
    public boolean deletedOnly = false;
    public boolean removeSingleCharacterSearchTerms = false;
    public boolean relax = false;
    public boolean debugInfo = false;
    public boolean noModifyShareCount = false;
    public boolean useRandomLogin = false;
    public boolean noBrowseFolder = false;
    public boolean skipCheckCond = false;
    public boolean skipCheckPrefCond = false;
    public boolean noProgress = false;
    public boolean writePlaylist = false;
    public boolean skipExisting = true;
    public boolean writeIndex = true;
    public boolean parallelAlbumSearch = false;
    public int downrankOn = -1;
    public int ignoreOn = -2;
    public int minAlbumTrackCount = -1;
    public int maxAlbumTrackCount = -1;
    public int fastSearchDelay = 300;
    public int minSharesAggregate = 2;
    public int maxTracks = Integer.MAX_VALUE;
    public int offset = 0;
    public int maxStaleTime = 50000;
    public int searchTimeout = 6000;
    public int concurrentProcesses = 2;
    public int unknownErrorRetries = 2;
    public int maxRetriesPerTrack = 30;
    public int listenPort = 49998;
    public int searchesPerTime = 34;
    public int searchRenewTime = 220;
    public int aggregateLengthTol = 3;
    public int parallelAlbumSearchProcesses = 5;
    public double fastSearchMinUpSpeed = 1.0;
    public Track regexToReplace = new Track();
    public Track regexReplaceBy = new Track();
    public AlbumArtOption albumArtOption = AlbumArtOption.Default;
    public InputType inputType = InputType.None;
    public SkipMode skipMode = SkipMode.Index;
    public SkipMode skipModeMusicDir = SkipMode.Name;
    public PrintOption printOption = PrintOption.None;

    public boolean hasAutoProfiles = false;

    public boolean doNotDownload() {
        return (printOption & (PrintOption.Results | PrintOption.Tracks)) != 0;
    }

    public boolean printTracks() {
        return (printOption & PrintOption.Tracks) != 0;
    }

    public boolean printResults() {
        return (printOption & PrintOption.Results) != 0;
    }

    public boolean printTracksFull() {
        return (printOption & PrintOption.Tracks) != 0 && (printOption & PrintOption.Full) != 0;
    }

    public boolean printResultsFull() {
        return (printOption & PrintOption.Results) != 0 && (printOption & PrintOption.Full) != 0;
    }

    public boolean deleteAlbumOnFail() {
        return failedAlbumPath.equals("delete");
    }

    public boolean ignoreAlbumFail() {
        return failedAlbumPath.equals("disable");
    }

    private Map<String, Profile> configProfiles;
    private Set<String> appliedProfiles;
    private String[] arguments;
    boolean hasConfiguredIndex = false;
    boolean confPathChanged = false;
    FileConditions undoTempConds = null;
    FileConditions undoTempPrefConds = null;

    public Config(String[] args) {
        configProfiles = new HashMap<>();
        appliedProfiles = new HashSet<>();
        arguments = args;

        arguments = Arrays.stream(args)
            .flatMap(arg -> {
                if (arg.length() > 2 && arg.charAt(0) == '-') {
                    if (arg.charAt(1) == '-') {
                        if (arg.length() > 3 && arg.contains("=")) {
                            return Arrays.stream(arg.split("=", 2)); // --arg=val becomes --arg val
                        }
                    } else if (!arg.contains(" ")) {
                        return arg.substring(1).chars().mapToObj(c -> "-" + (char) c); // -abc becomes -a -b -c
                    }
                }
                return Stream.of(arg);
            })
            .toArray(String[]::new);

        setConfigPath(arguments);

        if (!confPath.equals("none") && (confPathChanged || Files.exists(Paths.get(confPath)))) {
            parseConfig(confPath);
            applyDefaultConfig();
        }

        int profileIndex = Arrays.asList(arguments).lastIndexOf("--profile");

        if (profileIndex != -1) {
            profile = arguments[profileIndex + 1];
            if (profile.equals("help")) {
                listProfiles();
                System.exit(0);
            }
        }

        applyProfiles(profile);

        processArgs(arguments);
    }

    public Config() {
    }

    public Config copy() {
        Config copy = null;
        try {
            copy = (Config) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        copy.necessaryCond = new FileConditions(necessaryCond);
        copy.preferredCond = new FileConditions(preferredCond);

        if (undoTempConds != null) {
            copy.undoTempConds = new FileConditions(undoTempConds);
        }
        if (undoTempPrefConds != null) {
            copy.undoTempPrefConds = new FileConditions(undoTempPrefConds);
        }

        copy.regexToReplace = new Track(regexToReplace);
        copy.regexReplaceBy = new Track(regexReplaceBy);

        copy.appliedProfiles = new HashSet<>(appliedProfiles);

        copy.configProfiles = configProfiles;
        copy.arguments = arguments;

        return copy;
    }

    void setConfigPath(String[] args) {
        int idx = Arrays.asList(args).lastIndexOf("-c");
        if (idx == -1) {
            idx = Arrays.asList(args).lastIndexOf("--config");
        }

        if (idx != -1) {
            confPath = Utils.expandUser(args[idx + 1]);
            confPathChanged = true;

            if (Files.exists(Paths.get(System.getProperty("user.dir"), confPath))) {
                confPath = Paths.get(System.getProperty("user.dir"), confPath).toString();
            }
        }

        if (!confPathChanged) {
            String[] configPaths = new String[]{
                Paths.get(System.getProperty("user.home"), ".config", "sldl", "sldl.conf").toString(),
                Paths.get(System.getenv("APPDATA"), "sldl", "sldl.conf").toString(),
                Paths.get(System.getProperty("user.dir"), "sldl.conf").toString()
            };

            for (String path : configPaths) {
                if (Files.exists(Paths.get(path))) {
                    confPath = path;
                    break;
                }
            }
        }
    }

    public void postProcessArgs() {
        if (doNotDownload() || debugInfo) {
            concurrentProcesses = 1;
        }

        ignoreOn = Math.min(ignoreOn, downrankOn);

        if (doNotDownload()) {
            writeIndex = false;
        } else if (!hasConfiguredIndex && Program.trackLists != null && !Program.trackLists.lists.stream().anyMatch(x -> x.enablesIndexByDefault)) {
            writeIndex = false;
        }

        if (albumArtOnly && albumArtOption == AlbumArtOption.Default) {
            albumArtOption = AlbumArtOption.Largest;
        }

        nameFormat = nameFormat.trim();

        confPath = Utils.getFullPath(Utils.expandUser(confPath));
        parentDir = Utils.getFullPath(Utils.expandUser(parentDir));
        m3uFilePath = Utils.getFullPath(Utils.expandUser(m3uFilePath));
        indexFilePath = Utils.getFullPath(Utils.expandUser(indexFilePath));
        skipMusicDir = Utils.getFullPath(Utils.expandUser(skipMusicDir));
        failedAlbumPath = Utils.getFullPath(Utils.expandUser(failedAlbumPath));

        if (failedAlbumPath.length() == 0) {
            failedAlbumPath = Paths.get(parentDir, "failed").toString();
        }
    }

    void parseConfig(String path) {
        List<String> lines = null;
        try {
            lines = Files.readAllLines(Paths.get(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String curProfile = "default";

        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.length() == 0 || l.startsWith("#")) {
                continue;
            }

            if (l.startsWith("[") && l.endsWith("]")) {
                curProfile = l.substring(1, l.length() - 1);
                continue;
            }

            int idx = l.indexOf("=");
            if (idx <= 0 || idx == l.length() - 1) {
                throw new IllegalArgumentException("Error parsing config '" + path + "' at line " + i);
            }

            String[] x = l.split("=", 2);
            String key = x[0].trim();
            String val = x[1].trim();

            if (val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"') {
                val = val.substring(1, val.length() - 1);
            }

            if (!configProfiles.containsKey(curProfile)) {
                configProfiles.put(curProfile, new Profile());
            }

            if (key.equals("profile-cond") && !curProfile.equals("default")) {
                configProfiles.get(curProfile).cond = val;
                hasAutoProfiles = true;
            } else {
                if (key.length() == 1) {
                    key = "-" + key;
                } else {
                    key = "--" + key;
                }

                configProfiles.get(curProfile).args.add(key);
                configProfiles.get(curProfile).args.add(val);
            }
        }
    }

    public boolean needUpdateProfiles(TrackListEntry tle) {
        if (doNotDownload()) {
            return false;
        }
        if (!hasAutoProfiles) {
            return false;
        }

        for (Map.Entry<String, Profile> entry : configProfiles.entrySet()) {
            String key = entry.getKey();
            Profile val = entry.getValue();

            if (key.equals("default") || val.cond == null) {
                continue;
            }

            boolean condSatisfied = profileConditionSatisfied(val.cond, tle);
            boolean alreadyApplied = appliedProfiles.contains(key);

            if (condSatisfied && !alreadyApplied) {
                return true;
            }
            if (!condSatisfied && alreadyApplied) {
                return true;
            }
        }

        return false;
    }

    public boolean needUpdateProfiles(TrackListEntry tle, List<Profile> toApply) {
        toApply.clear();

        if (doNotDownload()) {
            return false;
        }
        if (!hasAutoProfiles) {
            return false;
        }

        boolean needUpdate = false;

        for (Map.Entry<String, Profile> entry : configProfiles.entrySet()) {
            String key = entry.getKey();
            Profile val = entry.getValue();

            if (key.equals("default") || val.cond == null) {
                continue;
            }

            boolean condSatisfied = profileConditionSatisfied(val.cond, tle);
            boolean alreadyApplied = appliedProfiles.contains(key);

            if (condSatisfied && !alreadyApplied) {
                needUpdate = true;
            }
            if (!condSatisfied && alreadyApplied) {
                needUpdate = true;
            }

            if (condSatisfied) {
                toApply.add(val);
            }
        }

        return needUpdate;
    }

    public void updateProfiles(TrackListEntry tle) {
        List<Profile> toApply = new ArrayList<>();
        if (!needUpdateProfiles(tle, toApply)) {
            return;
        }

        applyDefaultConfig();
        applyProfiles(profile);

        for (Profile profile : toApply) {
            System.out.println("Applying auto profile: " + profile);
            processArgs(profile.args.toArray(new String[0]));
            appliedProfiles.add(profile.toString());
        }

        processArgs(arguments);
        postProcessArgs();
    }

    void applyDefaultConfig() {
        if (configProfiles.containsKey("default")) {
            processArgs(configProfiles.get("default").args.toArray(new String[0]));
            appliedProfiles.add("default");
        }
    }

    void applyProfiles(String names) {
        for (String name : names.split(",")) {
            if (name.length() > 0 && !name.equals("default")) {
                if (configProfiles.containsKey(name)) {
                    processArgs(configProfiles.get(name).args.toArray(new String[0]));
                    appliedProfiles.add(name);
                } else {
                    System.out.println("Error: No profile '" + name + "' found in config");
                }
            }
        }
    }

    Object getVarValue(String var, TrackListEntry tle) {
        switch (var) {
            case "input-type":
                return inputType.toString().toLowerCase();
            case "download-mode":
                if (tle != null) {
                    return tle.source.Type.toString().toLowerCase();
                } else if (album && aggregate) {
                    return "album-aggregate";
                } else if (album) {
                    return "album";
                } else if (aggregate) {
                    return "aggregate";
                } else {
                    return "normal";
                }
            case "interactive":
                return interactiveMode;
            case "album":
                return album;
            case "aggregate":
                return aggregate;
            default:
                throw new IllegalArgumentException("Unrecognized profile condition variable " + var);
        }
    }

    public boolean profileConditionSatisfied(String cond, TrackListEntry tle) {
        Queue<String> tokens = new LinkedList<>(Arrays.asList(cond.split("(?<=\\s+|\\(|\\)|&&|\\|\\||==|!=|!|\".*?\")")));

        boolean parseExpression() {
            boolean left = parseAndExpression();

            while (!tokens.isEmpty()) {
                String token = tokens.peek();
                if (token.equals("||")) {
                    tokens.poll();
                    boolean right = parseAndExpression();
                    left = left || right;
                } else {
                    break;
                }
            }

            return left;
        }

        boolean parseAndExpression() {
            boolean left = parsePrimaryExpression();

            while (!tokens.isEmpty()) {
                String token = tokens.peek();
                if (token.equals("&&")) {
                    tokens.poll();
                    boolean right = parsePrimaryExpression();
                    left = left && right;
                } else {
                    break;
                }
            }

            return left;
        }

        boolean parsePrimaryExpression() {
            String token = tokens.poll();

            if (token.equals("(")) {
                boolean result = parseExpression();
                tokens.poll();
                return result;
            }

            if (token.equals("!")) {
                return !parsePrimaryExpression();
            }

            if (token.startsWith("\"")) {
                throw new IllegalArgumentException("Invalid token at this position: " + token);
            }

            if (!tokens.isEmpty() && (tokens.peek().equals("==") || tokens.peek().equals("!="))) {
                String op = tokens.poll();
                String value = tokens.poll().trim().toLowerCase();
                String varValue = getVarValue(token, tle).toString().toLowerCase();
                return op.equals("==") ? varValue.equals(value) : !varValue.equals(value);
            }

            return (boolean) getVarValue(token, tle);
        }

        return parseExpression();
    }

    void listProfiles() {
        System.out.println("Available profiles:");
        for (Map.Entry<String, Profile> entry : configProfiles.entrySet()) {
            String key = entry.getKey();
            Profile val = entry.getValue();

            if (key.equals("default")) {
                continue;
            }
            System.out.println("  [" + key + "]");
            if (val.cond != null) {
                System.out.println("    profile-cond = " + val.cond);
            }
            for (int i = 0; i < val.args.size(); i += 2) {
                System.out.println("    " + val.args.get(i).replaceFirst("--", "") + " = " + val.args.get(i + 1));
            }
            System.out.println();
        }
    }

    public void addTemporaryConditions(FileConditions cond, FileConditions prefCond) {
        throw new UnsupportedOperationException("Code has been refactored; probably does not work.");
        if (cond != null) {
            undoTempConds = necessaryCond.addConditions(cond);
        }
        if (prefCond != null) {
            undoTempPrefConds = preferredCond.addConditions(prefCond);
        }
    }

    public void restoreConditions() {
        throw new UnsupportedOperationException("Code has been refactored; probably does not work.");
        if (undoTempConds != null) {
            necessaryCond.addConditions(undoTempConds);
        }
        if (undoTempPrefConds != null) {
            preferredCond.addConditions(undoTempPrefConds);
        }
    }

    public static FileConditions parseConditions(String input) {
        FileConditions cond = new FileConditions();

        String[] conditions = input.split(";", -1);
        for (String condition : conditions) {
            String[] parts = condition.split(">=|<=|=|>|<", 2);
            String field = parts[0].replace("-", "").trim().toLowerCase();
            String value = parts.length > 1 ? parts[1].trim() : "true";

            switch (field) {
                case "sr":
                case "samplerate":
                    updateMinMax(value, condition, cond::setMinSampleRate, cond::setMaxSampleRate);
                    break;
                case "br":
                case "bitrate":
                    updateMinMax(value, condition, cond::setMinBitrate, cond::setMaxBitrate);
                    break;
                case "bd":
                case "bitdepth":
                    updateMinMax(value, condition, cond::setMinBitDepth, cond::setMaxBitDepth);
                    break;
                case "t":
                case "tol":
                case "lentol":
                case "lengthtol":
                case "tolerance":
                case "lengthtolerance":
                    cond.setLengthTolerance(Integer.parseInt(value));
                    break;
                case "f":
                case "format":
                case "formats":
                    cond.setFormats(value.split(",", -1));
                    break;
                case "banned":
                case "bannedusers":
                    cond.setBannedUsers(value.split(",", -1));
                    break;
                case "stricttitle":
                    cond.setStrictTitle(Boolean.parseBoolean(value));
                    break;
                case "strictartist":
                    cond.setStrictArtist(Boolean.parseBoolean(value));
                    break;
                case "strictalbum":
                    cond.setStrictAlbum(Boolean.parseBoolean(value));
                    break;
                case "acceptnolen":
                case "acceptnolength":
                    cond.setAcceptNoLength(Boolean.parseBoolean(value));
                    break;
                case "strict":
                case "strictconditions":
                case "acceptmissing":
                case "acceptmissingprops":
                    cond.setAcceptMissingProps(Boolean.parseBoolean(value));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown condition '" + condition + "'");
            }
        }

        return cond;
    }

    static void updateMinMax(String value, String condition, Consumer<Integer> setMin, Consumer<Integer> setMax) {
        if (condition.contains(">=")) {
            setMin.accept(Integer.parseInt(value));
        } else if (condition.contains("<=")) {
            setMax.accept(Integer.parseInt(value));
        } else if (condition.contains(">")) {
            setMin.accept(Integer.parseInt(value) + 1);
        } else if (condition.contains("<")) {
            setMax.accept(Integer.parseInt(value) - 1);
        } else if (condition.contains("=")) {
            setMin.accept(Integer.parseInt(value));
            setMax.accept(Integer.parseInt(value));
        }
    }

    void processArgs(String[] args) {
        boolean inputSet = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                switch (args[i]) {
                    case "-i":
                    case "--input":
                        input = args[++i];
                        break;
                    case "--it":
                    case "--input-type":
                        inputType = InputType.valueOf(args[++i].toUpperCase().trim());
                        break;
                    case "-p":
                    case "--path":
                    case "--parent":
                        parentDir = args[++i];
                        break;
                    case "-c":
                    case "--config":
                        confPath = args[++i];
                        break;
                    case "--smd":
                    case "--skip-music-dir":
                        skipMusicDir = args[++i];
                        break;
                    case "-g":
                    case "--aggregate":
                        aggregate = true;
                        break;
                    case "--msa":
                    case "--min-shares-aggregate":
                        minSharesAggregate = Integer.parseInt(args[++i]);
                        break;
                    case "--rf":
                    case "--relax":
                    case "--relax-filtering":
                        relax = true;
                        break;
                    case "--si":
                    case "--spotify-id":
                        spotifyId = args[++i];
                        break;
                    case "--ss":
                    case "--spotify-secret":
                        spotifySecret = args[++i];
                        break;
                    case "--stk":
                    case "--spotify-token":
                        spotifyToken = args[++i];
                        break;
                    case "--str":
                    case "--spotify-refresh":
                        spotifyRefresh = args[++i];
                        break;
                    case "--yk":
                    case "--youtube-key":
                        ytKey = args[++i];
                        break;
                    case "-l":
                    case "--login":
                        String[] login = args[++i].split(";", 2);
                        username = login[0];
                        password = login[1];
                        break;
                    case "--user":
                    case "--username":
                        username = args[++i];
                        break;
                    case "--pass":
                    case "--password":
                        password = args[++i];
                        break;
                    case "--rl":
                    case "--random-login":
                        useRandomLogin = true;
                        break;
                    case "--ac":
                    case "--artist-col":
                        artistCol = args[++i];
                        break;
                    case "--tc":
                    case "--track-col":
                    case "--title-col":
                        titleCol = args[++i];
                        break;
                    case "--alc":
                    case "--album-col":
                        albumCol = args[++i];
                        break;
                    case "--ydc":
                    case "--yt-desc-col":
                        descCol = args[++i];
                        break;
                    case "--atcc":
                    case "--album-track-count-col":
                        trackCountCol = args[++i];
                        break;
                    case "--yic":
                    case "--yt-id-col":
                        ytIdCol = args[++i];
                        break;
                    case "--lc":
                    case "--length-col":
                        lengthCol = args[++i];
                        break;
                    case "--tf":
                    case "--time-format":
                        timeUnit = args[++i];
                        break;
                    case "-n":
                    case "--number":
                        maxTracks = Integer.parseInt(args[++i]);
                        break;
                    case "-o":
                    case "--offset":
                        offset = Integer.parseInt(args[++i]);
                        break;
                    case "--nf":
                    case "--name-format":
                        nameFormat = args[++i];
                        break;
                    case "--irs":
                    case "--invalid-replace-str":
                        invalidReplaceStr = args[++i];
                        break;
                    case "--print":
                        printOption = PrintOption.valueOf(args[++i].toUpperCase().trim());
                        break;
                    case "--pt":
                    case "--print-tracks":
                        printOption = PrintOption.Tracks;
                        break;
                    case "--ptf":
                    case "--print-tracks-full":
                        printOption = PrintOption.Tracks | PrintOption.Full;
                        break;
                    case "--pr":
                    case "--print-results":
                        printOption = PrintOption.Results;
                        break;
                    case "--prf":
                    case "--print-results-full":
                        printOption = PrintOption.Results | PrintOption.Full;
                        break;
                    case "--yp":
                    case "--yt-parse":
                        ytParse = true;
                        break;
                    case "--yd":
                    case "--yt-dlp":
                        useYtdlp = true;
                        break;
                    case "--nse":
                    case "--no-skip-existing":
                        skipExisting = false;
                        break;
                    case "--snf":
                    case "--skip-not-found":
                        skipNotFound = true;
                        break;
                    case "--rfp":
                    case "--rfs":
                    case "--remove-from-source":
                    case "--remove-from-playlist":
                        removeTracksFromSource = true;
                        break;
                    case "--rft":
                    case "--remove-ft":
                        removeFt = true;
                        break;
                    case "--rb":
                    case "--remove-brackets":
                        removeBrackets = true;
                        break;
                    case "--gd":
                    case "--get-deleted":
                        getDeleted = true;
                        break;
                    case "--do":
                    case "--deleted-only":
                        getDeleted = true;
                        deletedOnly = true;
                        break;
                    case "--re":
                    case "--regex":
                        String s = args[++i].replace("\\;", "<<semicol>>");
                        String applyTo = "TAL";

                        if (s.length() > 2 && s.charAt(1) == ':' && (s.charAt(0) == 'T' || s.charAt(0) == 'A' || s.charAt(0) == 'L')) {
                            applyTo = s.substring(0, 1);
                            s = s.substring(2);
                        }

                        String[] parts = s.split(";");
                        String toReplace = parts[0].replace("<<semicol>>", ";");
                        String replaceBy = parts.length > 1 ? parts[1].replace("<<semicol>>", ";") : "";

                        if (applyTo.contains("T")) {
                            regexToReplace.setTitle(toReplace);
                            regexReplaceBy.setTitle(replaceBy);
                        }
                        if (applyTo.contains("A")) {
                            regexToReplace.setArtist(toReplace);
                            regexReplaceBy.setArtist(replaceBy);
                        }
                        if (applyTo.contains("L")) {
                            regexToReplace.setAlbum(toReplace);
                            regexReplaceBy.setAlbum(replaceBy);
                        }
                        break;
                    case "-r":
                    case "--reverse":
                        reverse = true;
                        break;
                    case "--wp":
                    case "--write-playlist":
                        writePlaylist = true;
                        break;
                    case "--pp":
                    case "--playlist-path":
                        m3uFilePath = args[++i];
                        break;
                    case "--nwi":
                    case "--no-write-index":
                        hasConfiguredIndex = true;
                        writeIndex = false;
                        break;
                    case "--ip":
                    case "--index-path":
                        hasConfiguredIndex = true;
                        indexFilePath = args[++i];
                        break;
                    case "--lp":
                    case "--port":
                    case "--listen-port":
                        listenPort = Integer.parseInt(args[++i]);
                        break;
                    case "--st":
                    case "--search-time":
                    case "--search-timeout":
                        searchTimeout = Integer.parseInt(args[++i]);
                        break;
                    case "--Mst":
                    case "--stale-time":
                    case "--max-stale-time":
                        maxStaleTime = Integer.parseInt(args[++i]);
                        break;
                    case "--cp":
                    case "--cd":
                    case "--processes":
                    case "--concurrent-processes":
                    case "--concurrent-downloads":
                        concurrentProcesses = Integer.parseInt(args[++i]);
                        break;
                    case "--spt":
                    case "--searches-per-time":
                        searchesPerTime = Integer.parseInt(args[++i]);
                        break;
                    case "--srt":
                    case "--searches-renew-time":
                        searchRenewTime = Integer.parseInt(args[++i]);
                        break;
                    case "--Mr":
                    case "--retries":
                    case "--max-retries":
                        maxRetriesPerTrack = Integer.parseInt(args[++i]);
                        break;
                    case "--atc":
                    case "--album-track-count":
                        String a = args[++i];
                        if (a.equals("-1")) {
                            minAlbumTrackCount = -1;
                            maxAlbumTrackCount = -1;
                        } else if (a.endsWith("-")) {
                            maxAlbumTrackCount = Integer.parseInt(a.substring(0, a.length() - 1));
                        } else if (a.endsWith("+")) {
                            minAlbumTrackCount = Integer.parseInt(a.substring(0, a.length() - 1));
                        } else {
                            minAlbumTrackCount = Integer.parseInt(a);
                            maxAlbumTrackCount = minAlbumTrackCount;
                        }
                        break;
                    case "--matc":
                    case "--min-album-track-count":
                        minAlbumTrackCount = Integer.parseInt(args[++i]);
                        break;
                    case "--Matc":
                    case "--max-album-track-count":
                        maxAlbumTrackCount = Integer.parseInt(args[++i]);
                        break;
                    case "--eMtc":
                    case "--extract-max-track-count":
                        setAlbumMaxTrackCount = true;
                        break;
                    case "--emtc":
                    case "--extract-min-track-count":
                        setAlbumMinTrackCount = true;
                        break;
                    case "--aa":
                    case "--album-art":
                        albumArtOption = AlbumArtOption.valueOf(args[++i].toUpperCase().trim());
                        break;
                    case "--aao":
                    case "--aa-only":
                    case "--album-art-only":
                        albumArtOnly = true;
                        if (albumArtOption == AlbumArtOption.Default) {
                            albumArtOption = AlbumArtOption.Largest;
                        }
                        preferredCond = new FileConditions();
                        necessaryCond = new FileConditions();
                        break;
                    case "--fap":
                    case "--failed-album-path":
                        failedAlbumPath = args[++i];
                        break;
                    case "-t":
                    case "--interactive":
                        interactiveMode = true;
                        break;
                    case "--pf":
                    case "--paf":
                    case "--pref-format":
                        preferredCond.setFormats(args[++i].split(",", -1));
                        break;
                    case "--plt":
                    case "--pref-tolerance":
                    case "--pref-length-tol":
                    case "--pref-length-tolerance":
                        preferredCond.setLengthTolerance(Integer.parseInt(args[++i]));
                        break;
                    case "--pmbr":
                    case "--pref-min-bitrate":
                        preferredCond.setMinBitrate(Integer.parseInt(args[++i]));
                        break;
                    case "--pMbr":
                    case "--pref-max-bitrate":
                        preferredCond.setMaxBitrate(Integer.parseInt(args[++i]));
                        break;
                    case "--pmsr":
                    case "--pref-min-samplerate":
                        preferredCond.setMinSampleRate(Integer.parseInt(args[++i]));
                        break;
                    case "--pMsr":
                    case "--pref-max-samplerate":
                        preferredCond.setMaxSampleRate(Integer.parseInt(args[++i]));
                        break;
                    case "--pmbd":
                    case "--pref-min-bitdepth":
                        preferredCond.setMinBitDepth(Integer.parseInt(args[++i]));
                        break;
                    case "--pMbd":
                    case "--pref-max-bitdepth":
                        preferredCond.setMaxBitDepth(Integer.parseInt(args[++i]));
                        break;
                    case "--pst":
                    case "--pstt":
                    case "--pref-strict-title":
                        preferredCond.setStrictTitle(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--psa":
                    case "--pref-strict-artist":
                        preferredCond.setStrictArtist(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--psal":
                    case "--pref-strict-album":
                        preferredCond.setStrictAlbum(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--panl":
                    case "--pref-accept-no-length":
                        preferredCond.setAcceptNoLength(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--pbu":
                    case "--pref-banned-users":
                        preferredCond.setBannedUsers(args[++i].split(",", -1));
                        break;
                    case "--af":
                    case "--format":
                        necessaryCond.setFormats(args[++i].split(",", -1));
                        break;
                    case "--lt":
                    case "--tolerance":
                    case "--length-tol":
                    case "--length-tolerance":
                        necessaryCond.setLengthTolerance(Integer.parseInt(args[++i]));
                        break;
                    case "--mbr":
                    case "--min-bitrate":
                        necessaryCond.setMinBitrate(Integer.parseInt(args[++i]));
                        break;
                    case "--Mbr":
                    case "--max-bitrate":
                        necessaryCond.setMaxBitrate(Integer.parseInt(args[++i]));
                        break;
                    case "--msr":
                    case "--min-samplerate":
                        necessaryCond.setMinSampleRate(Integer.parseInt(args[++i]));
                        break;
                    case "--Msr":
                    case "--max-samplerate":
                        necessaryCond.setMaxSampleRate(Integer.parseInt(args[++i]));
                        break;
                    case "--mbd":
                    case "--min-bitdepth":
                        necessaryCond.setMinBitDepth(Integer.parseInt(args[++i]));
                        break;
                    case "--Mbd":
                    case "--max-bitdepth":
                        necessaryCond.setMaxBitDepth(Integer.parseInt(args[++i]));
                        break;
                    case "--stt":
                    case "--strict-title":
                        necessaryCond.setStrictTitle(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--sa":
                    case "--strict-artist":
                        necessaryCond.setStrictArtist(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--sal":
                    case "--strict-album":
                        necessaryCond.setStrictAlbum(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--bu":
                    case "--banned-users":
                        necessaryCond.setBannedUsers(args[++i].split(",", -1));
                        break;
                    case "--anl":
                    case "--accept-no-length":
                        necessaryCond.setAcceptNoLength(Boolean.parseBoolean(args[++i]));
                        break;
                    case "--cond":
                    case "--conditions":
                        necessaryCond.addConditions(parseConditions(args[++i]));
                        break;
                    case "--pc":
                    case "--pref":
                    case "--preferred-conditions":
                        preferredCond.addConditions(parseConditions(args[++i]));
                        break;
                    case "--nmsc":
                    case "--no-modify-share-count":
                        noModifyShareCount = true;
                        break;
                    case "-d":
                    case "--desperate":
                        desperateSearch = true;
                        break;
                    case "--np":
                    case "--no-progress":
                        noProgress = true;
                        break;
                    case "--smod":
                    case "--skip-mode-output-dir":
                        skipMode = SkipMode.valueOf(args[++i].toUpperCase().trim());
                        break;
                    case "--smmd":
                    case "--skip-mode-music-dir":
                        skipModeMusicDir = SkipMode.valueOf(args[++i].toUpperCase().trim());
                        break;
                    case "--nrsc":
                    case "--no-remove-special-chars":
                        noRemoveSpecialChars = true;
                        break;
                    case "--amw":
                    case "--artist-maybe-wrong":
                        artistMaybeWrong = true;
                        break;
                    case "--fs":
                    case "--fast-search":
                        fastSearch = true;
                        break;
                    case "--fsd":
                    case "--fast-search-delay":
                        fastSearchDelay = Integer.parseInt(args[++i]);
                        break;
                    case "--fsmus":
                    case "--fast-search-min-up-speed":
                        fastSearchMinUpSpeed = Double.parseDouble(args[++i]);
                        break;
                    case "--debug":
                        debugInfo = true;
                        break;
                    case "--sc":
                    case "--strict":
                    case "--strict-conditions":
                        preferredCond.setAcceptMissingProps(false);
                        necessaryCond.setAcceptMissingProps(false);
                        break;
                    case "--yda":
                    case "--yt-dlp-argument":
                        ytdlpArgument = args[++i];
                        break;
                    case "-a":
                    case "--album":
                        album = true;
                        break;
                    case "--oc":
                    case "--on-complete":
                        onComplete = args[++i];
                        break;
                    case "--ftd":
                    case "--fails-to-downrank":
                        downrankOn = -Integer.parseInt(args[++i]);
                        break;
                    case "--fti":
                    case "--fails-to-ignore":
                        ignoreOn = -Integer.parseInt(args[++i]);
                        break;
                    case "--uer":
                    case "--unknown-error-retries":
                        unknownErrorRetries = Integer.parseInt(args[++i]);
                        break;
                    case "--profile":
                        profile = args[++i];
                        break;
                    case "--nbf":
                    case "--no-browse-folder":
                        noBrowseFolder = true;
                        break;
                    case "--scc":
                    case "--skip-check-cond":
                        skipCheckCond = true;
                        break;
                    case "--scpc":
                    case "--skip-check-pref-cond":
                        skipCheckPrefCond = true;
                        break;
                    case "--alt":
                    case "--aggregate-length-tol":
                        aggregateLengthTol = Integer.parseInt(args[++i]);
                        break;
                    case "--aps":
                    case "--album-parallel-search":
                        parallelAlbumSearch = true;
                        break;
                    case "--apsc":
                    case "--album-parallel-search-count":
                        parallelAlbumSearchProcesses = Integer.parseInt(args[++i]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            } else {
                if (!inputSet) {
                    input = args[i].trim();
                    inputSet = true;
                } else {
                    throw new IllegalArgumentException("Invalid argument '" + args[i] + "'. Input is already set to '" + input + "'");
                }
            }
        }
    }

    public static String[] getArgsArray(String commandLine) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args.toArray(new String[0]);
    }

    private static class Profile {
        List<String> args = new ArrayList<>();
        String cond;
    }
}
