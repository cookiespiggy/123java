package l.ys;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class ByteBufTest {

    @Test
    public void testMalloc() {
        // 创建一个缓冲区
        // UnpooledHeapByteBuf
        //      byte[] array;
        ByteBuf buf = Unpooled.buffer(10);
        System.out.println("------------初始时缓冲区------------");
        printBuf(buf);
        // 添加一些数据到缓冲区中
        System.out.println("------------添加数据到缓冲区------------");
        String s = "love";
        buf.writeBytes(s.getBytes());
        // array[0] 108
        printBuf(buf);
        // 读取数据
        System.out.println("------------读取数据------------");
        while (buf.isReadable()) {
            System.out.println(buf.readByte());
//            break; 与discardReadBytes有关  discard丢弃
        }
        printBuf(buf);
        // 执行compact
        System.out.println("------------执行discardReadBytes------------");
        buf.discardReadBytes();
        printBuf(buf);

        // 执行clear
        System.out.println("------------执行clear清空缓冲区------------");
        buf.clear();
        printBuf(buf);
    }

    /**
     * 打印出ByteBuf的信息
     *
     * @param buf
     */
    private void printBuf(ByteBuf buf) {
        System.out.println("readerIndex：" + buf.readerIndex());
        System.out.println("writerIndex：" + buf.writerIndex());
        System.out.println("capacity：" + buf.capacity());
    }
}
