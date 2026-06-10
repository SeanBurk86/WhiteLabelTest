package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

public class AudioManager implements Disposable {
    private final Music music;
    private final Sound dropSound;
    private final Sound plasmaShotSound;

    public AudioManager() {
        dropSound = Gdx.audio.newSound(Gdx.files.internal("bgaregga-073.wav"));
        plasmaShotSound = Gdx.audio.newSound(Gdx.files.internal("plasmashot.mp3"));

        music = Gdx.audio.newMusic(Gdx.files.internal("music.mp3"));
        music.setLooping(true);
        music.setVolume(0.5f);
    }

    public void playMusic() {
        if (!music.isPlaying()) music.play();
    }

    public void stopMusic() {
        music.stop();
    }

    public void playDrop() {
        dropSound.play();
    }

    public void playPlasmaShot() {
        if (plasmaShotSound != null) plasmaShotSound.play();
    }

    @Override
    public void dispose() {
        dropSound.dispose();
        if (plasmaShotSound != null) plasmaShotSound.dispose();
        music.dispose();
    }
}
