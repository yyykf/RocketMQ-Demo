package cn.ykf.rocketmq.demo.transaction.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;

/**
 * 事务消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class TransactionConsumer {

    public static void main(String[] args) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("TransactionConsumer");
        consumer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        consumer.subscribe("TRANSACTION_MSG", "*");
        // 添加监听
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            messages.forEach(System.out::println);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
    }
}
