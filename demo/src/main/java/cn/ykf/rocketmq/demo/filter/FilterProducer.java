package cn.ykf.rocketmq.demo.filter;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;


/**
 * 过滤消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class FilterProducer {

    public static void main(String[] args) throws MQClientException, RemotingException, InterruptedException, MQBrokerException {
        DefaultMQProducer producer = new DefaultMQProducer("FilterProducer");
        producer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        producer.start();

        for (int i = 0; i < 10; i++) {
            Message msg = new Message("FILTER_MSG", "tag" + i, "Hello Filter Message!".getBytes());
            // 添加用户属性，用于消费者过滤消息
            msg.putUserProperty("i", String.valueOf(i));

            SendResult result = producer.send(msg);
            System.out.println("发送结果：" + result);
        }

        producer.shutdown();
    }
}
