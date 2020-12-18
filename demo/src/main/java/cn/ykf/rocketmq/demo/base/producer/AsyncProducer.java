package cn.ykf.rocketmq.demo.base.producer;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

/**
 * 异步消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class AsyncProducer {

    public static void main(String[] args) throws MQClientException, InterruptedException, RemotingException, UnsupportedEncodingException {
        // 创建生产者，指定生产者组
        DefaultMQProducer producer = new DefaultMQProducer("BaseProducer");
        // 指定NameServer
        producer.setNamesrvAddr("172.16.61.100:9876;172.16.61.101:9876");
        // 启动生产者
        producer.start();

        for (int i = 0; i < 10; i++) {
            // 创建消息，指定Topic、Tag以及消息体
            Message message = new Message("BASE_MSG", "tag2", ("Hello World" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            // 发送异步，通过回调接口
            producer.send(message, new SendCallback() {
                /**
                 * 发送成功的回调函数
                 * @param sendResult 发送结果
                 */
                @Override
                public void onSuccess(SendResult sendResult) {
                    System.out.println("发送结果：" + sendResult);
                }

                /**
                 * 发送失败的回调函数
                 * @param throwable 发送失败的异常
                 */
                @Override
                public void onException(Throwable throwable) {
                    System.out.println("发送异常：" + throwable);
                }
            });


            TimeUnit.MILLISECONDS.sleep(500);
        }

        // 关闭生产者
        producer.shutdown();
    }
}
