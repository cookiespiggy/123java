package l.ys.netty.slidingwindow;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class NettyClient {
    public String host = "127.0.0.1"; // ip地址
    public int port = 9876; // 端口
    // 通过nio方式来接收连接和处理连接
    private EventLoopGroup group = new NioEventLoopGroup();
    public static NettyClient nettyClient = new NettyClient();

    /**
     * 唯一标记
     */
    private boolean initFalg = true;

    public static void main(String[] args) {
        nettyClient.run();
    }

    /**
     * Netty创建全部都是实现自AbstractBootstrap。 客户端的是Bootstrap，服务端的则是 ServerBootstrap。
     **/
    public void run() {
        doConnect(new Bootstrap(), group);
    }

    /**
     * 重连
     */
    public void doConnect(Bootstrap bootstrap, EventLoopGroup eventLoopGroup) {
        ChannelFuture f = null;
        try {
            if (bootstrap != null) {
                bootstrap.group(eventLoopGroup);
                bootstrap.channel(NioSocketChannel.class);
                bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
                bootstrap.handler(new NettyClientFilter());
                bootstrap.remoteAddress(host, port);
                f = bootstrap.connect().addListener((ChannelFuture futureListener) -> {
                    final EventLoop eventLoop = futureListener.channel().eventLoop();
                    if (!futureListener.isSuccess()) {
                        System.out.println("与服务端断开连接!在10s之后准备尝试重连!");
                        eventLoop.schedule(() -> doConnect(new Bootstrap(), eventLoop), 10, TimeUnit.SECONDS);
                    }
                });
                if (initFalg) {
                    System.out.println("Netty客户端启动成功!");
                    initFalg = false;
                }
                // 阻塞
                f.channel().closeFuture().sync();
            }
        } catch (Exception e) {
            System.out.println("客户端连接失败!" + e.getMessage());
        }

    }

    public class NettyClientFilter extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline ph = ch.pipeline();
            /*
             * 解码和编码，应和服务端一致
             * */
//        ph.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
            //入参说明: 读超时时间、写超时时间、所有类型的超时时间、时间格式
            //因为服务端设置的超时时间是5秒，所以设置4秒
            ph.addLast(new IdleStateHandler(0, 4, 0, TimeUnit.SECONDS));
            ph.addLast("decoder", new StringDecoder());
            ph.addLast("encoder", new StringEncoder());
            ph.addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024));
            ph.addLast("handler", new NettyClientHandler()); //客户端的逻辑
        }
    }

    public class NettyClientHandler extends ChannelInboundHandlerAdapter {
        /**
         * 发送次数
         */
        private int count = 1;

        /**
         * 循环次数
         */
        private int fcount = 1;


        /**
         * 心跳指令
         */
        private final String CMD_HEART = "cmd_heart";
        /**
         * 登录指令
         */
        private final String CMD_LOGIN = "cmd_login";

        /**
         * 建立连接时
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("建立连接时：" + new Date());
            ctx.fireChannelActive();
            Message message = new Message();
            message.setCmd(CMD_LOGIN);
            message.setId(1);
            message.setMsg("请求登录!");
            ctx.writeAndFlush(message.toString());
        }

        /**
         * 关闭连接时
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("关闭连接时：" + new Date());
            final EventLoop eventLoop = ctx.channel().eventLoop();
            NettyClient.nettyClient.doConnect(new Bootstrap(), eventLoop);
            super.channelInactive(ctx);
        }

        /**
         * 心跳请求处理
         * 每4秒发送一次心跳请求;
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            System.out.println("循环请求的时间：" + new Date() + "，次数" + fcount);
            if (obj instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) obj;
                //如果写通道处于空闲状态,就发送心跳命令
                if (IdleState.WRITER_IDLE.equals(event.state())) {
                    Message message = new Message();
                    message.setCmd(CMD_HEART);
                    ctx.channel().writeAndFlush(message.toString());
                    fcount++;
                }
            }
        }

        /**
         * 业务逻辑处理
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("第" + count + "次" + ",客户端接受的消息:" + msg);
            count++;
        }

    }
}
