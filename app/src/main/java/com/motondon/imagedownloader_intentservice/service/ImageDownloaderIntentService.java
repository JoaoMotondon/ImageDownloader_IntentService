package com.motondon.imagedownloader_intentservice.service;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import com.motondon.imagedownloader_intentservice.MainFragment;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Since this is a demo project which intends to demonstrate how to use IntentService as well as how to communicate
 * between an IntentService and the Main Thread, it will use two different approaches to send messages back to the
 * main UI thread.
 *
 * The first one uses a LocalBroadcastManager to send TASK_STARTED message to the MainFragment. MainFragment implements
 * a BroadcastReceiver which will receive this message.
 *
 * The second approach uses ResultReceiver. When creating the intent to start a download, MainFragment adds to the
 * intent an instance of the DownloadResultReceiver (that extends ResultReceiver). When a download finishes, this class
 * extracts the DownloadResultReceiver instance from the intent and simply use its send() method which will be handled by
 * the DownloadResultReceiver::onReceiveResult() that will pass it on to the MainFragment::onReceiveResult() method.
 *
 * This second approach is a little more trick to understand than the first one which uses LocalBroadcastManager, but it
 * was used here only to demonstrate another way on how a worker thread can communicate to the main UI thread.
 *
 * */
public class ImageDownloaderIntentService extends IntentService {

    private static final String TAG = ImageDownloaderIntentService.class.getSimpleName();

    private LocalBroadcastManager mLocalBroadcastManager;

    public ImageDownloaderIntentService() {
        super(TAG);
        Log.i(TAG, "Service constructor");

        // Do not try to call getApplicationContext() in the constructor, since context for this object has not created yet, and it will
        // return null. If we do it, we will  get this exception:
        // Caused by: java.lang.NullPointerException: Attempt to invoke virtual method 'android.content.Context android.content.Context.getApplicationContext()' on a null object reference
        // We let this code commented here as an informative purpose
        // mLocalBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate()");
        super.onCreate();

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.v(TAG, "onHandleIntent() - Begin");

        Bundle data = intent.getExtras();
        String downloadUrl = data.getString(MainFragment.DOWNLOAD_URI);

        try {
            String fileName = Uri.parse(downloadUrl).getLastPathSegment();

            // Use LocalBroadcastManager to send a message to the MainFragment class
            Log.d(TAG, "onHandleIntent() - Sending to the  " + MainFragment.class.getSimpleName() + " TASK_STARTED action so that it can have a chance to update its GUI with the fileName which is being downloaded.");
            Intent i = new Intent(MainFragment.TASK_STARTED);
            i.putExtra(MainFragment.CURRENT_FILE_NAME, fileName);
            mLocalBroadcastManager.sendBroadcast(i);

            Log.v(TAG, "onHandleIntent() - Downloading image...");
            Bitmap bitmap = downloadBitmap(downloadUrl);
            Log.v(TAG, "onHandleIntent() - Download finished");

            // Use ResultReceiver that was passed inside the intent extras in order to send a message back to the MainFragment class
            Log.d(TAG, "taskFinished() - Sending to the  " + MainFragment.class.getSimpleName() + " TASK_FINISHED action in order for it to update its imageFrame with image just downloaded.");
            final ResultReceiver receiver = data.getParcelable(MainFragment.RECEIVER);
            Bundle bundle = new Bundle();
            bundle.putParcelable(MainFragment.DOWNLOADED_IMAGE, bitmap);
            receiver.send(MainFragment.TASK_FINISHED, bundle);

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "onHandleIntent() - Exception while trying to download image from url: " + downloadUrl + ". Message: " + e.getMessage());
        }

        Log.v(TAG, "onHandleIntent() - End");
    }

    /**
     * Download image here
     *
     * @param strUrl
     * @return
     * @throws IOException
     */
    private Bitmap downloadBitmap(String strUrl) throws IOException {
        Bitmap bitmap=null;
        InputStream iStream = null;
        try{
            URL url = new URL(strUrl);
            /** Creating an http connection to communicate with url */
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            /** Connecting to url */
            urlConnection.connect();

            /** Reading data from url */
            iStream = urlConnection.getInputStream();

            /** Creating a bitmap from the stream returned from the url */
            bitmap = BitmapFactory.decodeStream(iStream);

        }catch(Exception e){
            Log.d(TAG, "Exception while downloading url: " + strUrl + ". Error: " + e.toString());
        }finally{
            if (iStream != null) {
                iStream.close();
            }
        }
        return bitmap;
    }
}
