package com.johnny.weardatalayertest;

import android.content.IntentSender;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

// http://stackoverflow.com/questions/8098806/where-do-i-create-and-use-scheduledthreadpoolexecutor-timertask-or-handler/8102488#8102488

public class MainActivity extends ActionBarActivity implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {

    private static final String TAG = "PhoneMainActivity";
    private TextView counter_lb;
    private Button start_btn;
    private Button stop_btn;

    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private boolean mResolvingError = false;
    private GoogleApiClient mGoogleApiClient;

    private Timer mTimer;
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        counter_lb = (TextView) findViewById(R.id.counter_lb);

        start_btn = (Button) findViewById(R.id.start_btn);
        start_btn.setOnClickListener(this);
        stop_btn = (Button) findViewById(R.id.stop_btn);
        stop_btn.setOnClickListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

    }


    @Override
    protected void onDestroy() {
        stopTimer();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_reset) {
            stopTimer();
            count = 0;
            counter_lb.setText(String.valueOf(count));
            syncCount(count);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (!mResolvingError) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override //ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "Google API Client was connected");
        mResolvingError = false;
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection to Google API client was suspended");
    }

    @Override //OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            Log.e(TAG, "Connection to Google API client has failed");
            mResolvingError = false;
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
        }
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }

        return results;
    }


    @Override
    public void onClick(View v) {
        if (v == start_btn) {
            startTimer();
        } else if (v == stop_btn) {
            stopTimer();
        }
    }

    private void startTimer() {
        if (mTimer != null) {
            stopTimer();
            Log.w(TAG, "ReSchedule the Timer.");
        }
        mTimer = new Timer();
        mTimer.schedule(new MyTimerTask(), 0, 1000);
        Log.w(TAG, "Timer Started.");
    }

    private void stopTimer() {
        if (mTimer != null) {
            Log.w(TAG, "Timer Stopped.");
            mTimer.cancel();
            mTimer = null;
        }
    }

    class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            count++;
            Log.v(TAG, "Tick " + count);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    counter_lb.setText(String.valueOf(count));
                    syncCount(count);
                }
            });
        }
    }

    private void syncCount(int count) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/Count");
        putDataMapRequest.getDataMap().putInt("COUNT", count);
        PutDataRequest request = putDataMapRequest.asPutDataRequest();

        Log.d(TAG, "Generating DataItem: " + request.toString(true));
        if (!mGoogleApiClient.isConnected()) {
            return;
        }
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.e(TAG, "ERROR: failed to putDataItem, status code: "
                                    + dataItemResult.getStatus().getStatusCode());
                        }
                    }
                });
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (DataEvent event : events) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {

                        String path = event.getDataItem().getUri().getPath();
                        DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                        if (path.equals("/Count")) {
                            Log.v("myTag", "DataMap received on watch: " + dataMap);
                            int count = dataMap.getInt("COUNT");
                            Log.v(TAG, "Count value = " + count);
                        } else if (path.equals("/GetCount")) {
                            syncCount(count);
                        }
                    } else if (event.getType() == DataEvent.TYPE_DELETED) {

                    }
                }
            }
        });
    }
}
