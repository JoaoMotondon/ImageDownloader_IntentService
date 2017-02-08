package com.motondon.imagedownloader_intentservice.service;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.os.ResultReceiver;

/**
 * This class is used by the MainFragment when it requests an image download. It then pass an instance of it via bundle to the
 * ImageDownloaderIntentService. Then, when a download is finished, ImageDownloaderIntentService will use it to communicate
 * with the MainFragment by sending back the image just downloaded.
 *
 */
public class DownloadResultReceiver extends ResultReceiver {

    // Implemented by the MainFragment so that it can receive events about the images being donwloaded
    public interface Receiver {
        void onReceiveResult(int resultCode, Bundle resultData);
    }

    private Receiver mReceiver;

    /**
     * Create a new ResultReceive to receive results.  Your
     * {@link #onReceiveResult} method will be called from the thread running
     * <var>handler</var> if given, or from an arbitrary thread if null.
     *
     * @param handler
     */
    public DownloadResultReceiver(Handler handler) {
        super(handler);
    }

    public void setReceiver(Receiver receiver) {
        this.mReceiver = receiver;
    }

    /**
     * This method is called when ImageDownloaderIntentService calls ResultReceiver::send() method. Then it will
     * call receiver (i.e. MainFragment) to inform about the download. The image is wrapped in the resultData parameter.
     *
     * @param resultCode
     * @param resultData
     */
    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (mReceiver != null) {
            mReceiver.onReceiveResult(resultCode, resultData);
        }
    }
}
