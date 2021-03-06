package com.motondon.imagedownloader_intentservice;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.motondon.imagedownloader_intentservice.service.DownloadResultReceiver;
import com.motondon.imagedownloader_intentservice.service.ImageDownloaderIntentService;

public class MainFragment extends Fragment implements DownloadResultReceiver.Receiver {

    public static final String TAG = MainFragment.class.getSimpleName();

    public static final String DOWNLOAD_URI = "DOWNLOAD_URI";
    public static final String CURRENT_FILE_NAME = "CURRENT_FILE_NAME";
    public static final String TASK_STARTED = "TASK_STARTED"; // Used by LocalBroadcastManager
    public static final int TASK_FINISHED = 555; // Used by the ResultReceiver
    public static final String RECEIVER = "RECEIVER";
    public static final String DOWNLOADED_IMAGE = "DOWNLOADED_IMAGE";

    // Although this link is defined here, we will use intents in order for the service to inform this fragment the file name for the image
    // being downloaded. This is just to demonstrate how to communicate between a service and a fragment.
    private String downloadUrl = "http://eskipaper.com/images/large-2.jpg";

    private Activity mActivity;

    private Button btnDownload;
    private ImageView imageView;
    private ProgressDialog mProgressDialog;

    private String currentFileName;

    // Used to communication between ImageDownloaderService and this Fragment.
    private LocalBroadcastManager mLocalBroadcastManager;

    // This is the receiver which will receive intents sent by the ImageDownloaderService. It is registered in onCreate and unregistered in onDestroy
    private final BroadcastReceiver mBroadcastReceiver  = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // When ImageDownloaderService starts a download, it will send this action with the file (name) being downloaded.
            if (intent.getAction().equals(TASK_STARTED)) {
                String fileName = intent.getExtras().getString(CURRENT_FILE_NAME);
                Log.d(TAG, "BroadcastReceiver::mBroadcastReceiver - Received a TASK_STARTED action for fileName " + fileName + ". Calling taskStarted() method...");
                taskStarted(fileName);
            }
        }
    };

    // For the demonstration purpose, when a download is finished, we will use another approach for the communication between the ImageDownloaderIntentService
    // and the main UI thread: ResultReceiver.
    // Basically, when the "Download Image" button is clicked, we will pass it as an argument to the ImageDownloaderIntentService. Then, when the download is
    // finished, this receiver will be used to send back the image to this fragment.
    private DownloadResultReceiver mResultReceiver;

    /**
     * Store a reference to the Activity in order to recreate ProgressDialog after a configuration change.
     *
     * @param context
     */
    @Override
    public void onAttach(Context context) {
        Log.v(TAG, "onAttach()");
        super.onAttach(context);
        this.mActivity = (Activity) context;
    }

    /**
     * Nullify Activity reference in order to avoid memory leak.
     */
    @Override
    public void onDetach() {
        Log.v(TAG, "onDetach()");
        super.onDetach();
        this.mActivity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate() - savedInstanceState: " + savedInstanceState);

        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentFileName = savedInstanceState.getString(CURRENT_FILE_NAME);

            // Restore ResultReceiver here after an orientation change.
            mResultReceiver = savedInstanceState.getParcelable(RECEIVER);
        }

        // If "currentFileName" contains any value, it means a download is being processed. So, after an orientation change under this
        // circumstance, show ProgressDialog again.
        if (currentFileName != null && !currentFileName.isEmpty()) {
            showProgressDialog(currentFileName);
        }

        // Lines below will get an instance of LocalBroadcastManager, create an intentFilter, add TASK_STARTED action and register the
        // BroadcastReceiver. This intent will be sent by the ImageDownloaderIntentService prior to start a download.
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(mActivity);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(TASK_STARTED);
        mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, mIntentFilter);

        // We will use a ResultReceiver for the ImageDownloaderIntentService to inform that a download has finished. So, initialize it
        // here.
        if (mResultReceiver == null) {
            mResultReceiver = new DownloadResultReceiver(new Handler());
        }

        // No matter whether ResultReceiver has just being created or it was recovered from the savedInstance, we need to set its receiver
        // to "this" (since "this" will be a new instance after an orientation change). Then, if during a download an orientation
        // change occurs, we will be able to handle the taskFinished event accordingly.
        mResultReceiver.setReceiver(this);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy()");

        super.onDestroy();

        // Close ProgressDialog (it is being showed), since it will be recreated after this fragment be reconstructed again (on
        // orientation change)
        dismissProgressDialog();

        // Do not forget to unregister the broadcastReceiver when destroying this activity.
        mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView()");

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        btnDownload = (Button) rootView.findViewById(R.id.btnDownloadImage);
        imageView = (ImageView) rootView.findViewById(R.id.imgView);

        // After a successful download, in case of an orientation change, the image will be saved in the bundle
        // (by the onSaveInstanceState method). So, retrieve it here and load it on the ImageView.
        if (savedInstanceState != null) {
            Bitmap bitmap = savedInstanceState.getParcelable(DOWNLOADED_IMAGE);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadImageClick(v);
            }
        });
        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.v(TAG, "onSaveInstanceState() = currentFileName: " + currentFileName);

        super.onSaveInstanceState(outState);
        outState.putString(CURRENT_FILE_NAME, currentFileName);

        // Save ResultReceiver instance during an orientation change, since there might be a download in progress.
        // Later, when recreating this fragment (onCreate method), we can get it back and set its receiver
        // by using the new MainFragment instance.
        outState.putParcelable(RECEIVER, mResultReceiver);

        // After a successful download, in case of an orientation change, store the image in the bundle so that it
        // can be restored later.
        Drawable downloadedImage = imageView.getDrawable();
        if (downloadedImage != null) {
            BitmapDrawable bitmapDrawable = ((BitmapDrawable) downloadedImage);
            Bitmap bitmap = bitmapDrawable .getBitmap();

            outState.putParcelable(DOWNLOADED_IMAGE, bitmap);
        }
    }

    private void onDownloadImageClick(View v) {
        Log.v(TAG, "onDownloadImageClick()");

        // First clear the image in the ImageView (if have one)
        imageView.setImageDrawable(null);

        Intent i = new Intent(mActivity, ImageDownloaderIntentService.class);

        // Add the URL to be downloaded
        i.putExtra(DOWNLOAD_URI, downloadUrl);

        // Used to send back the image after download finishes.
        i.putExtra(RECEIVER, mResultReceiver);

        // Now call a method which will start the asyncTask task.
        mActivity.startService(i);
    }

    /**
     * Prior to start a download, ImageDownloadService will send an intent (via localBroadcastManager) and this method will
     * be called. So, update the file (name) being downloaded
     *
     * @param file
     */
    private void taskStarted(final String file) {
        Log.v(TAG, "taskStarted()");

        currentFileName = file;
        showProgressDialog(file);
    }

    /**
     * After finishes a download, ImageDownloadService will use a ResultReceiver object (that was passed as an argument when
     * requesting a download) to send back the image just download. This will be handled by the DownloadResultReceiver class which implements
     * ResultReceiver. Since this fragment implements DownloadResultReceiver::Receiver interface, it will receive that
     * response in the onReceiveResult() which will call this method.
     *
     * @param bitmap
     */
    private void taskFinished(Bitmap bitmap) {
        Log.v(TAG, "taskFinished()");

        dismissProgressDialog();

        // Do not forget to nullify currentFileName, otherwise, after a finished download, if user change orientation,
        // progressDialog will be showed forever
        currentFileName = null;

        if(bitmap != null){
            imageView.setImageBitmap(bitmap);

        } else {
            Toast.makeText(mActivity, "Image Does Not exist or Network Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgressDialog(String file) {
        Log.v(TAG, "showProgressDialog()");

        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setTitle("Download Image");
        mProgressDialog.setMessage("Loading " + file + " file...");
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.show();
    }

    private void dismissProgressDialog() {
        Log.v(TAG, "dismissProgressDialog()");

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    /**
     * This is the method called by the DownloadResultReceiver when a download is finished.
     * @param resultCode
     * @param resultData
     */
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode) {

            case TASK_FINISHED:
                Bitmap bitmap = resultData.getParcelable(DOWNLOADED_IMAGE);
                Log.d(TAG, "onReceiveResult - Received a TASK_FINISHED action. Calling taskStarted() method...");
                taskFinished(bitmap);
                break;
        }
    }
}
