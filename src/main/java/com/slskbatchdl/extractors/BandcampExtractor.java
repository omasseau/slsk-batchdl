package com.slskbatchdl.extractors;

import com.slskbatchdl.Config;
import com.slskbatchdl.models.Track;
import com.slskbatchdl.models.TrackListEntry;
import com.slskbatchdl.models.TrackLists;
import com.slskbatchdl.utils.Printing;
import com.slskbatchdl.enums.TrackType;
import com.slskbatchdl.utils.Utils;
import com.slskbatchdl.utils.HtmlWeb;
import com.slskbatchdl.utils.HtmlDocument;
import com.slskbatchdl.utils.HtmlNode;
import com.slskbatchdl.utils.JsonDocument;
import com.slskbatchdl.utils.JsonElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BandcampExtractor implements IExtractor {

    public static boolean inputMatches(String input) {
        input = input.toLowerCase();
        return Utils.isInternetUrl(input) && input.contains("bandcamp.com");
    }

    @Override
    public TrackLists getTracks(String input, int maxTracks, int offset, boolean reverse, Config config) throws IOException {
        TrackLists trackLists = new TrackLists();
        boolean isTrack = input.contains("/track/");
        boolean isAlbum = !isTrack && input.contains("/album/");
        boolean isArtist = !isTrack && !isAlbum;

        if (isArtist) {
            System.out.println("Retrieving bandcamp artist discography..");
            String artistUrl = input.trim().replaceAll("/$", "");

            if (!artistUrl.endsWith("/music")) {
                artistUrl += "/music";
            }

            String response = Utils.httpGet(artistUrl);

            String idPattern = "band_id=(\\d+)&";
            Matcher match = Pattern.compile(idPattern).matcher(response);
            String id = match.find() ? match.group(1) : "";

            String address = "http://bandcamp.com/api/mobile/24/band_details?band_id=" + id;

            String responseString = Utils.httpGet(address);
            JsonDocument jsonDocument = JsonDocument.parse(responseString);
            JsonElement root = jsonDocument.getRootElement();

            String artistName = root.getProperty("name").getString();

            List<Track> tralbums = new ArrayList<>();

            for (JsonElement item : root.getProperty("discography").getArray()) {
                Track track = new Track();
                track.setAlbum(item.getProperty("title").getString());
                track.setArtist(item.getProperty("artist_name").getString() != null ? item.getProperty("artist_name").getString() : item.getProperty("band_name").getString());
                track.setType(TrackType.Album);

                TrackListEntry tle = new TrackListEntry(track);
                tle.setDefaultFolderName(track.getArtist());
                tle.setEnablesIndexByDefault(true);
                trackLists.addEntry(tle);
            }
        } else {
            System.out.println("Retrieving bandcamp item..");
            HtmlWeb web = new HtmlWeb();
            HtmlDocument doc = web.loadFromWeb(input);

            HtmlNode nameSection = doc.getDocumentNode().selectSingleNode("//div[@id='name-section']");
            String name = nameSection.selectSingleNode(".//h2[contains(@class, 'trackTitle')]").getInnerText().unHtmlString().trim();

            if (isAlbum) {
                String artist = nameSection.selectSingleNode(".//h3/span/a").getInnerText().unHtmlString().trim();
                Track track = new Track();
                track.setArtist(artist);
                track.setAlbum(name);
                track.setType(TrackType.Album);
                trackLists.addEntry(new TrackListEntry(track));

                if (config.setAlbumMinTrackCount || config.setAlbumMaxTrackCount) {
                    HtmlNode trackTable = doc.getDocumentNode().selectSingleNode("//*[@id='track_table']");
                    int n = trackTable.selectNodes(".//tr").size();

                    if (config.setAlbumMinTrackCount) {
                        track.setMinAlbumTrackCount(n);
                    }

                    if (config.setAlbumMaxTrackCount) {
                        track.setMaxAlbumTrackCount(n);
                    }
                }
            } else {
                String album = nameSection.selectSingleNode(".//h3[contains(@class, 'albumTitle')]/span/a").getInnerText().unHtmlString().trim();
                String artist = nameSection.selectSingleNode(".//h3[contains(@class, 'albumTitle')]/span[last()]/a").getInnerText().unHtmlString().trim();

                Track track = new Track();
                track.setArtist(artist);
                track.setTitle(name);
                track.setAlbum(album);
                trackLists.addEntry(new TrackListEntry(TrackType.Normal));
                trackLists.addTrackToLast(track);
            }
        }

        if (reverse) {
            trackLists.reverse();
        }

        if (offset > 0 || maxTracks < Integer.MAX_VALUE) {
            trackLists = TrackLists.fromFlattened(trackLists.flattened(true, false).subList(offset, Math.min(offset + maxTracks, trackLists.flattened(true, false).size())));
        }

        return trackLists;
    }
}
