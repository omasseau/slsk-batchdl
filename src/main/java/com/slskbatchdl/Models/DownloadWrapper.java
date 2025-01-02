package com.slskbatchdl.Models;

import com.slskbatchdl.Soulseek.File;
import com.slskbatchdl.Soulseek.SearchResponse;
import com.slskbatchdl.Transfer;
import com.slskbatchdl.TransferStates;
import com.slskbatchdl.Utilities.Printing;
import com.slskbatchdl.Models.Track;
import com.slskbatchdl.Program;
import com.konsole.ProgressBar;

import java.util.Date;
import java.util.concurrent.CancellationTokenSource;

public class DownloadWrapper {
    public String savePath;
    public String displayText = "";
    public int barState = 0;
    public File file;
    public Transfer transfer;
    public SearchResponse response;
    public ProgressBar progress;
    public Track track;
    public long bytesTransferred = 0;
    public boolean stalled = false;
    public boolean queued = false;
    public boolean success = false;
    public CancellationTokenSource cts;
    public Date startTime = new Date();
    public Date lastChangeTime = new Date();

    TransferStates prevTransferState = null;
    long prevBytesTransferred = 0;
    boolean updatedTextDownload = false;
    boolean updatedTextSuccess = false;
    final char[] bars = {'|', '/', '—', '\\'};

    public DownloadWrapper(String savePath, SearchResponse response, File file, Track track, CancellationTokenSource cts, ProgressBar progress) {
        this.savePath = savePath;
        this.response = response;
        this.file = file;
        this.cts = cts;
        this.track = track;
        this.progress = progress;
        this.displayText = Printing.DisplayString(track, file, response);

        Printing.RefreshOrPrint(progress, 0, "Initialize: " + displayText, true);
        Printing.RefreshOrPrint(progress, 0, displayText, false);
    }

    public void UpdateText() {
        float percentage = bytesTransferred / (float) file.Size;
        queued = (transfer.State & TransferStates.Queued) != 0;
        String bar;
        String state;
        boolean downloading = false;

        if (stalled) {
            state = "Stalled";
            bar = "  ";
        } else if (transfer != null) {
            if (queued) {
                if ((transfer.State & TransferStates.Remotely) != 0)
                    state = "Queued (R)";
                else
                    state = "Queued (L)";
                bar = "  ";
            } else if ((transfer.State & TransferStates.Initializing) != 0) {
                state = "Initialize";
                bar = "  ";
            } else if ((transfer.State & TransferStates.Completed) != 0) {
                TransferStates flag = transfer.State & (TransferStates.Succeeded | TransferStates.Cancelled
                        | TransferStates.TimedOut | TransferStates.Errored | TransferStates.Rejected
                        | TransferStates.Aborted);
                state = flag.toString();

                if (flag == TransferStates.Succeeded)
                    success = true;

                bar = "";
            } else {
                state = transfer.State.toString();
                if ((transfer.State & TransferStates.InProgress) != 0) {
                    downloading = true;
                    barState = (barState + 1) % bars.length;
                    bar = bars[barState] + " ";
                } else {
                    bar = "  ";
                }
            }
        } else {
            state = "NullState";
            bar = "  ";
        }

        String txt = String.format("%s%s: %s", bar, state, displayText);
        boolean needSimplePrintUpdate = (downloading && !updatedTextDownload) || (success && !updatedTextSuccess);
        updatedTextDownload |= downloading;
        updatedTextSuccess |= success;

        System.out.println(txt);
        Printing.RefreshOrPrint(progress, (int) (percentage * 100), txt, needSimplePrintUpdate, needSimplePrintUpdate);
    }

    public Date UpdateLastChangeTime(boolean updateAllFromThisUser, boolean forceChanged) {
        boolean changed = prevTransferState != transfer.State || prevBytesTransferred != bytesTransferred;
        if (changed || forceChanged) {
            lastChangeTime = new Date();
            stalled = false;
            if (updateAllFromThisUser) {
                for (var entry : Program.downloads.entrySet()) {
                    DownloadWrapper dl = entry.getValue();
                    if (dl != this && dl.response.Username.equals(response.Username))
                        dl.UpdateLastChangeTime(false, true);
                }
            }
        }
        prevTransferState = transfer.State;
        prevBytesTransferred = bytesTransferred;
        return lastChangeTime;
    }
}
