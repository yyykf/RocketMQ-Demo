package cn.ykf.rocketmq.demo.filter.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;

/**
 * 过滤消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class FilterConsumer {

    public static void main(String[] args) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("FilterConsumer");
        consumer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 通过tag过滤，这是第一种简单的方式，无法满足复杂的要求
        // consumer.subscribe("FILTER_MSG", "tag1 || tag2 || tag3");

        // 第二种方式就是通过sql过滤，只能在push模式下使用
        consumer.subscribe("FILTER_MSG", MessageSelector.bySql("i <= 4"));
        // 添加监听
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            messages.forEach(System.out::println);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
    }
}
