package cn.ykf.rocketmq.demo.order.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.io.UnsupportedEncodingException;

/**
 * 顺序消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class OrderConsumer {

    public static void main(String[] args) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("OrderConsumer");
        consumer.setNamesrvAddr("172.16.61.100:9876;172.16.61.101:9876");
        // 第一次启动从队列头开始消费，包含历史消息，如果非第一次启动，那么从上次消费的位置继续消费
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 订阅Topic
        consumer.subscribe("ORDER_MSG", "*");
        // 注册监听器
        consumer.registerMessageListener((MessageListenerOrderly) (messages, consumeOrderlyContext) -> {
            // 自动提交
            consumeOrderlyContext.setAutoCommit(true);

            for (MessageExt msg : messages) {
                // 消费消息
                try {
                    System.out.printf("%s 【msg】：%s，【body】：%s %n", Thread.currentThread().getName(),
                            msg, new String(msg.getBody(), RemotingHelper.DEFAULT_CHARSET));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }

            }

            return ConsumeOrderlyStatus.SUCCESS;
        });

        // 启动消费者
        consumer.start();
    }
}
