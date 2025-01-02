package com.slskbatchdl;

import com.slskbatchdl.models.Track;
import com.slskbatchdl.utils.Printing;
import com.slskbatchdl.utils.Utils;
import com.slskbatchdl.utils.ProgressBar;
import com.slskbatchdl.utils.SearchResponse;
import com.slskbatchdl.utils.SoulseekClientException;
import com.slskbatchdl.utils.TransferOptions;
import com.slskbatchdl.utils.TransferState;
import com.slskbatchdl.utils.DownloadWrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CancellationToken;
import java.util.concurrent.CancellationTokenSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Download {

    private static final ConcurrentMap<String, DownloadWrapper> downloads = new ConcurrentHashMap<>();

    public static void downloadFile(SearchResponse response, com.slskbatchdl.utils.File file, String filePath, Track track, ProgressBar progress, Config config, CancellationToken ct, CancellationTokenSource searchCts) throws Exception {
        Program.waitForLogin(config);
        Files.createDirectories(Paths.get(filePath).getParent());
        String origPath = filePath;
        filePath += ".incomplete";

        Printing.writeLineIf("Downloading: " + track + " to '" + filePath + "'", config.debugInfo);

        TransferOptions transferOptions = new TransferOptions(
            state -> {
                if (downloads.containsKey(file.getFilename())) {
                    downloads.get(file.getFilename()).setTransfer(state.getTransfer());
                }
            },
            progressUpdated -> {
                if (downloads.containsKey(file.getFilename())) {
                    downloads.get(file.getFilename()).setBytesTransferred(progressUpdated.getPreviousBytesTransferred());
                }
            }
        );

        try (CancellationTokenSource downloadCts = ct != null ? CancellationTokenSource.createLinkedTokenSource(ct) : new CancellationTokenSource();
             OutputStream outputStream = new FileOutputStream(filePath)) {

            DownloadWrapper wrapper = new DownloadWrapper(origPath, response, file, track, downloadCts, progress);
            downloads.put(file.getFilename(), wrapper);

            int maxRetries = 3;
            int retryCount = 0;
            while (true) {
                try {
                    Program.client.downloadAsync(response.getUsername(), file.getFilename(),
                        () -> outputStream,
                        file.getSize(), outputStream.getChannel().position(),
                        transferOptions, downloadCts.getToken());

                    break;
                } catch (SoulseekClientException e) {
                    retryCount++;

                    Printing.writeLineIf("Error while downloading: " + e, config.debugInfo, ConsoleColor.DarkYellow);

                    if (retryCount >= maxRetries || Program.isConnectedAndLoggedIn()) {
                        throw e;
                    }

                    Program.waitForLogin(config);
                }
            }
        } catch (Exception e) {
            if (Files.exists(Paths.get(filePath))) {
                try {
                    Files.delete(Paths.get(filePath));
                } catch (IOException ignored) {
                }
            }
            downloads.remove(file.getFilename());
            DownloadWrapper d = downloads.get(file.getFilename());
            if (d != null) {
                synchronized (d) {
                    d.updateText();
                }
            }
            throw e;
        }

        try {
            if (searchCts != null) {
                searchCts.cancel();
            }
        } catch (Exception ignored) {
        }

        try {
            Utils.move(filePath, origPath);
        } catch (IOException e) {
            Printing.writeLine("Failed to rename .incomplete file", ConsoleColor.DarkYellow, true);
        }

        downloads.remove(file.getFilename());

        DownloadWrapper x = downloads.get(file.getFilename());
        if (x != null) {
            synchronized (x) {
                x.setSuccess(true);
                x.updateText();
            }
        }
    }
}
