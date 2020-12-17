package cn.ykf.rocketmq.demo.base.producer;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 同步消息发送者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/17
 */
public class BaseProducer {

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException, RemotingException, InterruptedException, MQBrokerException {
        // 创建生产者，指定生产者组
        DefaultMQProducer producer = new DefaultMQProducer("BaseProducer");
        // 指定NameServer
        producer.setNamesrvAddr("172.16.61.100:9876;172.16.61.101:9876");
        // 启动生产者
        producer.start();

        for (int i = 0; i < 10; i++) {
            // 创建消息，指定Topic、Tag以及消息体
            Message message = new Message("BASE_MSG", "tag1", ("Hello World" + i).getBytes(StandardCharsets.UTF_8.name()));
            // 发送同步消息
            SendResult result = producer.send(message);

            System.out.println("发送结果：" + result);

            TimeUnit.MILLISECONDS.sleep(500);
        }

        // 关闭生产者
        producer.shutdown();
    }
}
