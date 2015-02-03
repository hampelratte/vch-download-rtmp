/*
 * Flazr <http://flazr.com> Copyright (C) 2009  Peter Thomas.
 *
 * This file is part of Flazr.
 *
 * Flazr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flazr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Flazr.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 *  Modified 08.03.2010 by Henrik Niehaus. Added support for other channels 
 */
package de.berlios.vch.download.rtmp;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flazr.io.flv.FlvAtom;
import com.flazr.rtmp.RtmpHeader;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpWriter;
import com.flazr.rtmp.message.Metadata;

public class OutputStreamFlvWriter implements RtmpWriter {

    private static final Logger logger = LoggerFactory.getLogger(OutputStreamFlvWriter.class);

    private final OutputStream out;
    private final int[] channelTimes = new int[RtmpHeader.MAX_CHANNEL_ID];
    private int primaryChannel = -1;
    private int lastLoggedSeconds;
    private final int seekTime;
    private final long startTime;
    private DownloadListener listener;
    private double duration;

    public OutputStreamFlvWriter(final int seekTime, final OutputStream out, DownloadListener listener) {
        this.seekTime = seekTime < 0 ? 0 : seekTime;
        this.out = out;
        this.startTime = System.currentTimeMillis();
        this.listener = listener;

        try {
            ByteBuffer b = FlvAtom.flvHeader().toByteBuffer(); 
            out.write(b.array(), b.position(), b.limit());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }

    @Override
    public void close() {
        if(out != null) {
            try {
                out.close();
            } catch (Exception e) {
                // fail silently
            }
        }
        if(primaryChannel == -1) {
            logger.warn("no media was written, closed file");
            return;
        }
        logger.info("finished in {} seconds, media duration: {} seconds (seek time: {})",
                new Object[]{(System.currentTimeMillis() - startTime) / 1000,
                (channelTimes[primaryChannel] - seekTime) / 1000, 
                seekTime / 1000});
    }

    private void logWriteProgress() {
        final int seconds = (channelTimes[primaryChannel] - seekTime) / 1000;
        int progress = (int)((seconds/duration)*100);
        if(listener != null) {
            listener.setProgress(progress);
        }
        if (seconds >= lastLoggedSeconds + 10) {
            logger.debug("write progress: " + seconds + " seconds");
            lastLoggedSeconds = seconds - (seconds % 10);
        }
    }

    @Override
    public void write(final RtmpMessage message) {
        final RtmpHeader header = message.getHeader();
        if(header.isAggregate()) {
            final ChannelBuffer in = message.encode();
            while (in.readable()) {
                final FlvAtom flvAtom = new FlvAtom(in);
                final int absoluteTime = flvAtom.getHeader().getTime();
                channelTimes[primaryChannel] = absoluteTime;
                write(flvAtom);
                // logger.debug("aggregate atom: {}", flvAtom);
                logWriteProgress();
            }
        } else { // METADATA / AUDIO / VIDEO
            if(message instanceof Metadata) {
                duration = ((Metadata)message).getDuration();
            }
            
            final int channelId = header.getChannelId();
            channelTimes[channelId] = seekTime + header.getTime();
            if(primaryChannel == -1 && (header.isAudio() || header.isVideo())) {
                logger.info("first media packet for channel: {}", header);
                primaryChannel = channelId;
                if(listener != null) {
                    listener.downloadStarted();
                }
            }
            if(header.getSize() <= 2) {
                return;
            }
            write(new FlvAtom(header.getMessageType(), channelTimes[channelId], message.encode()));
            if (channelId == primaryChannel) {
                logWriteProgress();
            }
        }
    }

    private void write(final FlvAtom flvAtom) {
        if(logger.isDebugEnabled()) {
            logger.debug("writing: {}", flvAtom);
        }
        if(out == null) {
            return;
        }
        try {
            ByteBuffer b = flvAtom.write().toByteBuffer(); 
            out.write(b.array(), b.position(), b.limit());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
