package l.ys;

import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * https://www.cnblogs.com/wzj4858/p/8205587.html
 * https://github.com/waylau/netty-4-user-guide-demos/blob/master/netty4-demos/src/main/java/com/waylau/java/demo/buffer/ByteBufferDemo.java
 */
public class ByteBufferTest {

    @Test
    public void testMalloc() {
        // 创建一个缓冲区 final byte[] hb;  hb[0]=108
        ByteBuffer buffer = ByteBuffer.allocate(10);
        System.out.println("------------初始时缓冲区------------");
        printBuffer(buffer);
        // 添加一些数据到缓冲区中
        System.out.println("------------添加数据到缓冲区------------");
        String s = "love";
        buffer.put(s.getBytes());
        printBuffer(buffer);
        // 切换成读模式
        System.out.println("------------执行flip切换到读取模式------------");
        buffer.flip();
        printBuffer(buffer);
        // 读取数据
        System.out.println("------------读取数据------------");
        // 创建一个limit()大小的字节数组(因为就只有limit这么多个数据可读)
        byte[] bytes = new byte[buffer.limit()];
        // 将读取的数据装进我们的字节数组中
        buffer.get(bytes);
        printBuffer(buffer);
        // 执行compact(紧凑)
        System.out.println("------------执行compact------------");
        buffer.compact();
        printBuffer(buffer);
        // 执行clear
        System.out.println("------------执行clear清空缓冲区------------");
        buffer.clear();
        printBuffer(buffer);
    }

    // 假设我们又添加一个数据，但是我们没有读取，分析一下compact与clear的区别
    @Test
    public void testCompactAndClear() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(10);
        printBuffer(buffer);
        buffer.put("hello".getBytes());
        buffer.put("world".getBytes());
        printBuffer(buffer);
        buffer.flip();
        byte[] dst = new byte[5];
        buffer.get(dst, 0, dst.length);
        printBuffer(buffer);
        buffer.compact();// 如果数据没有读完，是不会清理position的。
        printBuffer(buffer);
        buffer.clear();
        printBuffer(buffer);
    }


    /**
     * 打印ByteBuffer的信息
     *
     * @param buffer
     */
    private void printBuffer(ByteBuffer buffer) {
        System.out.println("mark：" + buffer.mark());
        System.out.println("position：" + buffer.position());
        System.out.println("limit：" + buffer.limit());
        System.out.println("capacity：" + buffer.capacity());
    }
}
