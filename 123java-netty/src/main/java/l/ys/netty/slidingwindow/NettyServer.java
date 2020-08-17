package l.ys.netty.slidingwindow;

import com.alibaba.fastjson.JSON;
import com.cloudhopper.commons.util.windowing.DuplicateKeyException;
import com.cloudhopper.commons.util.windowing.OfferTimeoutException;
import com.cloudhopper.commons.util.windowing.Window;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.TimeUnit;

/**
 * Netty服务端 滑动窗口 测试
 */
public class NettyServer {
    private static final int port = 9876; // 设置服务端端口
    // 创建一个boss和work线程
    private EventLoopGroup boss = null;
    private EventLoopGroup work = null;
    private ServerBootstrap b = null;

    // 定义一个通道
    private ChannelGroup channels;
    private ChannelFuture f;
    // 定义通道组的共用定时器任务
    private EventExecutor executor = new DefaultEventExecutor(new DefaultThreadFactory("netty-server-threadFactory"));

    public NettyServer() {
        // 为boss组指定一个线程 // 通过nio方式来接收连接和处理连接
        boss = new NioEventLoopGroup(1, new DefaultThreadFactory("netty-server-boss"));
        // 为work组指定当前系统cup个数乘以2的线程数 // 通过nio方式来接收连接和处理连接
        work = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2,
                new DefaultThreadFactory("netty-server-work"));
        channels = new DefaultChannelGroup(executor);
    }

    public void run() {
        try {
            //初始化
            init();
            // 服务器绑定端口监听
            f = b.bind(port).sync();
            System.out.println("服务端启动成功,端口是:" + port);
            // 监听服务器关闭监听
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    /**
     * 初始化
     */
    private void init() {
        if (null != b) {
            return;
        }
        b = new ServerBootstrap();
        b.group(boss, work);
        b.channel(NioServerSocketChannel.class);
        //
        int backlog = 128;
        //设置缓冲区队列
        b.option(ChannelOption.SO_BACKLOG, backlog)
                //设置是否可以重用端口
                .option(ChannelOption.SO_REUSEADDR, false);
        //设置无延时
        b.childOption(ChannelOption.TCP_NODELAY, true)
                //设置套接字发送缓冲区大小
                .childOption(ChannelOption.SO_SNDBUF, 1000 * 1024 * 1024);
        // 设置过滤器
        b.childHandler(new NettyServerFilter());

    }

    /*
     * 释放资源
     */
    public void destroy() {
        if (null != channels) {
            channels.close().awaitUninterruptibly();
        }
        if (null != work) {
            work.shutdownGracefully(); //关闭EventLoopGroup，释放掉所有资源包括创建的线程
        }
        if (null != boss) {
            boss.shutdownGracefully(); //关闭EventLoopGroup，释放掉所有资源包括创建的线程
        }
    }

    private static class NettyServerFilter extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline ph = ch.pipeline();
            // 解码和编码，应和客户端一致
            //入参说明: 读超时时间、写超时时间、所有类型的超时时间、时间格式
            ph.addLast(new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS));
            ph.addLast("decoder", new StringDecoder());
            ph.addLast("encoder", new StringEncoder());
            ph.addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024));
            ph.addLast("handler", new NettyServerHandler());// 服务端业务逻辑
        }
    }

    public static class NettyServerHandler extends ChannelInboundHandlerAdapter {
        /**
         * 空闲次数
         */
        private int idle_count = 1;
        /**
         * 发送次数
         */
        private int count = 1;

        /**
         * 心跳指令
         */
        private final String CMD_HEART = "cmd_heart";
        /**
         * 登录指令
         */
        private final String CMD_LOGIN = "cmd_login";
        /**
         * 成功
         */
        private final String CMD_SUC = "ok";

        boolean flag = true;

        // 滑动窗口
        private Window<Integer, Message, Message> window = new Window<Integer, Message, Message>(20);

        /**
         * 超时处理 如果5秒没有接受客户端的心跳，就触发; 如果超过两次，则直接关闭;
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            if (obj instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) obj;
                if (IdleState.READER_IDLE.equals(event.state())) { // 如果读通道处于空闲状态，说明没有接收到心跳命令
                    System.out.println("已经5秒没有接收到客户端的信息了");
                    if (idle_count > 1) {
                        System.out.println("关闭这个不活跃的channel");
                        ctx.channel().close();
                    }
                    idle_count++;
                }
            } else {
                super.userEventTriggered(ctx, obj);
            }
        }

        /**
         * 业务逻辑处理
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
            System.out.println("第" + count + "次" + ",服务端接受的消息:" + obj);
            Message msg = JSON.parseObject(obj.toString(), Message.class);
            String cmd = msg.getCmd();
            // 如果是心跳命令，则发送给客户端
            if (CMD_HEART.equals(cmd)) {
                msg.setCmd(CMD_SUC);
                msg.setMsg("服务端成功收到请求!");
                ctx.writeAndFlush(msg.toString());
            } else if (CMD_LOGIN.equals(cmd)) {
                handlerResponse(msg);
                msg.setCmd(CMD_SUC);
                msg.setMsg("登录成功！");
                ctx.writeAndFlush(msg.toString());

            } else {
                System.out.println("未知命令!" + cmd);
                return;
            }
            if (flag) {
                msg.setCmd(CMD_SUC);
                msg.setMsg("滑动窗口测试!");
                new Thread(() -> handlerRequest(msg, ctx)).start();
                flag = false;
            }
            count++;
        }

        /**
         * 异常处理
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }

        /**
         * 滑动窗口响应缓存
         */
        public void handlerResponse(Message msg) {
            int id = msg.getId();
            WindowFuture<Integer, Message, Message> future = null;
            try {

                future = window.complete(id, msg);
            } catch (InterruptedException e) {
                System.err.println("完成窗口失败！ID:" + id + " 异常:" + e);
                return;
            }
            if (null == future) {
                System.err.println("完成窗口在完成方法引用前失败!id:" + id);
                return;
            }
            if (future.isSuccess()) {
                System.err.println("等待响应的请求:" + future.getRequest());
                return;
            } else {
                System.err.println("等待超时的请求:" + future.getRequest());
                return;
            }

        }

        /*
         * 滑动窗口响应请求
         */
        @SuppressWarnings("unchecked")
        public Message handlerRequest(Message request, ChannelHandlerContext ctx) {
            // 入滑动窗口超时、滑动窗口内超时、客户端调用超时 毫秒
            int windowWaitOffset = 30000;
            int windowExpiration = 30000;
            int invokeOffset = 30000;
            int id = request.getId();
            WindowFuture<Integer, Message, Message> reqFuture = null;
            try {
                reqFuture = window.offer(id, request, windowWaitOffset, windowExpiration);
            } catch (DuplicateKeyException e) {
                System.out.println("重复调用！" + e);
                return null;
            } catch (OfferTimeoutException e) {
                System.out.println("滑动窗口请求超时！" + e);
                return null;
            } catch (InterruptedException e) {
                System.out.println("滑动窗口过程中被中断!" + e);
                return null;
            }
            System.out.println("滑动窗口发送的数据:" + request.toString());
            ChannelFuture future = ctx.writeAndFlush(request.toString());
            future.awaitUninterruptibly();
            if (future.isCancelled()) {// 被中断
                try {
                    // 取消该id的数据
                    window.cancel(id);
                } catch (InterruptedException e) {
                    // IGNORE
                }
                System.out.println("调用失败，被用户中断!");
            } else if (!future.isSuccess()) {// 失败，遇到异常
                try {
                    window.cancel(id);
                } catch (InterruptedException e) {
                }
                Throwable cause = future.cause();
                System.out.println("无法将数据正确写到Channel,Channel可能被关闭" + cause);
            } else {// 正常
                try {
                    reqFuture.await(invokeOffset);
                } catch (InterruptedException e) {
                    System.out.println("调用过程中被用户中断!-->消息可能已经发送到服务端" + e);
                }
                // 成功返回正确的响应
                if (reqFuture.isSuccess()) {
                    return reqFuture.getResponse();
                }
                try {
                    window.cancel(id);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            return null;
        }
    }


    /**
     * Netty创建全部都是实现自AbstractBootstrap。 客户端的是Bootstrap，服务端的则是 ServerBootstrap。
     **/
    public static void main(String[] args) {
        NettyServer nettyServer = new NettyServer();
        nettyServer.run();
    }

}
