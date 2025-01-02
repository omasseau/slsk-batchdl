package com.slskbatchdl.Models;

import com.slskbatchdl.Utils;
import com.slskbatchdl.Enums;
import com.slskbatchdl.Soulseek;

public class SimpleFile {
    public String Path;
    public String Artists;
    public String Title;
    public String Album;
    public int Length;
    public int Bitrate;
    public int Samplerate;
    public int Bitdepth;

    public SimpleFile(TagLib.File file) {
        Path = file.getName();
        Artists = file.getTag().getJoinedPerformers();
        Title = file.getTag().getTitle();
        Album = file.getTag().getAlbum();
        Length = (int) file.getLength();
        Bitrate = file.getProperties().getAudioBitrate();
        Samplerate = file.getProperties().getAudioSampleRate();
        Bitdepth = file.getProperties().getBitsPerSample();
    }
}
