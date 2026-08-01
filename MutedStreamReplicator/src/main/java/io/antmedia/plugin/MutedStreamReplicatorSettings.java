package io.antmedia.plugin;

public class MutedStreamReplicatorSettings {

    /** Suffix appended to the source stream ID to form the muted replica stream ID. */
    private String mutedStreamSuffix = "-muted";

    /** Prefix prepended to the source stream ID to form the muted replica stream ID. */
    private String mutedStreamPrefix = "";

    /**
     * If true, the muted replica broadcast record is kept in the datastore after the source stream ends.
     * If false (default), the muted replica broadcast is deleted when the source stream ends.
     */
    private boolean keepMutedStreamsAfterEnd = false;

    /** If true, removes replica endpoints left in the datastore by a previous crash on startup. */
    private boolean cleanupOrphanedReplicasOnStartup = true;

    public String getMutedStreamSuffix() { return mutedStreamSuffix; }
    public void setMutedStreamSuffix(String v) { this.mutedStreamSuffix = v; }

    public String getMutedStreamPrefix() { return mutedStreamPrefix; }
    public void setMutedStreamPrefix(String v) { this.mutedStreamPrefix = v; }

    public boolean isKeepMutedStreamsAfterEnd() { return keepMutedStreamsAfterEnd; }
    public void setKeepMutedStreamsAfterEnd(boolean v) { this.keepMutedStreamsAfterEnd = v; }

    public boolean isCleanupOrphanedReplicasOnStartup() { return cleanupOrphanedReplicasOnStartup; }
    public void setCleanupOrphanedReplicasOnStartup(boolean v) { this.cleanupOrphanedReplicasOnStartup = v; }
}
