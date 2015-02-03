/*
 * The MIT License
 *
 * Copyright (c) 2009 Carl Byström
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.berlios.vch.download.log.rtmp;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelPipelineCoverage("one")
public class LoggingHandler extends org.jboss.netty.handler.logging.LoggingHandler {

    private static transient Logger logger = LoggerFactory.getLogger(LoggingHandler.class);

    private boolean handleUpstream, handleDownstream;

    public LoggingHandler(boolean handleUpstream, boolean handleDownstream) {
        this.handleUpstream = handleUpstream;
        this.handleDownstream = handleDownstream;
    }

    public void log(ChannelEvent e, boolean upstream) {
        if (e instanceof WriteCompletionEvent) {
            return;
        }

        String msg = e.toString();

        if (e instanceof MessageEvent) {
            MessageEvent me = (MessageEvent) e;

            if (me.getMessage() instanceof ChannelBuffer) {
                ChannelBuffer buf = (ChannelBuffer) me.getMessage();
                buf.markReaderIndex();
                buf.markWriterIndex();

                byte[] bytes = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), bytes);
                msg += msg + "\n  " + StringUtils.toHexString(bytes);

                buf.resetReaderIndex();
                buf.resetWriterIndex();
            }
        }

        // Log the message (and exception if available.)
        if (e instanceof ExceptionEvent) {
            logger.error(msg, ((ExceptionEvent) e).getCause());
        } else {
            logger.debug("{} Event: {}", new Object[] { (upstream ? "Upstream" : "Downstream"), msg,
                    e.getClass().getName() });
        }
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (handleUpstream) {
            log(e, true);
        }
        ctx.sendUpstream(e);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (handleDownstream) {
            log(e, false);
        }
        ctx.sendDownstream(e);
    }
}
