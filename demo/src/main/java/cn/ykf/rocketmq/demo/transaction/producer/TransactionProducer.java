package cn.ykf.rocketmq.demo.transaction.producer;

import cn.ykf.rocketmq.demo.transaction.listener.DefaultTransactionListener;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 事务消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class TransactionProducer {

    public static void main(String[] args) throws MQClientException {
        // 事务消息保证本地事务和消息的发送以原子方式进行，不支持延迟消息和批量消息
        // todo 但是如果支付流程是：支付成功-扣减库存-通知物流中心发货，那么支付的本地事务成功，并且发送了扣减库存的消息
        // 如果扣减库存失败的话，事务回滚，那么支付成功的事务怎么办？
        // 猜测：producer端一旦事务成功并且成功发送消息，那么就认为成功了，consumer端无论成功失败都不能影响producer的事务
        // 如果consumer端扣减库存失败，那么应该producer消息补偿？或者多次补偿后不成功则人工修正？
        // todo 了解一下分布式事务
        TransactionMQProducer producer = new TransactionMQProducer("TransactionProducer");
        producer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 设置事务监听器
        producer.setTransactionListener(new DefaultTransactionListener());
        // 设置用于执行回查请求的线程池，执行本地事务不会使用该线程池
        producer.setExecutorService(new ThreadPoolExecutor(2, 5, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2000), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("client-transaction-msg-check-thread");
            return thread;
        }));
        // 启动生产者
        producer.start();

        String[] tags = {"tag1", "tag2", "tag3"};
        for (int i = 0; i < 3; i++) {
            Message msg = new Message("TRANSACTION_MSG", tags[i], ("Hello Transaction Message " + i + "!").getBytes());
            // todo 优先级比Broker配置参数transactionTimeout高，这个配置代表什么？执行事务的超时时间？？
            msg.putUserProperty(MessageConst.PROPERTY_CHECK_IMMUNITY_TIME_IN_SECONDS, "1");

            // 发送事务消息，此时发送的是half消息，如果broker成功收到并返回SEND_OK，那么将会回调监听器的本地事务执行方法，第二个参数是本地事务执行所需的参数
            TransactionSendResult result = producer.sendMessageInTransaction(msg, i);
            System.out.println("half消息发送结果：" + result);
        }

        // 不能关闭生产者，防止无法回调监听器
    }
}
