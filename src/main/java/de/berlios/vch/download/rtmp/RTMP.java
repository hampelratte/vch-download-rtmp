package de.berlios.vch.download.rtmp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import com.flazr.rtmp.RtmpHandshake;
import com.flazr.rtmp.client.ClientOptions;
import com.flazr.util.Utils;

import de.berlios.vch.net.INetworkProtocol;

@Component
@Provides
public class RTMP implements INetworkProtocol {

    private List<String> schemes = Arrays.asList(new String[] { "rtmp", "rtmpt", "rtmpe" });

    @Requires
    private LogService logger;

    private BundleContext ctx;

    public RTMP(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "Real Time Messaging Protocol";
    }

    @Override
    public List<String> getSchemes() {
        return schemes;
    }

    @Override
    public boolean isBridgeNeeded() {
        return true;
    }

    @Override
    public URI toBridgeUri(URI videoUri, Map<String, ?> connectionDetails) throws URISyntaxException {
        // host
        String host = videoUri.getHost();

        // app
        String streamName = (String) connectionDetails.get("streamName");
        String app = videoUri.getPath() + (videoUri.getQuery() != null ? "?" + videoUri.getQuery() : "")
                + (videoUri.getFragment() != null ? "#" + videoUri.getFragment() : "");
        app = app.substring(1); // cut off the leading /
        int pos = app.indexOf(streamName);
        if (streamName.startsWith("mp4:")) {
            if (!app.contains("mp4:")) {
                pos = app.indexOf(streamName.substring(4));
            }
        }
        if (pos > 0) {
            app = app.substring(0, pos);
        }

        String servletHost = "localhost";
        InetAddress localMachine;
        try {
            localMachine = InetAddress.getLocalHost();
            servletHost = localMachine.getHostName();
        } catch (UnknownHostException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't determine host name of this host. Falling back to \"localhost\"");
        }
        String port = ctx.getProperty("org.osgi.service.http.port");
        String uri = null;
        try {
            uri = "http://" + servletHost + ":" + port + StreamBridge.PATH + "?host=" + host + "&app=" + URLEncoder.encode(app, "UTF-8") + "&stream="
                    + URLEncoder.encode(streamName, "UTF-8") + "&scheme=" + videoUri.getScheme();
            if (connectionDetails.containsKey("swfUri")) {
                uri += "&swfUri=" + URLEncoder.encode(connectionDetails.get("swfUri").toString(), "UTF-8");
            }
            if (connectionDetails.containsKey("pageUrl")) {
                uri += "&pageUrl=" + URLEncoder.encode(connectionDetails.get("pageUrl").toString(), "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            logger.log(LogService.LOG_ERROR, "Couldn't create bridge uri", e);
        }
        logger.log(LogService.LOG_DEBUG, "Bridge URI is " + uri);
        return new URI(uri);
    }

    public static void initSwfVerification(ClientOptions options, URI swfUri) throws MalformedURLException, IOException, InvalidKeyException,
            NoSuchAlgorithmException {
        InputStream in = swfUri.toURL().openStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SwfFile.decompressSwf(in, bos);
        byte[] data = bos.toByteArray();
        byte[] hmacSha256 = Utils.sha256(data, RtmpHandshake.CLIENT_CONST);
        options.setSwfHash(hmacSha256);
        options.setSwfSize(data.length);
    }
}
