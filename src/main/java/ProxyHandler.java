import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.buffer.Unpooled;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ProxyHandler extends ChannelInboundHandlerAdapter {
    private static final String targetHost = "oauth.vk.com";
    private static final int targetPort = 443;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("message");
        System.out.println(msg);
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            String uri = request.uri();

            System.out.println("uri");

            // Проверяем, начинается ли URI запроса с https://oauth.vk.com
            if (uri.startsWith("https://oauth.vk.com")) {
                System.out.println("oauth");
                // Создаем подключение к VPN-серверу и пересылаем запрос туда
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(ctx.channel().eventLoop())
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new ForwardHandler(ctx.channel()));
                            }
                        });

                ChannelFuture future = bootstrap.connect(targetHost, targetPort);
                future.addListener(f -> {
                    if (future.isSuccess()) {
                        Channel vpnChannel = future.channel();
                        vpnChannel.writeAndFlush(request);
                    }
                });
            } else {
                // Обрабатываем остальные запросы здесь
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
                ctx.writeAndFlush(response);
            }
        }
    }
}

class ForwardHandler extends ChannelInboundHandlerAdapter {
    private final Channel clientChannel;

    public ForwardHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Полученный ответ от VPN-сервера, отправляем его клиенту
        clientChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
