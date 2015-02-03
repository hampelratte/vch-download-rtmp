package de.berlios.vch.download.rtmp;

public interface DownloadListener {
    
    public void setProgress(int percent);
    
    public void downloadStarted();
    
    public void downloadFinished();
    
    public void downloadFailed(Exception e);
}
