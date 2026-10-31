package lasertag;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/** Chiptune-style synthesized sound effects. Silently disabled if no audio device is available. */
final class Sound {
    private Sound() {}

    private static final float RATE = 22050;
    private static volatile Clip[][] pools;
    static volatile boolean muted;

    static final int ZAP = 0, TAG = 1, ROUND = 2, BLIP = 3, SELECT = 4;

    /** Synthesizes the clips in the background so start-up isn't delayed. */
    static void init() {
        Thread t = new Thread(() -> {
            try {
                pools = new Clip[][] {
                    pool(sweep(1400, 300, 0.09, 0.18, true), 6),
                    pool(noise(0.35, 0.35), 3),
                    pool(chime(), 1),
                    pool(sweep(880, 880, 0.04, 0.15, true), 3),
                    pool(sweep(660, 1320, 0.10, 0.18, true), 2),
                };
            } catch (Exception | LinkageError e) {
                pools = null;
            }
        }, "lt-sound");
        t.setDaemon(true);
        t.start();
    }

    static void play(int sound) {
        Clip[][] p = pools;
        if (muted || p == null) return;
        for (Clip c : p[sound]) {
            if (!c.isRunning()) {
                c.setFramePosition(0);
                c.start();
                return;
            }
        }
    }

    private static Clip[] pool(byte[] data, int size) throws Exception {
        AudioFormat fmt = new AudioFormat(RATE, 8, 1, true, false);
        Clip[] clips = new Clip[size];
        for (int i = 0; i < size; i++) {
            clips[i] = AudioSystem.getClip();
            clips[i].open(fmt, data, 0, data.length);
        }
        return clips;
    }

    private static byte[] sweep(double f0, double f1, double seconds, double volume, boolean square) {
        int n = (int) (RATE * seconds);
        byte[] out = new byte[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double k = i / (double) n;
            phase += 2 * Math.PI * (f0 + (f1 - f0) * k) / RATE;
            double s = square ? Math.signum(Math.sin(phase)) : Math.sin(phase) + 0.5 * Math.sin(phase * 2.01);
            out[i] = (byte) (s * volume * (1 - k) * 127);
        }
        return out;
    }

    /** An 8-bit style noise burst (LFSR, like old sound chips) that drops in pitch. */
    private static byte[] noise(double seconds, double volume) {
        int n = (int) (RATE * seconds);
        byte[] out = new byte[n];
        int lfsr = 0x7FFF;
        double hold = 0, value = 1;
        for (int i = 0; i < n; i++) {
            double k = i / (double) n;
            hold += 1 + k * 12; // sample-and-hold period grows, lowering the pitch
            if (hold >= 4) {
                hold = 0;
                int bit = (lfsr ^ (lfsr >> 1)) & 1;
                lfsr = (lfsr >> 1) | (bit << 14);
                value = (lfsr & 1) == 0 ? 1 : -1;
            }
            out[i] = (byte) (value * volume * (1 - k) * 127);
        }
        return out;
    }

    private static byte[] chime() {
        double[] notes = {523, 659, 784, 1047};
        int per = (int) (RATE * 0.12);
        byte[] out = new byte[per * notes.length];
        for (int j = 0; j < notes.length; j++) {
            for (int i = 0; i < per; i++) {
                double env = 1 - i / (double) per;
                out[j * per + i] = (byte) (Math.signum(Math.sin(2 * Math.PI * notes[j] * i / RATE)) * 0.18 * env * 127);
            }
        }
        return out;
    }
}
