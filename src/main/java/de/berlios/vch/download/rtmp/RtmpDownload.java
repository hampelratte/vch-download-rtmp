package de.berlios.vch.download.rtmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.osgi.service.log.LogService;

import com.flazr.rtmp.client.ClientOptions;

import de.berlios.vch.download.AbstractDownload;
import de.berlios.vch.parser.IVideoPage;

public class RtmpDownload extends AbstractDownload {

    private LogService logger;

    private String scheme;
    private String host;
    private String app;
    private String streamName;
    private File localFile;
    private URI swfUri;
    private String pageUri;

    private int progress;
    private Channel channel;

    private BandwidthMeterHandler bandwidthMeterHandler;

    public RtmpDownload(IVideoPage video, LogService logger) throws URISyntaxException, UnsupportedEncodingException {
        super(video);
        this.logger = logger;

        // scheme
        this.scheme = video.getVideoUri().getScheme();

        // file
        URI uri = video.getVideoUri();
        String p = uri.getPath();
        file = p.substring(p.lastIndexOf('/'));
        if (uri.getQuery() != null) {
            file += "?" + uri.getQuery();
        }

        // host
        host = uri.getHost();

        // streamName
        streamName = (String) video.getUserData().get("streamName");
        streamName = URLDecoder.decode(streamName, "UTF-8");
        streamName = streamName.replace(' ', '+');

        // app
        logger.log(LogService.LOG_INFO, "Stream Name: " + streamName);
        app = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "") + (uri.getFragment() != null ? "#" + uri.getFragment() : "");
        app = app.substring(1); // cut off the leading /
        int pos = app.indexOf(streamName);
        if (streamName.startsWith("mp4:")) {
            if (!app.contains("mp4:")) {
                pos = app.indexOf(streamName.substring(4));
            }
        }
        app = app.substring(0, pos);
        if (app.endsWith("/")) {
            app = app.substring(0, app.length() - 1);
        }
        logger.log(LogService.LOG_INFO, "app: " + app);

        // swf verification
        if (video.getUserData().get("swfUri") != null) {
            swfUri = new URI(video.getUserData().get("swfUri").toString());
            logger.log(LogService.LOG_INFO, "swfUrl: " + swfUri);
        }

        // pageUrl
        if (video.getUserData().get("pageUrl") != null) {
            pageUri = video.getUserData().get("pageUrl").toString();
            logger.log(LogService.LOG_INFO, "pageUrl: " + pageUri);
        }

        bandwidthMeterHandler = new BandwidthMeterHandler();
    }

    @Override
    public int getProgress() {
        return progress;
    }

    @Override
    public boolean isPauseSupported() {
        return false;
    }

    private long lastPoll = System.currentTimeMillis();

    @Override
    public float getSpeed() {
        if (getStatus() == Status.DOWNLOADING) {
            // calculate throughput
            float diffInSeconds = (System.currentTimeMillis() - lastPoll) / 1000f;
            lastPoll = System.currentTimeMillis();
            long bytes = bandwidthMeterHandler.getBytesReceived();
            bandwidthMeterHandler.reset();
            float kbytes = bytes / 1024f;
            return (kbytes / diffInSeconds);
        } else {
            return -1;
        }
    }

    @Override
    public boolean isRunning() {
        return getStatus() == Status.DOWNLOADING || getStatus() == Status.STARTING;
    }

    @Override
    public void stop() {
        setStatus(Status.STOPPED);
        if (channel != null) {
            channel.close().awaitUninterruptibly();
        }
    }

    @Override
    public void cancel() {
        setStatus(Status.CANCELED);

        // delete the video file
        if (localFile != null && localFile.exists()) {
            boolean deleted = localFile.delete();
            if (!deleted) {
                logger.log(LogService.LOG_WARNING, "Couldn't delete file " + localFile.getAbsolutePath());
            }
        }
    }

    @Override
    public synchronized String getLocalFile() {
        String filename = file.substring(1);

        // cut off query parameters
        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf('?'));
        }

        // replace anything other than a-z, A-Z or 0-9 with _
        String title = getVideoPage().getTitle().replaceAll("[^a-zA-z0-9]", "_");

        return getDestinationDir() + File.separator + title + "_" + filename;
    }

    @Override
    public void run() {
        try {
            localFile = new File(getLocalFile());
            FileOutputStream fos = new FileOutputStream(localFile);
            OutputStreamFlvWriter writer = new OutputStreamFlvWriter(0, fos, new DownloadListener() {
                @Override
                public void setProgress(int percent) {
                    progress = percent;
                }

                @Override
                public void downloadFailed(Exception e) {
                    stop();
                    setStatus(Status.FAILED);
                    setException(e);
                }

                @Override
                public void downloadFinished() {
                    setStatus(Status.FINISHED);
                }

                @Override
                public void downloadStarted() {
                    setStatus(Status.DOWNLOADING);
                }
            });

            ClientOptions options;
            if ("rtmpe".equals(scheme)) {
                logger.log(LogService.LOG_INFO, "Trying to establish encrypted connection over rtmpe");
                options = new ClientOptions(host, 1935, app, streamName, getLocalFile(), true, null);
            } else {
                options = new ClientOptions(host, app, streamName, getLocalFile());
            }
            if (swfUri != null) {
                try {
                    RTMP.initSwfVerification(options, swfUri);

                    // log swf verification params
                    logger.log(LogService.LOG_INFO, "SWF size: " + options.getSwfSize());
                    String hash = "";
                    for (byte b : options.getSwfHash()) {
                        String s = Integer.toHexString(b & 0xFF);
                        s = s.length() == 1 ? "0" + s : s;
                        hash += s + " ";
                    }
                    logger.log(LogService.LOG_INFO, "HMAC SHA 256: " + hash);
                } catch (Exception e) {
                    logger.log(LogService.LOG_ERROR, "Couldn't initialize SWF verification", e);
                }
            }
            if (pageUri != null) {
                Map<String, Object> params = options.getParams();
                if (params == null) {
                    params = new HashMap<String, Object>();
                    options.setParams(params);
                }
                params.put("pageUrl", pageUri);
            }
            options.setWriterToSave(writer);
            logger.log(LogService.LOG_INFO, "Starting download: " + host + " " + app + " " + streamName);
            final ClientBootstrap bootstrap = getBootstrap(Executors.newCachedThreadPool(), bandwidthMeterHandler, options);
            final ChannelFuture future = bootstrap.connect(new InetSocketAddress(options.getHost(), options.getPort()));
            future.awaitUninterruptibly();
            if (!future.isSuccess()) {
                logger.log(LogService.LOG_ERROR, "Error creating client connection", future.getCause());
                setStatus(Status.FAILED);
                setException(future.getCause());
            }
            channel = future.getChannel();
            future.getChannel().getCloseFuture().awaitUninterruptibly();
            if (getProgress() == 100) {
                setStatus(Status.FINISHED);
            } else {
                setStatus(Status.STOPPED);
            }
            bootstrap.getFactory().releaseExternalResources();

        } catch (FileNotFoundException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't start download to file " + getLocalFile(), e);
        }
    }

    public static ClientBootstrap getBootstrap(final Executor executor, final BandwidthMeterHandler bandwidthMeterHandler, final ClientOptions options) {
        final ChannelFactory factory = new NioClientSocketChannelFactory(executor, executor);
        final ClientBootstrap bootstrap = new ClientBootstrap(factory);
        bootstrap.setPipelineFactory(new RtmpPipelineFactory(bandwidthMeterHandler, options));
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
        return bootstrap;
    }
}