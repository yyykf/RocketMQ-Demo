package cn.ykf.rocketmq.demo.transaction.listener;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;

/**
 * 事务消息监听器
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class DefaultTransactionListener implements TransactionListener {

    private long startTime;

    /**
     * 用于执行本地事务
     * 返回 {@code LocalTransactionState.COMMIT_MESSAGE} broker 才会将已经收到的half消息给消费者消费
     * 返回 {@code LocalTransactionState.ROLLBACK_MESSAGE} broker 将丢弃该消息，无法用于消费者消费
     * 返回 {@code LocalTransactionState.UNKNOWN} broker 将经过一定时间后调用该监听器的回查方法，根据返回状态决定如何处理消息
     *
     * @param msg 已经成功发送到 broker 的half消息
     * @param arg 本地事务执行所需的参数
     * @return 上述三种状态之一
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        System.out.printf("%s 发送成功的half消息：%s，事务参数：%s%n", Thread.currentThread().getName(), msg, arg);
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (Objects.equals(msg.getTags(), "tag1")) {
            // 事务执行成功，half消息可以被消费者消费
            return LocalTransactionState.COMMIT_MESSAGE;
        } else if (Objects.equals(msg.getTags(), "tag2")) {
            // 事务执行失败，half消息将被删除
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }

        // 事务状态需要回查，记录回查间隔
        startTime=System.currentTimeMillis();
        return LocalTransactionState.UNKNOW;
    }

    /**
     * 回查本地事务执行结果，如果执行本地事务时返回了 {@code LocalTransactionState.UNKNOWN}
     * 或者返回 {@code LocalTransactionState.COMMIT_MESSAGE}、{@code LocalTransactionState.ROLLBACK_MESSAGE} 时由于
     * 网络波动在指定时间内没有传输到 broker，那么broker就会进行回查，判断该本地事务是否已经执行完毕
     * <p>
     * 默认情况下，最多回查15次，对应Broker配置参数transactionCheckMax，超过该次数，丢弃消息并且记录错误到日志中
     * 默认经过60s回查，通过Broker配置参数transactionCheckInterval设置
     * todo 可以通过用户自定义属性CHECK_IMMUNITY_TIME_IN_SECONDS覆盖，存疑
     *
     * @param msg 待回查的half消息
     * @return 上述三种状态之一
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        System.out.printf("%s 经过 %s ms回查，回查消息： %s%n", Thread.currentThread().getName(), System.currentTimeMillis() - startTime, msg);
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return LocalTransactionState.COMMIT_MESSAGE;
    }
}
