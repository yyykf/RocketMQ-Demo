package cn.ykf.rocketmq.demo.batch.producer;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 批量消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020-12-20
 */
public class BatchProducer {

    public static void main(String[] args) throws MQClientException {
        // 创建生产者，指定生产者组
        DefaultMQProducer producer = new DefaultMQProducer("BatchProducer");
        // 指定NameServer
        producer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 启动生产者
        producer.start();

        // 创建消息，指定Topic、Tag以及消息体
        Message msg1 = new Message("BATCH_MSG", "tag1", "key1", "Hello Batch Message 1".getBytes());
        Message msg2 = new Message("BATCH_MSG", "tag2", "key2", "Hello Batch Message 2".getBytes());
        Message msg3 = new Message("BATCH_MSG", "tag3", "key3", "Hello Batch Message 3".getBytes());
        List<Message> messages = Arrays.asList(msg1, msg2, msg3);

        // 批量发送，批量发送的消息总大小不能超过1M
        ListSplitter splitter = new ListSplitter(messages);
        while (splitter.hasNext()) {
            try {
                // 批量消息只是对于生产者来说是批量发送，但是与消费者是多次收到还是一次收到无关，这个取决于消费者自己设置的推送大小
                // 如果消费者设置了推送大小，那么生产者用同步发送也好，批量发送也好，当提前发送到broker堆积时，消费者一启动就可以收到多条消息了
                SendResult result = producer.send(splitter.next());
                System.out.println("发送结果：" + result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 关闭生产者
        producer.shutdown();
    }

    /**
     * 用于分割批量消息集合的迭代器，避免发送总大小大于1M
     */
    static class ListSplitter implements Iterator<List<Message>> {
        /** 允许的总大小，1MB */
        private final int SIZE_LIMIT = 1024 * 1024;
        /** 待切割的消息集合 */
        private final List<Message> messages;
        /** 当前迭代器索引 */
        private int currIndex;

        ListSplitter(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public boolean hasNext() {
            return currIndex < messages.size();
        }

        @Override
        public List<Message> next() {
            int nextIndex = currIndex;
            int totalSize = 0;
            for (; nextIndex < messages.size(); nextIndex++) {
                Message message = messages.get(nextIndex);
                int tmpSize = message.getTopic().length() + message.getBody().length;
                Map<String, String> properties = message.getProperties();
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    tmpSize += entry.getKey().length() + entry.getValue().length();
                }
                // 增加日志的开销20字节
                tmpSize = tmpSize + 20;
                if (tmpSize > SIZE_LIMIT) {
                    //单个消息超过了最大的限制
                    //忽略,否则会阻塞分裂的进程
                    if (nextIndex - currIndex == 0) {
                        //假如下一个子列表没有元素,则添加这个子列表然后退出循环,否则只是退出循环
                        nextIndex++;
                    }
                    break;
                }
                if (tmpSize + totalSize > SIZE_LIMIT) {
                    break;
                } else {
                    totalSize += tmpSize;
                }

            }
            List<Message> subList = messages.subList(currIndex, nextIndex);
            currIndex = nextIndex;
            return subList;
        }
    }
}
