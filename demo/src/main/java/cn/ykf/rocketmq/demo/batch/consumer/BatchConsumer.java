package cn.ykf.rocketmq.demo.batch.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 批量消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class BatchConsumer {

    public static void main(String[] args) throws MQClientException {
        // 创建消费者，使用push模式
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("BaseConsumer");
        // 指定NameServer
        consumer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 订阅Topic
        consumer.subscribe("BATCH_MSG", "*");
        /*
         * org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
         * private int consumeMessageBatchMaxSize = 1;
         * broker默认推送1条，所以生产者即使一次性发送批量消息，消费者也会收到多次回调
         */
        consumer.setConsumeMessageBatchMaxSize(10);
        // 注册消费消息的回调函数，使用并发消费模式，还有顺序消费模式 MessageListenerOrderly
        consumer.registerMessageListener(((MessageListenerConcurrently) (messages, consumeConcurrentlyContext) -> {
            System.out.println("Messages Size():" + messages.size());

            for (MessageExt msg : messages) {
                // 消费消息
                System.out.printf("%s 【msg】：%s，【body】：%s %n", Thread.currentThread().getName(),
                        msg, new String(msg.getBody()));
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }));

        // 启动消费者
        consumer.start();
    }
}
