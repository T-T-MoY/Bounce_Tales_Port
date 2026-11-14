package com.bouncetales.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.bouncetales.world.script.TriggerVM;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Carga sonidos y musica bajo demanda y permite ejecutar el juego sin el paquete de audio.
 *
 * <p>Los efectos viven en {@code assets/sounds/<id>.ogg} (o {@code .wav}) y la musica usa los
 * mismos ids de cargador del original ({@code 28..35}) en {@code assets/sounds/<id>.ogg}.
 * Si un fichero no existe se registra una sola vez y la reproduccion se convierte en no-op.
 */
public final class SoundBank implements Disposable, TriggerVM.SoundSink {

    private static final float MUSIC_VOLUME = 0.55f;

    /**
     * Ajustes globales de audio. Son estaticos para que el mute elegido en el menu se
     * conserve al entrar en un nivel (cada pantalla crea su propio {@code SoundBank}).
     */
    private static boolean musicMuted = Boolean.getBoolean("bounce.muteMusic");
    private static boolean fxMuted = Boolean.getBoolean("bounce.muteFx");

    private final Map<Integer, Sound> sounds = new HashMap<>();
    private final Set<Integer> missingSounds = new HashSet<>();

    private final Map<Integer, Music> musicTracks = new HashMap<>();
    private final Set<Integer> missingMusic = new HashSet<>();
    private Music currentMusic;
    private int currentMusicId = -1;

    @Override
    public void playSound(int id) {
        if (fxMuted) {
            return;
        }
        if (missingSounds.contains(id)) {
            return;
        }
        Sound sound = sounds.get(id);
        if (sound == null) {
            sound = loadSound(id);
            if (sound != null) {
                sounds.put(id, sound);
            }
            if (sound == null) {
                return;
            }
        }
        sound.play();
    }

    private Sound loadSound(int id) {
        String[] paths = {"sounds/" + id + ".ogg", "sounds/" + id + ".wav"};
        for (String path : paths) {
            if (Gdx.files.internal(path).exists()) {
                return Gdx.audio.newSound(Gdx.files.internal(path));
            }
        }
        if (missingSounds.add(id)) {
            log("No se encontro el sonido " + paths[0] + " ni " + paths[1]
                    + "; se omite (no-op).");
        }
        return null;
    }

    /**
     * Reproduce una pista en bucle. Repetir el mismo id no reinicia la musica.
     *
     * @param id id de cargador de audio del original (28..35)
     */
    public void playMusic(int id) {
        if (id < 0) {
            return;
        }
        if (id == currentMusicId && currentMusic != null && currentMusic.isPlaying()) {
            return;
        }
        stopMusic();
        if (missingMusic.contains(id)) {
            return;
        }
        Music music = musicTracks.get(id);
        if (music == null) {
            music = loadMusic(id);
            if (music == null) {
                return;
            }
            musicTracks.put(id, music);
        }
        currentMusic = music;
        currentMusicId = id;
        music.setLooping(true);
        music.setVolume(musicMuted ? 0f : MUSIC_VOLUME);
        music.play();
        log("musica " + id + " -> sounds/" + id + ".ogg"
                + (musicMuted ? " (silenciada)" : ""));
    }

    private Music loadMusic(int id) {
        String[] paths = {"sounds/" + id + ".ogg", "sounds/" + id + ".wav"};
        for (String path : paths) {
            if (Gdx.files.internal(path).exists()) {
                return Gdx.audio.newMusic(Gdx.files.internal(path));
            }
        }
        if (missingMusic.add(id)) {
            log("No se encontro la musica " + paths[0] + "; se omite (no-op). "
                    + "Ejecuta tools/convert_midi.py para generarla.");
        }
        return null;
    }

    /** Detiene la musica actual sin liberarla. */
    public void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
        }
        currentMusic = null;
        currentMusicId = -1;
    }

    public boolean isMusicPlaying() {
        return currentMusic != null && currentMusic.isPlaying();
    }

    /** Silencia o recupera la musica sin detener la pista actual. */
    public void setMusicMuted(boolean muted) {
        musicMuted = muted;
        if (currentMusic != null) {
            currentMusic.setVolume(muted ? 0f : MUSIC_VOLUME);
        }
        log("musica " + (muted ? "silenciada" : "activada"));
    }

    public boolean toggleMusicMuted() {
        setMusicMuted(!musicMuted);
        return musicMuted;
    }

    /** Silencia o recupera los efectos (los ids sin fichero siguen siendo no-op). */
    public void setFxMuted(boolean muted) {
        fxMuted = muted;
        log("efectos " + (muted ? "silenciados" : "activados"));
    }

    public boolean toggleFxMuted() {
        setFxMuted(!fxMuted);
        return fxMuted;
    }

    public static boolean isMusicMuted() {
        return musicMuted;
    }

    public static boolean isFxMuted() {
        return fxMuted;
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("SoundBank", message);
        } else {
            System.out.println("SoundBank: " + message);
        }
    }

    @Override
    public void dispose() {
        stopMusic();
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        missingSounds.clear();
        for (Music music : musicTracks.values()) {
            music.dispose();
        }
        musicTracks.clear();
        missingMusic.clear();
    }
}
