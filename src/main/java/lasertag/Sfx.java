package lasertag;

/** Sound effects, played by javax.sound on the desktop and Web Audio in the browser. */
interface Sfx {
    int ZAP = 0, TAG = 1, ROUND = 2, BLIP = 3, SELECT = 4;

    void play(int sound);

    Sfx SILENT = sound -> { };
}
