/*
 * This software is available under Apache License
 * Copyright (c) 2020
 */

package org.pixel.demo.learning.audio;

import org.pixel.audio.AudioPlayer;
import org.pixel.commons.ServiceProvider;
import org.pixel.content.ContentManager;
import org.pixel.content.Sound;
import org.pixel.demo.learning.common.DemoGame;
import org.pixel.core.WindowSettings;

public class AudioDemo extends DemoGame {

    protected Sound sound;
    protected ContentManager contentManager;
    protected AudioPlayer audioPlayer;

    public AudioDemo(WindowSettings settings) {
        super(settings);
    }

    @Override
    public void load() {
        // general game instances
        contentManager = ServiceProvider.create(ContentManager.class);
        audioPlayer = ServiceProvider.create(AudioPlayer.class);

        // load the audio source into memory
        sound = contentManager.load("audio/sfx_step_grass.ogg", Sound.class);

        // play the sound continuously
        audioPlayer.play(sound, true);
    }

    @Override
    public void dispose() {
        contentManager.dispose();
        sound.dispose();
        super.dispose();
    }

    public static void main(String[] args) {
        var settings = new WindowSettings(600, 480);
        settings.setTitle("Volume up! Audio is playing :)");
        settings.setWindowResizable(false);
        settings.setMultisampling(2);
        settings.setVsync(true);
        settings.setDebugMode(true);

        var window = new AudioDemo(settings);
        window.start();
    }
}
