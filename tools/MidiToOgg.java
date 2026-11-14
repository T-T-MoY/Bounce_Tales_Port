import com.sun.media.sound.AudioSynthesizer;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renderiza un MIDI a WAV usando el sintetizador Gervill incluido en el JDK.
 *
 * <p>Se ejecuta en modo fuente (JDK 17+):
 * <pre>
 *   java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
 *        tools/MidiToOgg.java resources/aw.mid out/aw.wav
 * </pre>
 *
 * <p>La reproduccion es offline: se envian todos los eventos al receptor y se lee el
 * {@link AudioInputStream} del sintetizador, sin depender de la tarjeta de sonido.
 * {@code tools/convert_midi.py} lo usa y despues convierte el WAV a OGG con ffmpeg.
 */
public final class MidiToOgg {

    private static final float SAMPLE_RATE = 44100f;
    private static final int BITS = 16;
    private static final int CHANNELS = 2;
    private static final double TAIL_SECONDS = 1.25;

    private MidiToOgg() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("uso: java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED "
                    + "tools/MidiToOgg.java <entrada.mid> <salida.wav>");
            System.exit(2);
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        render(input, output);
        System.out.println("wav: " + output.getAbsolutePath()
                + " (" + output.length() + " bytes)");
    }

    /** Renderiza {@code input} a un WAV PCM 16-bit estereo. */
    public static void render(File input, File output) throws Exception {
        Sequence sequence = MidiSystem.getSequence(input);

        AudioFormat format = new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false);
        Synthesizer base = MidiSystem.getSynthesizer();
        if (!(base instanceof AudioSynthesizer)) {
            base.close();
            throw new IllegalStateException("El sintetizador del JDK no implementa AudioSynthesizer");
        }
        AudioSynthesizer synth = (AudioSynthesizer) base;

        AudioInputStream stream = synth.openStream(format, null);
        Receiver receiver = synth.getReceiver();

        long tickLength = Math.max(1L, sequence.getTickLength());
        long microLength = sequence.getMicrosecondLength();
        List<TimedEvent> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                events.add(new TimedEvent(track.get(i)));
            }
        }
        events.sort(Comparator.comparingLong(e -> e.event.getTick()));

        for (TimedEvent timed : events) {
            MidiMessage message = timed.event.getMessage();
            if (message instanceof MetaMessage) {
                continue; // tempo/meta ya estan aplicados en microLength
            }
            long timestamp = timed.event.getTick() * microLength / tickLength;
            receiver.send(message, timestamp);
        }

        // Apagar todas las notas antes del silencio final para no dejar colas infinitas.
        for (int channel = 0; channel < 16; channel++) {
            receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 123, 0),
                    microLength);
        }

        double seconds = microLength / 1_000_000.0 + TAIL_SECONDS;
        long frames = (long) Math.ceil(seconds * format.getSampleRate());
        int frameSize = format.getFrameSize();
        byte[] data = new byte[(int) Math.min(Integer.MAX_VALUE - 8L, frames * frameSize)];
        int offset = 0;
        while (offset < data.length) {
            int read = stream.read(data, offset, Math.min(8192, data.length - offset));
            if (read < 0) {
                break;
            }
            offset += read;
        }

        try {
            stream.close();
        } catch (Exception ignored) {
            // El sintetizador ya no se necesita.
        }
        synth.close();

        int usable = offset - (offset % frameSize);
        AudioInputStream rendered = new AudioInputStream(
                new ByteArrayInputStream(data, 0, usable), format, usable / frameSize);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("No se pudo crear el directorio " + parent);
        }
        AudioSystem.write(rendered, AudioFileFormat.Type.WAVE, output);
    }

    private static final class TimedEvent {
        final MidiEvent event;

        TimedEvent(MidiEvent event) {
            this.event = event;
        }
    }
}
