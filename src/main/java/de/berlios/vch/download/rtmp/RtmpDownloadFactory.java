package de.berlios.vch.download.rtmp;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.osgi.service.log.LogService;

import de.berlios.vch.download.Download;
import de.berlios.vch.download.DownloadFactory;
import de.berlios.vch.parser.IVideoPage;

@Component
@Provides
public class RtmpDownloadFactory implements DownloadFactory {

    @Requires
    private LogService logger;

    private boolean valid = false;

    public RtmpDownloadFactory(LogService logger) {
        this.logger = logger;
    }

    @Override
    public boolean accept(IVideoPage video) {
        if (valid && video.getVideoUri() != null) {
            return "rtmp".equals(video.getVideoUri().getScheme()) || "rtmpt".equals(video.getVideoUri().getScheme())
                    || "rtmpe".equals(video.getVideoUri().getScheme());
        }
        return false;
    }

    @Override
    public Download createDownload(IVideoPage page) throws URISyntaxException, UnsupportedEncodingException {
        return new RtmpDownload(page, logger);
    }

    @Validate
    public void start() {
        valid = true;
    }

    @Invalidate
    public void stop() {
        valid = false;
    }
}
