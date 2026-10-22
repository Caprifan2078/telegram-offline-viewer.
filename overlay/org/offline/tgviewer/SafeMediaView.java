package org.offline.tgviewer;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** A local-only, TextureView-based player with a privacy-aware external fallback. */
final class SafeMediaView extends LinearLayout {
    private final TextureView texture;
    private final TextView note, durationLabel;
    private final Button playPause;
    private final SeekBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Surface surface;
    private Uri uri;
    private Runnable goBack, openExternal;
    private boolean ready, dragging;
    private int generation, videoWidth, videoHeight;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!ready || player == null) return;
            try {
                int length = Math.max(0, player.getDuration()), at = player.getCurrentPosition();
                if (!dragging && length > 0) progress.setProgress((int)(1000L * at / length));
                durationLabel.setText(clock(at) + " / " + clock(length));
                playPause.setText(player.isPlaying() ? "Ⅱ Пауза" : "▶ Играть");
                handler.postDelayed(this, 400);
            } catch (IllegalStateException ignored) { }
        }
    };

    SafeMediaView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(0xff0b1320);
        FrameLayout screen = new FrameLayout(context);
        addView(screen, new LayoutParams(-1, 0, 1));
        texture = new TextureView(context);
        texture.setOpaque(false);
        screen.addView(texture, new FrameLayout.LayoutParams(-1,-1));
        note = new TextView(context);
        note.setTextColor(0xffffffff);
        note.setGravity(Gravity.CENTER);
        note.setPadding(20,20,20,20);
        screen.addView(note, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout actions = new LinearLayout(context);
        addView(actions, new LayoutParams(-1,-2));
        Button back = button(context, "← Чат", actions);
        back.setOnClickListener(v -> { if (goBack != null) goBack.run(); });
        playPause = button(context, "▶ Играть", actions);
        playPause.setOnClickListener(v -> {
            if (!ready || player == null) return;
            try {
                if (player.isPlaying()) player.pause(); else player.start();
                playPause.setText(player.isPlaying() ? "Ⅱ Пауза" : "▶ Играть");
            } catch (IllegalStateException e) { note.setText("Ошибка плеера"); }
        });
        Button external = button(context, "Другой плеер", actions);
        external.setOnClickListener(v -> { if (openExternal != null) openExternal.run(); });
        LinearLayout timeline = new LinearLayout(context);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        addView(timeline, new LayoutParams(-1,-2));
        progress = new SeekBar(context);
        progress.setMax(1000);
        timeline.addView(progress, new LayoutParams(0,-2,1));
        durationLabel = new TextView(context);
        durationLabel.setTextColor(0xffffffff);
        timeline.addView(durationLabel);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar v) { dragging = true; }
            @Override public void onProgressChanged(SeekBar v, int value, boolean user) { }
            @Override public void onStopTrackingTouch(SeekBar v) {
                dragging = false;
                if (ready && player != null) {
                    try { player.seekTo((int)(player.getDuration() * v.getProgress() / 1000.0)); }
                    catch (IllegalStateException ignored) { }
                }
            }
        });
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture t, int w, int h) { startWhenReady(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) { aspect(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t) { release(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture t) { }
        });
    }

    private Button button(Context c, String label, LinearLayout parent) {
        Button v = new Button(c);
        v.setText(label);
        v.setTextSize(11);
        v.setAllCaps(false);
        v.setMinWidth(0);
        v.setMinimumWidth(0);
        v.setPadding(4,2,4,2);
        parent.addView(v, new LayoutParams(0,-2,1));
        return v;
    }

    void play(Uri source, Runnable back, Runnable external) {
        release();
        uri = source;
        goBack = back;
        openExternal = external;
        note.setText("Открываю файл…");
        note.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        durationLabel.setText("00:00 / 00:00");
        if (texture.isAvailable()) startWhenReady();
    }

    private void startWhenReady() {
        if (uri == null || !texture.isAvailable()) return;
        release();
        final int token = generation;
        try {
            surface = new Surface(texture.getSurfaceTexture());
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setDataSource(getContext(), uri);
            mp.setSurface(surface);
            mp.setOnVideoSizeChangedListener((p,w,h) -> {
                if (token != generation || p != player) return;
                videoWidth = w; videoHeight = h; aspect();
            });
            mp.setOnInfoListener((p,what,extra) -> {
                if (token == generation && p == player
                        && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    note.setVisibility(View.GONE);
                }
                return false;
            });
            mp.setOnPreparedListener(p -> {
                if (token != generation || p != player) return;
                ready = true;
                videoWidth = p.getVideoWidth();
                videoHeight = p.getVideoHeight();
                aspect();
                note.setText(videoWidth <= 0 || videoHeight <= 0
                        ? "Аудио есть, но видео не распознано. Попробуй другой плеер."
                        : "Если экран чёрный, попробуй «Другой плеер».");
                p.start();
                handler.removeCallbacks(tick);
                handler.post(tick);
            });
            mp.setOnErrorListener((p,what,extra) -> {
                if (token == generation && p == player) {
                    ready = false;
                    note.setText("Не удалось декодировать видео (" + what + "/" + extra + "). Другой плеер →");
                    note.setVisibility(View.VISIBLE);
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception ignored) {
            note.setText("Ошибка воспроизведения. Попробуй другой плеер.");
            release();
        }
    }

    private void aspect() {
        float w = texture.getWidth(), h = texture.getHeight();
        if (w <= 0 || h <= 0 || videoWidth <= 0 || videoHeight <= 0) return;
        float ratio = (float)videoWidth / videoHeight, screen = w/h;
        Matrix matrix = new Matrix();
        matrix.setScale(screen > ratio ? ratio/screen : 1f,
                screen > ratio ? 1f : screen/ratio, w/2, h/2);
        texture.setTransform(matrix);
    }

    void pausePlayback() {
        if (ready && player != null) {
            try { if (player.isPlaying()) player.pause(); }
            catch (IllegalStateException ignored) { }
        }
    }
    void stopPlayback() {
        uri = null;
        release();
        note.setText("");
        note.setVisibility(View.VISIBLE);
    }
    private void release() {
        generation++;
        handler.removeCallbacks(tick);
        ready = false;
        videoWidth = videoHeight = 0;
        if (player != null) {
            try { player.reset(); player.release(); } catch (IllegalStateException ignored) { }
            player = null;
        }
        if (surface != null) { surface.release(); surface = null; }
    }
    private static String clock(int ms) {
        int s = Math.max(0, ms/1000);
        return s >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", s/3600, (s/60)%60, s%60)
                : String.format(Locale.ROOT, "%02d:%02d", s/60, s%60);
    }
}
