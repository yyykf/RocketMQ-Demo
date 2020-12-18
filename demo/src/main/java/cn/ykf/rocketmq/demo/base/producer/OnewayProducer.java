package cn.ykf.rocketmq.demo.base.producer;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

/**
 * 单向消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class OnewayProducer {

    public static void main(String[] args) throws RemotingException, MQClientException, InterruptedException, UnsupportedEncodingException {
        // 创建生产者，指定生产者组
        DefaultMQProducer producer = new DefaultMQProducer("BaseProducer");
        // 指定NameServer
        producer.setNamesrvAddr("172.16.61.100:9876;172.16.61.101:9876");
        // 启动生产者
        producer.start();

        for (int i = 0; i < 10; i++) {
            // 创建消息，指定Topic、Tag以及消息体
            Message message = new Message("BASE_MSG", "tag3", ("Hello World" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            System.out.println("发送第" + (i + 1) + "条单向消息...");
            // 发送单向消息
            producer.sendOneway(message);
        }

        // 关闭生产者
        producer.shutdown();
    }
}
