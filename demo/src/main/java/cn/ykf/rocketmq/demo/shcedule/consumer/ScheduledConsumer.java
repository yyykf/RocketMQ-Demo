package cn.ykf.rocketmq.demo.shcedule.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 延迟消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020-12-20
 */
public class ScheduledConsumer {

    public static void main(String[] args) throws MQClientException {
        // 创建消费者
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("ScheduledConsumer");
        // 指定NameServer
        consumer.setNamesrvAddr("192.168.72.200:9876;192.168.72.201:9876");
        // 订阅Topic
        consumer.subscribe("ScheduleTopic", "*");
        // 注册监听
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt msg : messages) {
                System.out.println("Receive message[msgId=" + msg.getMsgId() + "] "
                        + (System.currentTimeMillis() - msg.getStoreTimestamp()) + "ms later");
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        // 启动消费者
        consumer.start();
    }
}
