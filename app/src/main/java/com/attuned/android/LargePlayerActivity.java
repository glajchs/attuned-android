package com.attuned.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import com.eqot.fontawesome.FontAwesome;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LargePlayerActivity extends AppCompatActivity {
    public Button pauseButton;
    public Button shuffleButton;
    public TextView titleText;
    public TextView artistText;
    public TextView albumText;
    public TextView trackText;
    public SeekBar seekBar;

    // To invoke the bound service, first make sure that this value
    // is not null.
    private AttunedMusicPlayerService attunedMusicPlayerService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.

            attunedMusicPlayerService = ((AttunedMusicPlayerService.LocalBinder)service).getService();
            attunedMusicPlayerService.initializeLargePlayerActivity(LargePlayerActivity.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            attunedMusicPlayerService = null;
        }
    };
    private boolean mShouldUnbind;
    private ScheduledFuture<?> seekScheduledFuture;
    private ScheduledExecutorService service;

    void doBindService() {
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        Intent serviceIntent = new Intent(LargePlayerActivity.this, AttunedMusicPlayerService.class);
        startService(serviceIntent);
        if (bindService(serviceIntent,
                        mConnection, Context.BIND_IMPORTANT)) {
            mShouldUnbind = true;
        } else {
            Log.e("MY_APP_TAG", "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    void doUnbindService() {
        if (mShouldUnbind) {
            // Release information about the service's state.
            unbindService(mConnection);
            mShouldUnbind = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        doBindService();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.attuned.android.R.layout.activity_large_player);
        FontAwesome.applyToAllViews(this, findViewById(R.id.shuffleButtonLarge));
        FontAwesome.applyToAllViews(this, findViewById(R.id.playPauseButtonLarge));
        FontAwesome.applyToAllViews(this, findViewById(R.id.previousButtonLarge));
        FontAwesome.applyToAllViews(this, findViewById(R.id.nextButtonLarge));
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to read the contacts
            }

            int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1445123125;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);

            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant that should be quite unique

            return;
        }
        titleText = findViewById(com.attuned.android.R.id.titleText);
        artistText = findViewById(com.attuned.android.R.id.artistText);
        albumText = findViewById(com.attuned.android.R.id.albumText);
        trackText = findViewById(com.attuned.android.R.id.trackText);
        seekBar = findViewById(com.attuned.android.R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("onProgressChanged", String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                destroySeekListener();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                attunedMusicPlayerService.seekTo((long) seekBar.getProgress(), true);
                setupSeekListener();
            }
        });

        shuffleButton = findViewById(com.attuned.android.R.id.shuffleButtonLarge);
        shuffleButton.setOnClickListener(new OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                attunedMusicPlayerService.toggleShuffle();
            }
        });
        shuffleButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                attunedMusicPlayerService.reShuffleIndicies();
                return true;
            }
        });
        pauseButton = findViewById(R.id.playPauseButtonLarge);
        pauseButton.setOnClickListener(new OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                boolean isPlaying = attunedMusicPlayerService.togglePlayPause();
                if (isPlaying) {
                    setupSeekListener();
                } else {
                    destroySeekListener();
                }
            }
        });
        Button previousButton = findViewById(com.attuned.android.R.id.previousButtonLarge);
        previousButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                destroySeekListener();
                attunedMusicPlayerService.playPreviousSong();
                setupSeekListener();
            }
        });
        Button nextButton = findViewById(com.attuned.android.R.id.nextButtonLarge);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                destroySeekListener();
                attunedMusicPlayerService.playNextSong();
                setupSeekListener();
            }
        });
    }

    private void destroySeekListener() {
        if (seekScheduledFuture != null) {
            seekScheduledFuture.cancel(true);
        }
    }

    private void setupSeekListener() {
        if (service == null) {
            service = Executors.newScheduledThreadPool(1);
        }
        destroySeekListener();
        seekScheduledFuture = service.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                seekBar.setProgress(attunedMusicPlayerService.getCurrentSeek());
            }
        }, 250, 250, TimeUnit.MILLISECONDS);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(com.attuned.android.R.menu.large_player, menu);
        return true;
    }

}
