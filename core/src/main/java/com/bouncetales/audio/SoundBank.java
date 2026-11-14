package com.bouncetales.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.bouncetales.world.script.TriggerVM;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Carga sonidos bajo demanda y permite ejecutar el juego sin el paquete de audio. */
public final class SoundBank implements Disposable, TriggerVM.SoundSink {

    private final Map<Integer, Sound> sounds = new HashMap<>();
    private final Set<Integer> missing = new HashSet<>();

    @Override
    public void playSound(int id) {
        Sound sound = sounds.get(id);
        if (sound == null && !sounds.containsKey(id)) {
            sound = load(id);
        }
        if (sound != null) {
            sound.play();
        }
    }

    private Sound load(int id) {
        String basePath = "sounds/" + id;
        for (String extension : new String[]{".ogg", ".wav"}) {
            String path = basePath + extension;
            if (Gdx.files.internal(path).exists()) {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(path));
                sounds.put(id, sound);
                return sound;
            }
        }
        if (missing.add(id)) {
            log("No se encontro el sonido " + basePath + ".ogg ni .wav; se omite.");
        }
        return null;
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
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        missing.clear();
    }
}
