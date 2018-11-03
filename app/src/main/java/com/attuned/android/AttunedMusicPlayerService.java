package com.attuned.android;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Build.VERSION;
import android.os.IBinder;
import android.util.Log;
import com.eqot.fontawesome.FontAwesome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static android.provider.BaseColumns._ID;
import static android.provider.MediaStore.Audio.AudioColumns.*;
import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
import static android.provider.MediaStore.MediaColumns.TITLE;

public class AttunedMusicPlayerService extends Service {

    public static final String APPLICATION_STATE_IS_SHUFFLED = "isShuffled";
    public static final String APPLICATION_STATE_SEEK_TIME = "seekTime";
    public static final String APPLICATION_STATE_CURRENTLY_PLAYING_TRACK_NUMBER = "currentlyPlayingTrackNumber";
    protected MediaPlayer mediaPlayer;
    protected ArrayList<Song> allSongs = new ArrayList<>();
    protected ArrayList<Integer> shuffledIndexes = new ArrayList<>();
    private final IBinder binder = new LocalBinder();
    private LargePlayerActivity largePlayerActivity;

    protected boolean isShuffled = false;
    protected int currentlyPlayingTrackNumber = -1;

    private String pauseButtonImageResource;
    private String titleText;
    private String artistText;
    private String albumText;
    private String trackText;
    private SharedPreferences applicationState;

    public void initializeLargePlayerActivity(LargePlayerActivity largePlayerActivity) {
        this.largePlayerActivity = largePlayerActivity;

        renderLargePlayerActivityFromSong();
    }

    private void renderLargePlayerActivityFromSong() {
        Song playingSong = allSongs.get(currentlyPlayingTrackNumber);
        titleText = playingSong.title;
        largePlayerActivity.titleText.setText(titleText);
        artistText = playingSong.artist;
        largePlayerActivity.artistText.setText(artistText);
        albumText = playingSong.album;
        largePlayerActivity.albumText.setText(albumText);
        int trackNumber = playingSong.track;
        if (trackNumber != -1) {
            trackText = String.valueOf(playingSong.track) + " - ";
        } else {
            trackText = "";
        }
        largePlayerActivity.trackText.setText(trackText);
        setPauseButtonState();
        setShuffleButtonColor();
        setupSeekBar();
    }

    private void setPauseButtonState() {
        pauseButtonImageResource = mediaPlayer.isPlaying() ? "{fa-pause}" : "{fa-play}";
        largePlayerActivity.pauseButton.setText(pauseButtonImageResource);
        FontAwesome.applyToAllViews(getApplicationContext(), largePlayerActivity.pauseButton);
    }

    private void setShuffleButtonColor() {
        int shuffleColor = isShuffled ? R.color.colorEnabledButton : R.color.colorDisabledButton;
        largePlayerActivity.shuffleButton.setTextColor(getResources().getColor(shuffleColor, null));
    }

    public int getCurrentSeek() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            return Long.valueOf(mediaPlayer.getTimestamp().getAnchorMediaTimeUs() / 1000l).intValue();
        } else {
            return 0;
        }
    }

    // Class used for the client Binder.
    public class LocalBinder extends Binder {
        AttunedMusicPlayerService getService() {
            // Return this instance of MyService so clients can call public methods
            return AttunedMusicPlayerService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        applicationState = getApplicationContext().getSharedPreferences("ApplicationState", Context.MODE_PRIVATE);
        isShuffled = applicationState.getBoolean(APPLICATION_STATE_IS_SHUFFLED, false);
        initializeMusicLibrary();
        createMusicPlayer();
    }

    // .apply() doesn't wait for it to finish, but since this is being destroyed, in order to properly store the value,
    // we have to wait, which means using .commit().
    @SuppressLint("ApplySharedPref")
    private void safeOffSeekTime() {
        if (applicationState != null && mediaPlayer.isPlaying()) {
            Editor applicationStateEditor = applicationState.edit();
            applicationStateEditor.putLong(APPLICATION_STATE_SEEK_TIME, mediaPlayer.getTimestamp().getAnchorMediaTimeUs() / 1000l);
            applicationStateEditor.putBoolean(APPLICATION_STATE_IS_SHUFFLED, isShuffled);
            applicationStateEditor.commit();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        safeOffSeekTime();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        safeOffSeekTime();
        super.onDestroy();
    }

    private void initializeMusicLibrary() {
        ContentResolver contentResolver = getContentResolver();
        Cursor allMediaCursor = contentResolver.query(EXTERNAL_CONTENT_URI, null, null, null, null);
        if (allMediaCursor == null) {
            // query failed, handle error.
        } else if (!allMediaCursor.moveToFirst()) {
            // no media on the device
        } else {
            int idColumn = allMediaCursor.getColumnIndex(_ID);
            int titleColumn = allMediaCursor.getColumnIndex(TITLE);
            int artistColumn = allMediaCursor.getColumnIndex(ARTIST);
            int albumColumn = allMediaCursor.getColumnIndex(ALBUM);
            int trackColumn = allMediaCursor.getColumnIndex(TRACK);
            int isMusicColumn = allMediaCursor.getColumnIndex(IS_MUSIC);
            do {
                if (allMediaCursor.getInt(isMusicColumn) != 1) {
                    continue;
                }
                allSongs.add(new Song(allMediaCursor.getString(idColumn),
                                      allMediaCursor.getString(titleColumn),
                                      allMediaCursor.getString(artistColumn),
                                      allMediaCursor.getString(albumColumn),
                                      allMediaCursor.getInt(trackColumn)));
            } while (allMediaCursor.moveToNext());
        }
        Collections.sort(allSongs, new Comparator<Song>() {
            @Override
            public int compare(Song lhs, Song rhs) {
                int artistCompare = lhs.artist.compareToIgnoreCase(rhs.artist);
                if (artistCompare == 0) {
                    int albumCompare = lhs.album.compareToIgnoreCase(rhs.album);
                    if (albumCompare == 0) {
                        if (lhs.track > rhs.track) {
                            return 1;
                        } else if (lhs.track < rhs.track) {
                            return -1;
                        } else {
                            return lhs.title.compareToIgnoreCase(rhs.title);
                        }
                    } else {
                        return albumCompare;
                    }
                } else {
                    return artistCompare;
                }
            }
        });
        for (int i = 0; i < allSongs.size(); i++) {
            shuffledIndexes.add(i);
        }
        Collections.shuffle(shuffledIndexes);
        Log.w("Attuned", "done looping");
    }

    private void createMusicPlayer() {
        mediaPlayer = new MediaPlayer();
        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build();
        mediaPlayer.setAudioAttributes(audioAttributes);
        // TODO settings-ize this
        mediaPlayer.setScreenOnWhilePlaying(true);
        mediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("MediaPlayer", "error setting up the media player");
                return false;
            }
        });
        mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playNextSong();
            }
        });
        currentlyPlayingTrackNumber = applicationState.getInt(APPLICATION_STATE_CURRENTLY_PLAYING_TRACK_NUMBER, getCurrentSongIndexToPlay());
        setupSong(false);
    }

    private void setupSong(boolean isUserInitiated) {
        Uri songUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"), allSongs.get(currentlyPlayingTrackNumber).songID);
        mediaPlayer.stop();
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!isUserInitiated) {
            Long seekTimeToSet = applicationState.getLong(APPLICATION_STATE_SEEK_TIME, 0l);
            if (seekTimeToSet != 0l) {
                seekTo(seekTimeToSet, false);
            }
        }
        setupSeekBar();
    }

    public void seekTo(Long seekTimeToSet, boolean userDrivenChange) {
        if (VERSION.SDK_INT >= 26) {
            mediaPlayer.seekTo(seekTimeToSet, MediaPlayer.SEEK_PREVIOUS_SYNC);
            if (userDrivenChange) {
                Editor applicationStateEditor = applicationState.edit();
                applicationStateEditor.putLong(APPLICATION_STATE_SEEK_TIME, seekTimeToSet);
                applicationStateEditor.apply();
            }
        }
    }

    private void setupSeekBar() {
        if (largePlayerActivity != null) {
            largePlayerActivity.seekBar.setMax(mediaPlayer.getDuration());
            largePlayerActivity.seekBar.setProgress((int) (mediaPlayer.getTimestamp().getAnchorMediaTimeUs() / 1000));
        }
    }

    protected boolean togglePlayPause() {
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
//                pauseButtonImageResource = R.drawable.play_48;
//                largePlayerActivity.pauseButton.setImageResource(pauseButtonImageResource);
                setPauseButtonState();
                Editor applicationStateEditor = applicationState.edit();
                applicationStateEditor.putLong(APPLICATION_STATE_SEEK_TIME, mediaPlayer.getTimestamp().getAnchorMediaTimeUs() / 1000);
                applicationStateEditor.apply();
                return false;
            } else {
                mediaPlayer.start();
//                pauseButtonImageResource = R.drawable.pause_48;
//                largePlayerActivity.pauseButton.setImageResource(pauseButtonImageResource);
                setPauseButtonState();
                return true;
            }
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    protected void toggleShuffle() {
        isShuffled = !isShuffled;
        Editor applicationStateEditor = applicationState.edit();
        applicationStateEditor.putBoolean(APPLICATION_STATE_IS_SHUFFLED, isShuffled);
        applicationStateEditor.apply();
        setShuffleButtonColor();
    }

    protected void reShuffleIndicies() {
        shuffledIndexes = new ArrayList<Integer>();
        for (int i = 0; i < allSongs.size(); i++) {
            shuffledIndexes.add(i);
        }
        Collections.shuffle(shuffledIndexes);
        isShuffled = true;
    }

    protected void playPreviousSong() {
        currentlyPlayingTrackNumber = getPreviousSongIndex();
        setupSong(true);
        playNewSong(currentlyPlayingTrackNumber);
    }

    protected void playNewSong(int songIndexToPlay) {
        currentlyPlayingTrackNumber = songIndexToPlay;
        Editor applicationStateEditor = applicationState.edit();
        applicationStateEditor.putInt(APPLICATION_STATE_CURRENTLY_PLAYING_TRACK_NUMBER, currentlyPlayingTrackNumber);
        applicationStateEditor.apply();
        mediaPlayer.start();
        renderLargePlayerActivityFromSong();
    }

    protected void playNextSong() {
        currentlyPlayingTrackNumber = getNextSongIndex();
        setupSong(true);
        playNewSong(currentlyPlayingTrackNumber);
    }

    protected int getNextSongIndex() {
        if (currentlyPlayingTrackNumber == -1) {
            return getFirstSongIndex();
        } else if (isShuffled) {
            int shuffleSongIndex = shuffledIndexes.indexOf(currentlyPlayingTrackNumber);
            if (shuffleSongIndex == shuffledIndexes.size() - 1) {
                return shuffledIndexes.get(0);
            } else {
                return shuffledIndexes.get(shuffleSongIndex + 1);
            }
        } else {
            if (currentlyPlayingTrackNumber == allSongs.size() - 1) {
                return 0;
            } else {
                return currentlyPlayingTrackNumber + 1;
            }
        }
    }

    protected int getPreviousSongIndex() {
        if (currentlyPlayingTrackNumber == -1) {
            return getFirstSongIndex();
        } else if (isShuffled) {
            int shuffleSongIndex = shuffledIndexes.indexOf(currentlyPlayingTrackNumber);
            if (shuffleSongIndex == 0) {
                return shuffledIndexes.get(shuffledIndexes.size() - 1);
            } else {
                return shuffledIndexes.get(shuffleSongIndex - 1);
            }
        } else {
            if (currentlyPlayingTrackNumber == 0) {
                return allSongs.size() - 1;
            } else {
                return currentlyPlayingTrackNumber - 1;
            }
        }
    }

    protected int getCurrentSongIndexToPlay() {
        if (currentlyPlayingTrackNumber >= 0) {
            return currentlyPlayingTrackNumber;
        } else {
            return getFirstSongIndex();
        }
    }

    protected int getFirstSongIndex() {
        if (isShuffled) {
            return shuffledIndexes.get(0);
        } else {
            return 0;
        }
    }

}
