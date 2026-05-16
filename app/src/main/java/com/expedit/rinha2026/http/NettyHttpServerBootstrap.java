package com.expedit.rinha2026.http;

import com.expedit.rinha2026.bootstrap.AppConfig;
import com.expedit.rinha2026.bootstrap.ReadinessState;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public final class NettyHttpServerBootstrap {
    private static final int MAX_CONTENT_LENGTH = 64 * 1024;

    public Channel start(AppConfig appConfig, ReadinessState readinessState, HttpRequestContext context) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(appConfig.serverThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 8192)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                            .addLast(new HttpServerCodec())
                            .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                            .addLast(new NettyRequestHandler(readinessState, context));
                    }
                });

            Channel channel = bootstrap.bind(appConfig.port()).sync().channel();
            channel.closeFuture().addListener(future -> {
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            });
            return channel;
        } catch (InterruptedException interruptedException) {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            Thread.currentThread().interrupt();
            throw interruptedException;
        } catch (RuntimeException runtimeException) {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            throw runtimeException;
        }
    }
}
