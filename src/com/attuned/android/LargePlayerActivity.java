package com.attuned.android;

import static android.provider.BaseColumns._ID;
import static android.provider.MediaStore.Audio.AudioColumns.ALBUM;
import static android.provider.MediaStore.Audio.AudioColumns.ARTIST;
import static android.provider.MediaStore.Audio.AudioColumns.IS_MUSIC;
import static android.provider.MediaStore.Audio.AudioColumns.TRACK;
import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
import static android.provider.MediaStore.MediaColumns.TITLE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

public class LargePlayerActivity extends Activity {
    public MediaPlayer mediaPlayer;
    boolean isPrepared = false;
    boolean isErrored = false;
    boolean isShuffled = false;
    int currentlyPlayingTrackNumber = -1;
    
    class Song {
        public String title;
        public String artist;
        public String album;
        public int track;
        public String songID;
        
        public Song(String songID, String title, String artist, String album, int track) {
            this.songID = songID;
            this.title = title;
            this.artist = artist;
            this.album = album;
            if (track >= 1000) {
                this.track = track - 1000;
            } else {
                this.track = track;
            }
        }
    }
    
    private ArrayList<Song> allSongs = new ArrayList<Song>();
    private ArrayList<Integer> shuffledIndecies = new ArrayList<Integer>();
    private ImageButton pauseButton;
    TextView titleText;
    TextView artistText;
    TextView albumText;
    TextView trackText;
    TextView songPositionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_large_player);
        Uri internalContentUri = EXTERNAL_CONTENT_URI;
        ContentResolver contentResolver = getContentResolver();
        Cursor allMediaCursor = contentResolver.query(internalContentUri, null, null, null, null);
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
                            int titleCompare = lhs.title.compareToIgnoreCase(rhs.title);
                            if (titleCompare == 0) {
                                return -1;
                            } else {
                                return titleCompare;
                            }
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

//        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
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
        titleText = (TextView) findViewById(R.id.titleText);
        artistText = (TextView) findViewById(R.id.artistText);
        albumText = (TextView) findViewById(R.id.albumText);
        trackText = (TextView) findViewById(R.id.trackText);
//        songPositionText = (TextView) findViewById(R.id.songPositionText);
        
        ImageButton shuffleButton = (ImageButton) findViewById(R.id.shuffleButtonLarge);
        shuffleButton.setOnClickListener(new OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                toggleShuffle();
            }
        });
        shuffleButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                reShuffleIndicies();
                return true;
            }
        });
        pauseButton = (ImageButton) findViewById(R.id.playPauseButtonLarge);
        pauseButton.setOnClickListener(new OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });
        ImageButton previousButton = (ImageButton) findViewById(R.id.previousButtonLarge);
        previousButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playPreviousSong();
            }
        });
        ImageButton nextButton = (ImageButton) findViewById(R.id.nextButtonLarge);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playNextSong();
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
                pauseButton.setImageResource(R.drawable.play_48);
            } else if (!isPrepared) {
                int indexOfCurrentSongToPlay = getCurrentSongIndexToPlay();
                Uri songUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"), allSongs.get(indexOfCurrentSongToPlay).songID);
                mediaPlayer.setDataSource(getApplicationContext(), songUri);
                mediaPlayer.prepare();
                playSong(indexOfCurrentSongToPlay);
                pauseButton.setImageResource(R.drawable.pause_48);
            } else {
                mediaPlayer.start();
                pauseButton.setImageResource(R.drawable.pause_48);
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
        try {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
            playSong(previousSongIndex);
            pauseButton.setImageResource(R.drawable.pause_48);
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

    private void playSong(int songIndexToPlay) {
        currentlyPlayingTrackNumber = songIndexToPlay;
        mediaPlayer.start();
        titleText.setText(allSongs.get(songIndexToPlay).title);
        artistText.setText(allSongs.get(songIndexToPlay).artist);
        albumText.setText(allSongs.get(songIndexToPlay).album);
        int trackNumber = allSongs.get(songIndexToPlay).track;
        if (trackNumber != -1) {
            trackText.setText(String.valueOf(allSongs.get(songIndexToPlay).track) + " - ");
        } else {
            trackText.setText("");
        }
    }

    protected void playNextSong() {
        int nextSongIndex = getNextSongIndex();
        Uri songUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"),
                        allSongs.get(nextSongIndex).songID);
        try {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
            playSong(nextSongIndex);
            pauseButton.setImageResource(R.drawable.pause_48);
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
            int shuffleSongIndex = shuffledIndecies.indexOf((Integer) currentlyPlayingTrackNumber);
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
            int shuffleSongIndex = shuffledIndecies.indexOf((Integer) currentlyPlayingTrackNumber);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.large_player, menu);
        return true;
    }

}