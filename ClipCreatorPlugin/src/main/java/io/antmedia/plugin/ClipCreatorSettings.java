package io.antmedia.plugin;

public class ClipCreatorSettings {

    //10 minutes
    private int mp4CreationIntervalSeconds = 600;

    public int getMp4CreationIntervalSeconds() {
        return mp4CreationIntervalSeconds;
    }

    public void setMp4CreationIntervalSeconds(int mp4CreationIntervalSeconds) {
        this.mp4CreationIntervalSeconds = mp4CreationIntervalSeconds;
    }
}
