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
package de.berlios.vch.download.rtmp;

import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

@ChannelPipelineCoverage("one")
public class BandwidthMeterHandler extends SimpleChannelHandler {
    private AtomicLong bytesSent = new AtomicLong();
    private AtomicLong bytesReceived = new AtomicLong();

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

        if (e instanceof MessageEvent && ((MessageEvent) e).getMessage() instanceof ChannelBuffer) {
            ChannelBuffer b = (ChannelBuffer) ((MessageEvent) e).getMessage();
            long readableBytes = b.readableBytes();
            bytesReceived.addAndGet(readableBytes);
        }

        super.handleUpstream(ctx, e);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof MessageEvent && ((MessageEvent) e).getMessage() instanceof ChannelBuffer) {
            ChannelBuffer b = (ChannelBuffer) ((MessageEvent) e).getMessage();
            bytesSent.addAndGet(b.readableBytes());
        }

        super.handleDownstream(ctx, e);
    }

    public void reset() {
        bytesSent.set(0);
        bytesReceived.set(0);
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }
}
