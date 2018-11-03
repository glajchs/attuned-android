package com.attuned.android;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static android.provider.BaseColumns._ID;
import static android.provider.MediaStore.Audio.AudioColumns.*;
import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
import static android.provider.MediaStore.MediaColumns.TITLE;

public class AttunedMusicPlayerService extends Service {

    protected MediaPlayer mediaPlayer;
    protected ArrayList<Song> allSongs = new ArrayList<>();
    protected ArrayList<Integer> shuffledIndecies = new ArrayList<>();
    protected boolean isPrepared = false;
    protected boolean isErrored = false;
    protected boolean isShuffled = false;
    protected int currentlyPlayingTrackNumber = -1;
    private final IBinder binder = new LocalBinder();
    private LargePlayerActivity largePlayerActivity;

    public void setLargePlayerActivity(LargePlayerActivity largePlayerActivity) {
        this.largePlayerActivity = largePlayerActivity;
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
        initializeMusicLibrary();
        createMusicPlayer();
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
            shuffledIndecies.add(i);
        }
        Collections.shuffle(shuffledIndecies);
        Log.w("Attuned", "done looping");
    }

    private void createMusicPlayer() {
        mediaPlayer = new MediaPlayer();
        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build();
        mediaPlayer.setAudioAttributes(audioAttributes );
        mediaPlayer.setOnPreparedListener(new OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isPrepared = true;
            }
        });
        mediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                isErrored = true;
                return false;
            }
        });
        mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playNextSong();
            }
        });
    }

    protected void togglePlayPause() {
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                largePlayerActivity.pauseButton.setImageResource(com.attuned.android.R.drawable.play_48);
            } else if (!isPrepared) {
                int indexOfCurrentSongToPlay = getCurrentSongIndexToPlay();
                Uri songUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"), allSongs.get(indexOfCurrentSongToPlay).songID);
                mediaPlayer.setDataSource(getApplicationContext(), songUri);
                mediaPlayer.prepare();
                playSong(indexOfCurrentSongToPlay);
                largePlayerActivity.pauseButton.setImageResource(com.attuned.android.R.drawable.pause_48);
            } else {
                mediaPlayer.start();
                largePlayerActivity.pauseButton.setImageResource(com.attuned.android.R.drawable.pause_48);
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
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected void toggleShuffle() {
        isShuffled = !isShuffled;
    }

    protected void reShuffleIndicies() {
        shuffledIndecies = new ArrayList<Integer>();
        for (int i = 0; i < allSongs.size(); i++) {
            shuffledIndecies.add(i);
        }
        Collections.shuffle(shuffledIndecies);
        isShuffled = true;
    }

    protected void playPreviousSong() {
        int previousSongIndex = getPreviousSongIndex();
        Uri songUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"),
                        allSongs.get(previousSongIndex).songID);
        playNewSong(songUri, previousSongIndex);
    }

    protected void playSong(int songIndexToPlay) {
        currentlyPlayingTrackNumber = songIndexToPlay;
        mediaPlayer.start();
        largePlayerActivity.titleText.setText(allSongs.get(songIndexToPlay).title);
        largePlayerActivity.artistText.setText(allSongs.get(songIndexToPlay).artist);
        largePlayerActivity.albumText.setText(allSongs.get(songIndexToPlay).album);
        int trackNumber = allSongs.get(songIndexToPlay).track;
        if (trackNumber != -1) {
            largePlayerActivity.trackText.setText(String.valueOf(allSongs.get(songIndexToPlay).track) + " - ");
        } else {
            largePlayerActivity.trackText.setText("");
        }
    }

    protected void playNextSong() {
        int nextSongIndex = getNextSongIndex();
        Uri songUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"),
                        allSongs.get(nextSongIndex).songID);
        playNewSong(songUri, nextSongIndex);
    }

    protected void playNewSong(Uri songUri, int songIndex) {
        try {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
            playSong(songIndex);
            largePlayerActivity.pauseButton.setImageResource(com.attuned.android.R.drawable.pause_48);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected int getNextSongIndex() {
        if (currentlyPlayingTrackNumber == -1) {
            return getFirstSongIndex();
        } else if (isShuffled) {
            int shuffleSongIndex = shuffledIndecies.indexOf(currentlyPlayingTrackNumber);
            if (shuffleSongIndex == shuffledIndecies.size() - 1) {
                return shuffledIndecies.get(0);
            } else {
                return shuffledIndecies.get(shuffleSongIndex + 1);
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
            int shuffleSongIndex = shuffledIndecies.indexOf(currentlyPlayingTrackNumber);
            if (shuffleSongIndex == 0) {
                return shuffledIndecies.get(shuffledIndecies.size() - 1);
            } else {
                return shuffledIndecies.get(shuffleSongIndex - 1);
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
            return shuffledIndecies.get(0);
        } else {
            return 0;
        }
    }

}
