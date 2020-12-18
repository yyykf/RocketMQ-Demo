package cn.ykf.rocketmq.demo.order.producer;

import cn.ykf.rocketmq.demo.order.dto.OrderStep;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * 有序消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class OrderProducer {

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException, RemotingException, InterruptedException, MQBrokerException {
        // 创建生产者，指定生产者组
        DefaultMQProducer producer = new DefaultMQProducer("OrderProducer");
        // 指定NameServer
        producer.setNamesrvAddr("172.16.61.100:9876;172.16.61.101:9876");
        // 启动生产者
        producer.start();

        // 模拟数据
        List<OrderStep> orderSteps = OrderStep.buildOrderSteps();
        int keyIndex = 0;
        // 逐条发送消息
        for (OrderStep orderStep : orderSteps) {
            // 时间戳
            long timeStamp = System.currentTimeMillis();
            // 消息内容
            String content = timeStamp + " Hello Order Message " + orderStep;
            Message message = new Message("ORDER_MSG", "TagA", "KEY" + keyIndex++, content.getBytes(RemotingHelper.DEFAULT_CHARSET));

            // 发送有序消息（局部有序，在对应消息队列中有效，Broker默认会有4个消息队列）
            SendResult result = producer.send(message, new MessageQueueSelector() {
                /**
                 * 消息选择器，用于选择对应的消息队列
                 *
                 * @param list    消息队列集合
                 * @param message 消息
                 * @param arg     业务参数，一般用来选择队列
                 * @return 对应的消息队列
                 */
                @Override
                public MessageQueue select(List<MessageQueue> list, Message message, Object arg) {
                    // 这里的arg也就是传递进来的orderId
                    return list.get((int) ((long) arg % list.size()));
                }
            }, orderStep.getOrderId());

            System.out.println("发送结果：" + result);
        }

        // 关闭生产者
        producer.shutdown();
    }
}
