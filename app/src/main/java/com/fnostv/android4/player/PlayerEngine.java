package com.fnostv.android4.player;

import android.graphics.SurfaceTexture;

public interface PlayerEngine {
    interface Listener {
        void onPrepared(int videoWidth, int videoHeight, int durationMs);

        void onVideoSizeChanged(int videoWidth, int videoHeight);

        void onBufferingStart(int extra);

        void onBufferingEnd(int extra);

        void onFirstFrame();

        /** 视频输出层真正建立（VLC 的 Vout 事件），画面马上能看见。 */
        void onVideoOutputReady();

        void onError(String reason);

        /** 自然播放到片尾（区别于用户停止/切换片源）。 */
        void onCompletion();

        void onLog(String message);
    }

    String name();

    void setListener(Listener listener);

    void attachSurface(SurfaceTexture texture, int width, int height);

    void resizeSurface(int width, int height);

    void setFillMode(boolean fillMode);

    /** 0.0 ~ 1.0，仅用于回前台恢复时静音等待出画，不影响系统音量。 */
    void setVolume(float volume);

    void detachSurface();

    void prepare(String url, PlaybackOptions options) throws Exception;

    void start();

    void pause();

    void stop();

    void seekTo(int positionMs);

    boolean setSpeed(float speed);

    boolean isPlaying();

    int getDuration();

    int getCurrentPosition();

    void release();
}
