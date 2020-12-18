package cn.ykf.rocketmq.demo.order.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单步骤
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class OrderStep {

    /** 订单id */
    private long orderId;
    /** 描述 */
    private String desc;

    /**
     * 生成模拟订单数据
     *
     * @return 模拟订单数据
     */
    public static List<OrderStep> buildOrderSteps() {
        List<OrderStep> orderList = new ArrayList<>();

        orderList.add(new OrderStep(1L, "创建"));
        orderList.add(new OrderStep(2L, "创建"));
        orderList.add(new OrderStep(1L, "付款"));
        orderList.add(new OrderStep(3L, "创建"));
        orderList.add(new OrderStep(2L, "付款"));
        orderList.add(new OrderStep(3L, "付款"));
        orderList.add(new OrderStep(2L, "完成"));
        orderList.add(new OrderStep(1L, "推送"));
        orderList.add(new OrderStep(3L, "完成"));
        orderList.add(new OrderStep(1L, "完成"));

        return orderList;
    }

    public OrderStep() {
    }

    public OrderStep(long orderId, String desc) {
        this.orderId = orderId;
        this.desc = desc;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "OrderStep{" +
                "orderId=" + orderId +
                ", desc='" + desc + '\'' +
                '}';
    }
}
