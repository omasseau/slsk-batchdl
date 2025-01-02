package com.slskbatchdl;

public class Enums {
    public enum FailureReason {
        None(0),
        InvalidSearchString(1),
        OutOfDownloadRetries(2),
        NoSuitableFileFound(3),
        AllDownloadsFailed(4);

        private final int value;

        FailureReason(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum TrackState {
        Initial(0),
        Downloaded(1),
        Failed(2),
        AlreadyExists(3),
        NotFoundLastTime(4);

        private final int value;

        TrackState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum SkipMode {
        Name(0),
        Tag(2),
        Index(4);

        private final int value;

        SkipMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum InputType {
        CSV,
        YouTube,
        Spotify,
        Bandcamp,
        String,
        List,
        None
    }

    public enum TrackType {
        Normal(0),
        Album(1),
        Aggregate(2),
        AlbumAggregate(3);

        private final int value;

        TrackType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum M3uOption {
        None,
        Index,
        Playlist,
        All
    }

    public enum PrintOption {
        None(0),
        Tracks(1),
        Results(2),
        Full(4);

        private final int value;

        PrintOption(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum AlbumArtOption {
        Default,
        Most,
        Largest
    }

    public enum Verbosity {
        Silent,
        Error,
        Warning,
        Normal,
        Verbose
    }

    public enum AlbumFailOption {
        Ignore,
        Keep,
        Delete
    }
}
