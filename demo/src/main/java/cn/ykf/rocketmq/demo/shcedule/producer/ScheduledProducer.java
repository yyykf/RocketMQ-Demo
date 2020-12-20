package cn.ykf.rocketmq.demo.shcedule.producer;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * 延迟消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020-12-20
 */
public class ScheduledProducer {

    public static void main(String[] args) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        // 创建生产者
        DefaultMQProducer producer = new DefaultMQProducer("ScheduledProducer");
        // 指定NameServer
        producer.setNamesrvAddr("192.168.72.200:9876;192.168.72.201:9876");
        // 启动生产者
        producer.start();

        for (int i = 0; i < 10; i++) {
            Message message = new Message("ScheduleTopic", "tag", "Hello Scheduled Message!".getBytes());
            // 设置延迟5s，默认18个等级，对应1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
            message.setDelayTimeLevel(2);

            SendResult result = producer.send(message);
            System.out.println("Send Result: " + result);
        }

        // 关闭消费者
        producer.shutdown();
    }
}
