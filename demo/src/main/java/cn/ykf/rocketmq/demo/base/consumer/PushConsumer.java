package cn.ykf.rocketmq.demo.base.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * 消息消费者 - push模式，负载均衡模式
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class PushConsumer {

    public static void main(String[] args) throws MQClientException {
        // 创建消费者，使用push模式
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("BaseConsumer");
        // 指定NameServer
        consumer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 订阅Topic
        consumer.subscribe("BASE_MSG", "*");
        // 使用负载均衡模式（可以不用指定，默认就是）
        consumer.setMessageModel(MessageModel.CLUSTERING);
        // 注册消费消息的回调函数，使用并发消费模式，还有顺序消费模式 MessageListenerOrderly
        consumer.registerMessageListener((new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                System.out.println(messages.size());
                for (MessageExt msg : messages) {
                    try {
                        // 消费消息
                        System.out.printf("%s 【msg】：%s，【body】：%s %n", Thread.currentThread().getName(),
                                msg, new String(msg.getBody(), RemotingHelper.DEFAULT_CHARSET));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        // 稍后消费
                        // todo 有个疑惑，这里在for循环中返回稍后消费，不会影响到list中的其他消息吗？还是每次都是一条消息
                        // 现在是单条消息多次推送
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        }));

        // 启动消费者
        consumer.start();
    }
}
