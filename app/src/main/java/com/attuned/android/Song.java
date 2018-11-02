/*
 * Copyright (C) 2010-2013 Evergage, Inc.
 * All rights reserved.
 */

package com.attuned.android;

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
