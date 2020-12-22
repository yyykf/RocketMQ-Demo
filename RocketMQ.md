## 0. 基本概述

- MQ的作用
  - 应用解耦
    - 避免代码硬耦合，不用直接调用某个服务，而是将消息发送到MQ，由对应服务去消费就可以
    - 提高容错性，如果直接调用某个服务，那么如果对应服务不可用了，就会导致该系统也不可用
  - 流量削峰
    - 比如秒杀，qps为1w，可能会压垮数据库，那么就可以先把请求发送到MQ中（MQ的性能更好），然后由消费端分批拉取消息，逐渐消费（比如每秒拉取2k个消息，分5秒消费完）
  - 数据分发
    - 使用MQ让数据在多个系统中流通，数据的生产者无需关心哪个系统需要，直接扔到MQ中，需要的系统自己订阅，不需要的系统就取消订阅，不用去修改代码，和应用解耦一个道理
- MQ的缺点
  - 提供了系统的复杂度
    - 以前是同步调用，现在是通过MQ异步调用。如何保证消息没有被重复消费？消息丢失怎么办？消息传递的顺序性怎么办？
  - 系统可用性降低
    - 如果MQ不可用了，怎么办?
  - 一致性问题
    - 生产者做了某些处理，等待消费者做后续的处理，但是消费者消费失败了，那么生产者做过的处理怎么办？
    - 或者A系统处理完业务，给B、C系统发送了消息，B系统消费成功，C系统消费失败，怎么办，如何保证消息数据处理的一致性?

- 简单入门
  - 先修改配置文件，因为RocketMq默认所需JVM内存很大，有可能导致启动失败

    ```sh
    vim bin/runbroker.sh
    vim bin/runserver.sh
    ## 参考配置
    JAVA_OPT="${JAVA_OPT} -server -Xms256m -Xmx256m -Xmn128m -XX:MetaspaceSize=128m  -XX:MaxMetaspaceSize=320m"
    ```

  - 启动NameServer

    ```sh
    ## 进入rocketmq安装目录，启动NameServer
    nohup sh bin/mqnamesrv &
    ## 查看启动日志
    tail -f ~/logs/rocketmqlogs/namesrv.log
    ```

  - 启动Broker

    ```bash
    ## 指定NameServer
    nohup sh bin/mqbroker -n localhost:9876 &
    ## 查看启动日志
    tail -f ~/logs/rocketmqlogs/broker.log
    ```

  - 关闭RocketMQ

    ```sh
    ## 关闭NameServer
    sh bin/mqshutdown namesrv
    ## 关闭Broker
    sh bin/mqshutdown broker
    ```

## 1. 专业名词介绍

- Producer：消息发送者
- Consumer：消息接收者
- Broker：暂存和传输消息
- NameServer：管理Broker，相当于路由，向Consumer和Producer提供对应Topic的Broker地址
- Topic：区分消息的种类。Producer可以指定一个或多个要发送的Topic，Consumer可以订阅一个或多个要消费的Topic
- Message Queue：Topic 的分区，一个Topic的消息可以分散在多个Queue

## 2. 集群搭建

### 2.1 集群工作方式

- NameServer集群中的每个节点都是独立的，**不会进行数据的同步**。

- Producer集群中的每个节点随机向NameServer集群中的一个结点建立长链接，用于从NameServer拉取要发送的Topic对应的Broker信息，**并且向Master Broker建立长链接**，定时向其发送心跳

- Consumer集群中的每个节点随机向NameServer集群中的一个结点建立长链接，用于从NameServer拉取要消费的Topic对应的Broker信息，**并且向Master Broker、Slave Broker建立长链接**，定时向其发送心跳。**Consumer可以从Master订阅消息，也可以从Slave订阅消息，订阅规则由Broker指定**

- Broker集群中，Master和Slave是一对多的关系，通过指定相同的BrokerName来建立关系，通过BrokerId来建立主从关系，id为0是Master，否则为Slave。**每个Broker都会和NameServer集群中的每个节点建立长链接，并且定时注册Topic信息到所有NameServer结点中，因此NameServer每个节点之前不需要数据的同步**。Broker主从结点之间需要数据的同步（异步或同步）

  > 会不会注册到第一个NameServer之后，Broker挂掉了，导致没有注册到其他的NameServer结点，因此出现了NameServer各结点之间的数据不一致？

### 2.2 双主双从集群工作流程

- NameServer最先启动，因为是路由控制中心，等待Broker、Producer、Consumer来连接

- 启动Broker，**跟所有的NameServer建立长链接**，定时发送心跳包，包含IP、Port以及存储的Topic信息。注册到NamerServer后，NamerServer就会建立起Topic和Broker的映射关系

- 创建Topic，指定该Topic要存储在哪些Broker上，也可以发送消息时自动创建Topic

  > 疑问：如果自动创建Topic的话，也就是发送消息之前没有Broker拥有该Topic，那么Topic会随机存储到某个Broker吗？然后NameServer再把随机存储到的Broker返回给Producer？
  >
  > 
  >
  > 好像是随机选择一个主Broker进行创建Topic，然后主从之间进行同步Topic，然后再各自注册到所有的NameServer，最后返回Master给Producer
  >
  > https://blog.csdn.net/gameloft9/article/details/99600973

  - broker.log
    - ![image-20201217174705925](https://typora-pics-1255993109.cos.ap-guangzhou.myqcloud.com/image-20201217174705925.png)
  - namesrv.log
    - ![image-20201217175409578](https://typora-pics-1255993109.cos.ap-guangzhou.myqcloud.com/image-20201217175409578.png)

- Producer先和任一NameServer结点建立长链接，获取当前要发送的Topic存在哪些Broker上，轮询从队列列表中选择一个队列，然后与队列所在的Broker建立长链接，就可以开始发送消息了

- Consumer先和任一NameServer结点建立长连接，获取订阅的Topic存在哪些Broker上，然后直接和Broker建立连接，开始消费消息

### 2.3 配置流程

#### 2.3.1 环境

- 采用双主双从，只用两台服务器，内存充裕的话可以用4台，主从都分开

| **序号** | **IP**        | **角色**                 | **架构模式**    |
| -------- | ------------- | ------------------------ | --------------- |
| 1        | 172.16.61.100 | nameserver、brokerserver | Master1、Slave2 |
| 2        | 172.16.61.101 | nameserver、brokerserver | Master2、Slave1 |

#### 2.3.2 配置Host

```sh
vim /etc/hosts
## 配置后需要重启网卡
service network restart
## 或者
systemctl restart network
```

```sh
## nameserver
172.16.61.100 rocketmq-nameserver1
172.16.61.101 rocketmq-nameserver2
## broker
172.16.61.100 rocketmq-master1
172.16.61.100 rocketmq-slave2
172.16.61.101 rocketmq-master2
172.16.61.101 rocketmq-slav01
```

#### 2.3.3 防火墙配置

- 学习环境直接关闭防火墙

```sh
systemctl stop firewalld.service
systemctl disable firewalld.service
```

- 生产环境应该开放特定端口，RocketMQ默认端口为：9876、10911、11011
  - `nameserver` 默认使用 9876 端口
  - `master` 默认使用 10911 端口
  - `slave` 默认使用11011 端口

```sh
# 开放name server默认端口
firewall-cmd --remove-port=9876/tcp --permanent
# 开放master默认端口
firewall-cmd --remove-port=10911/tcp --permanent
# 开放slave默认端口（当前集群模式可不开启）
firewall-cmd --remove-port=11011/tcp --permanent
# 重新防火墙
firewall-cmd --reload
```

#### 2.3.4 配置环境变量

```sh
[root@mq02 rocketmq-all-4.4.0-bin-release]# pwd
/opt/rocketmq-all-4.4.0-bin-release
[root@mq02 rocketmq-all-4.4.0-bin-release]# vim /etc/profile
[root@mq02 rocketmq-all-4.4.0-bin-release]# source /etc/profile
```

```sh
## ROCKETMQ ENVIROMENT
export ROCKETMQ_HOME=/opt/rocketmq-all-4.4.0-bin-release
export PATH=$PATH:${ROCKETMQ_HOME}/bin
```

#### 2.3.5 创建消息存储目录

- 进入MQ安装目录

```sh
## Master的存储路径
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store/commitlog
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store/consumequeue
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store/index
## Slave的存储路径，不能共用，一般不会在同一机器
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store-slave
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store-slave/commitlog
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store-slave/consumequeue
[root@mq01 rocketmq-all-4.4.0-bin-release]# mkdir store-slave/index
```

#### 2.3.6 broker配置文件

- Master1（172.16.61.100）

```sh
vim $ROCKETMQ_HOME/conf/2m-2s-sync/broker-a.properties
```

```properties
#所属集群名字
brokerClusterName=rocketmq-cluster
#broker名字，注意此处不同的配置文件填写的不一样
brokerName=broker-a
#0 表示 Master，>0 表示 Slave
brokerId=0
#nameServer地址，分号分割
namesrvAddr=rocketmq-nameserver1:9876;rocketmq-nameserver2:9876
#在发送消息时，自动创建服务器不存在的topic，默认创建的队列数
defaultTopicQueueNums=4
#是否允许 Broker 自动创建Topic，建议线下开启，线上关闭
autoCreateTopicEnable=true
#是否允许 Broker 自动创建订阅组，建议线下开启，线上关闭
autoCreateSubscriptionGroup=true
#Broker 对外服务的监听端口
listenPort=10911
#删除文件时间点，默认凌晨 4点
deleteWhen=04
#文件保留时间，默认 48 小时
fileReservedTime=120
#commitLog每个文件的大小默认1G
mapedFileSizeCommitLog=1073741824
#ConsumeQueue每个文件默认存30W条，根据业务情况调整
mapedFileSizeConsumeQueue=300000
#destroyMapedFileIntervalForcibly=120000
#redeleteHangedFileInterval=120000
#检测物理文件磁盘空间
diskMaxUsedSpaceRatio=88
#存储路径
storePathRootDir=/opt/rocketmq-all-4.4.0-bin-release/store
#commitLog 存储路径
storePathCommitLog=/opt/rocketmq-all-4.4.0-bin-release/store/commitlog
#消费队列存储路径存储路径
storePathConsumeQueue=/opt/rocketmq-all-4.4.0-bin-release/store/consumequeue
#消息索引存储路径
storePathIndex=/opt/rocketmq-all-4.4.0-bin-release/store/index
#checkpoint 文件存储路径
storeCheckpoint=/opt/rocketmq-all-4.4.0-bin-release/store/checkpoint
#abort 文件存储路径
abortFile=/opt/rocketmq-all-4.4.0-bin-release/store/abort
#限制的消息大小
maxMessageSize=65536
#flushCommitLogLeastPages=4
#flushConsumeQueueLeastPages=2
#flushCommitLogThoroughInterval=10000
#flushConsumeQueueThoroughInterval=60000
#Broker 的角色
#- ASYNC_MASTER 异步复制Master
#- SYNC_MASTER 同步双写Master
#- SLAVE
brokerRole=SYNC_MASTER
#刷盘方式
#- ASYNC_FLUSH 异步刷盘
#- SYNC_FLUSH 同步刷盘
flushDiskType=SYNC_FLUSH
#checkTransactionMessageEnable=false
#发消息线程池数量
#sendMessageThreadPoolNums=128
#拉消息线程池数量
#pullMessageThreadPoolNums=128
```

- Slave2（172.16.61.100）

```sh
vim $ROCKETMQ_HOME/conf/2m-2s-sync/broker-b-s.properties
```

```properties
#所属集群名字
brokerClusterName=rocketmq-cluster
#broker名字，注意此处不同的配置文件填写的不一样
brokerName=broker-b
#0 表示 Master，>0 表示 Slave
brokerId=1
#nameServer地址，分号分割
namesrvAddr=rocketmq-nameserver1:9876;rocketmq-nameserver2:9876
#在发送消息时，自动创建服务器不存在的topic，默认创建的队列数
defaultTopicQueueNums=4
#是否允许 Broker 自动创建Topic，建议线下开启，线上关闭
autoCreateTopicEnable=true
#是否允许 Broker 自动创建订阅组，建议线下开启，线上关闭
autoCreateSubscriptionGroup=true
#Broker 对外服务的监听端口
listenPort=11011
#删除文件时间点，默认凌晨 4点
deleteWhen=04
#文件保留时间，默认 48 小时
fileReservedTime=120
#commitLog每个文件的大小默认1G
mapedFileSizeCommitLog=1073741824
#ConsumeQueue每个文件默认存30W条，根据业务情况调整
mapedFileSizeConsumeQueue=300000
#destroyMapedFileIntervalForcibly=120000
#redeleteHangedFileInterval=120000
#检测物理文件磁盘空间
diskMaxUsedSpaceRatio=88
#存储路径，注意在同一机器上时不要和master使用同一路径
storePathRootDir=/opt/rocketmq-all-4.4.0-bin-release/store-slave
#commitLog 存储路径
storePathCommitLog=/opt/rocketmq-all-4.4.0-bin-release/store-slave/commitlog
#消费队列存储路径存储路径
storePathConsumeQueue=/opt/rocketmq-all-4.4.0-bin-release/store-slave/consumequeue
#消息索引存储路径
storePathIndex=/opt/rocketmq-all-4.4.0-bin-release/store-slave/index
#checkpoint 文件存储路径
storeCheckpoint=/opt/rocketmq-all-4.4.0-bin-release/store-slave/checkpoint
#abort 文件存储路径
abortFile=/opt/rocketmq-all-4.4.0-bin-release/store-slave/abort
#限制的消息大小
maxMessageSize=65536
#flushCommitLogLeastPages=4
#flushConsumeQueueLeastPages=2
#flushCommitLogThoroughInterval=10000
#flushConsumeQueueThoroughInterval=60000
#Broker 的角色
#- ASYNC_MASTER 异步复制Master
#- SYNC_MASTER 同步双写Master
#- SLAVE
brokerRole=SLAVE
#刷盘方式
#- ASYNC_FLUSH 异步刷盘
#- SYNC_FLUSH 同步刷盘
flushDiskType=ASYNC_FLUSH
#checkTransactionMessageEnable=false
#发消息线程池数量
#sendMessageThreadPoolNums=128
#拉消息线程池数量
#pullMessageThreadPoolNums=128
```

- Master2（172.16.61.101）

```sh
vim $ROCKETMQ_HOME/conf/2m-2s-sync/broker-b.properties
```

```properties
#所属集群名字
brokerClusterName=rocketmq-cluster
#broker名字，注意此处不同的配置文件填写的不一样
brokerName=broker-b
#0 表示 Master，>0 表示 Slave
brokerId=0
#nameServer地址，分号分割
namesrvAddr=rocketmq-nameserver1:9876;rocketmq-nameserver2:9876
#在发送消息时，自动创建服务器不存在的topic，默认创建的队列数
defaultTopicQueueNums=4
#是否允许 Broker 自动创建Topic，建议线下开启，线上关闭
autoCreateTopicEnable=true
#是否允许 Broker 自动创建订阅组，建议线下开启，线上关闭
autoCreateSubscriptionGroup=true
#Broker 对外服务的监听端口
listenPort=10911
#删除文件时间点，默认凌晨 4点
deleteWhen=04
#文件保留时间，默认 48 小时
fileReservedTime=120
#commitLog每个文件的大小默认1G
mapedFileSizeCommitLog=1073741824
#ConsumeQueue每个文件默认存30W条，根据业务情况调整
mapedFileSizeConsumeQueue=300000
#destroyMapedFileIntervalForcibly=120000
#redeleteHangedFileInterval=120000
#检测物理文件磁盘空间
diskMaxUsedSpaceRatio=88
#存储路径
storePathRootDir=/opt/rocketmq-all-4.4.0-bin-release/store
#commitLog 存储路径
storePathCommitLog=/opt/rocketmq-all-4.4.0-bin-release/store/commitlog
#消费队列存储路径存储路径
storePathConsumeQueue=/opt/rocketmq-all-4.4.0-bin-release/store/consumequeue
#消息索引存储路径
storePathIndex=/opt/rocketmq-all-4.4.0-bin-release/store/index
#checkpoint 文件存储路径
storeCheckpoint=/opt/rocketmq-all-4.4.0-bin-release/store/checkpoint
#abort 文件存储路径
  abortFile=/opt/rocketmq-all-4.4.0-bin-release/store/abort
#限制的消息大小
maxMessageSize=65536
#flushCommitLogLeastPages=4
#flushConsumeQueueLeastPages=2
#flushCommitLogThoroughInterval=10000
#flushConsumeQueueThoroughInterval=60000
#Broker 的角色
#- ASYNC_MASTER 异步复制Master
#- SYNC_MASTER 同步双写Master
#- SLAVE
brokerRole=SYNC_MASTER
#刷盘方式
#- ASYNC_FLUSH 异步刷盘
#- SYNC_FLUSH 同步刷盘
flushDiskType=SYNC_FLUSH
#checkTransactionMessageEnable=false
#发消息线程池数量
#sendMessageThreadPoolNums=128
#拉消息线程池数量
#pullMessageThreadPoolNums=128
```

- Slave1（172.16.61.101）

```sh
vim /opt/rocketmq-all-4.4.0-bin-release/conf/2m-2s-sync/broker-a-s.properties
```

```properties
#所属集群名字
brokerClusterName=rocketmq-cluster
#broker名字，注意此处不同的配置文件填写的不一样
brokerName=broker-a
#0 表示 Master，>0 表示 Slave
brokerId=1
#nameServer地址，分号分割
namesrvAddr=rocketmq-nameserver1:9876;rocketmq-nameserver2:9876
#在发送消息时，自动创建服务器不存在的topic，默认创建的队列数
defaultTopicQueueNums=4
#是否允许 Broker 自动创建Topic，建议线下开启，线上关闭
autoCreateTopicEnable=true
#是否允许 Broker 自动创建订阅组，建议线下开启，线上关闭
autoCreateSubscriptionGroup=true
#Broker 对外服务的监听端口
listenPort=11011
#删除文件时间点，默认凌晨 4点
deleteWhen=04
#文件保留时间，默认 48 小时
fileReservedTime=120
#commitLog每个文件的大小默认1G
mapedFileSizeCommitLog=1073741824
#ConsumeQueue每个文件默认存30W条，根据业务情况调整
mapedFileSizeConsumeQueue=300000
#destroyMapedFileIntervalForcibly=120000
#redeleteHangedFileInterval=120000
#检测物理文件磁盘空间
diskMaxUsedSpaceRatio=88
#存储路径
storePathRootDir=/opt/rocketmq-all-4.4.0-bin-release/store
#commitLog 存储路径
storePathCommitLog=/opt/rocketmq-all-4.4.0-bin-release/store/commitlog
#消费队列存储路径存储路径
storePathConsumeQueue=/opt/rocketmq-all-4.4.0-bin-release/store/consumequeue
#消息索引存储路径
storePathIndex=/opt/rocketmq-all-4.4.0-bin-release/store/index
#checkpoint 文件存储路径
storeCheckpoint=/opt/rocketmq-all-4.4.0-bin-release/store/checkpoint
#abort 文件存储路径
abortFile=/opt/rocketmq-all-4.4.0-bin-release/store/abort
#限制的消息大小
maxMessageSize=65536
#flushCommitLogLeastPages=4
#flushConsumeQueueLeastPages=2
#flushCommitLogThoroughInterval=10000
#flushConsumeQueueThoroughInterval=60000
#Broker 的角色
#- ASYNC_MASTER 异步复制Master
#- SYNC_MASTER 同步双写Master
#- SLAVE
brokerRole=SLAVE
#刷盘方式
#- ASYNC_FLUSH 异步刷盘
#- SYNC_FLUSH 同步刷盘
flushDiskType=ASYNC_FLUSH
#checkTransactionMessageEnable=false
#发消息线程池数量
#sendMessageThreadPoolNums=128
#拉消息线程池数量
#pullMessageThreadPoolNums=128
```

#### 2.3.7 服务启动

- 启动NameServer集群

```sh
nohup sh mqnamesrv &
```

- 启动Broker集群

  - 172.16.61.100启动Master1和Slave2

  ```sh
  ## Master1
  nohup sh mqbroker -c $ROCKERMQ_HOME/conf/2m-2s-sync/broker-a.properties &
  ## Slave2
  nohup sh mqbroker -c $ROCKERMQ_HOME/conf/2m-2s-sync/broker-b-s.properties &
  ```

  - 172.16.61.101启动Master2和Slave1

  ```sh
  ## Master2
  nohup sh mqbroker -c $ROCKERMQ_HOME/conf/2m-2s-sync/broker-b.properties &
  ## Slave1
  nohup sh mqbroker -c $ROCKERMQ_HOME/conf/2m-2s-sync/broker-a-s.properties &
  ```

  ![image-20201217153755787](https://typora-pics-1255993109.cos.ap-guangzhou.myqcloud.com/image-20201217153755787.png)

- 查看日志

  ```sh
  tail -500f ~/logs/rocketmqlogs/namesrv.log
  tail -500f ~/logs/rocketmqlogs/broker.log
  ```

## 3. Demo

### 3.1 准备工作

- 导入依赖

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.4.0</version>
</dependency>
```

- 发送消息步骤

  > - 创建消息生产者producer，并指定生产者组名
  > - 指定Nameserver地址
  > - 启动producer
  > - 创建消息对象，指定主题Topic、Tag和消息体
  > - 发送消息
  > - 关闭生产者producer

- 接受消息步骤

  > - 创建消费者Consumer，指定消费者组名
  > - 指定Nameserver地址
  > - 订阅主题Topic和Tag
  > - 设置回调函数，处理消息
  > - 启动消费者consumer

### 3.2 发送同步消息

```java
package cn.ykf.rocketmq.demo.base.producer;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
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
            Message message = new Message("BASE_MSG", "tag1", ("Hello World" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            // 发送同步消息
            SendResult result = producer.send(message);

            System.out.println("发送结果：" + result);

            TimeUnit.MILLISECONDS.sleep(500);
        }

        // 关闭生产者
        producer.shutdown();
    }
}
```

### 3.3 发送异步消息

- 立刻返回，通过回调获取发送结果

```java
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
        // 发送失败不重试
        producer.setRetryTimesWhenSendAsyncFailed(0);
        // 启动生产者
        producer.start();

        for (int i = 0; i < 10; i++) {
            // 创建消息，指定Topic、Tag以及消息体
            Message message = new Message("BASE_MSG", "tag2", ("Hello World" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            // 发送异步，通过回调接口
            producer.send(message, new SendCallback() {
                /**
                 * 发送成功的回调函数
                 *
                 * @param sendResult 发送结果
                 */
                @Override
                public void onSuccess(SendResult sendResult) {
                    System.out.println("发送结果：" + sendResult);
                }

                /**
                 * 发送失败的回调函数
                 *
                 * @param throwable 发送失败的异常
                 */
                @Override
                public void onException(Throwable throwable) {
                    System.out.println("发送异常：" + throwable);
                }
            });

            // 睡眠时间可以改长一点，防止回调还没执行producer就shutdown了
            TimeUnit.MILLISECONDS.sleep(500);
        }

        // 关闭生产者
        producer.shutdown();
    }
}
```

### 3.4 发送单向消息

- 没有发送结果，不关心发送成功与否

```java
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
```

### 3.5 负载均衡消费消息

- 默认的消息方式，多个消费者协作消费队列消息

```java
package cn.ykf.rocketmq.demo.base.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * 消息消费者 - push模式，负债均衡模式
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class PushConsumer {

    public static void main(String[] args) throws MQClientException {
        // 创建消费者，使用push模式
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("BaseConsumer");
        // 指定NameServer
        consumer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 订阅Topic
        consumer.subscribe("BASE_MSG", "*");
        // 使用负载均衡模式（可以不用指定，默认就是）
        consumer.setMessageModel(MessageModel.CLUSTERING);
        // 注册消费消息的回调函数，使用并发消费模式，还有顺序消费模式 MessageListenerOrderly
        consumer.registerMessageListener((new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                System.out.println(messages.size());
                for (MessageExt msg : messages) {
                    try {
                        // 消费消息
                        System.out.printf("%s 【msg】：%s，【body】：%s %n", Thread.currentThread().getName(),
                                msg, new String(msg.getBody(), RemotingHelper.DEFAULT_CHARSET));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        // 稍后消费
                        // todo 有个疑惑，这里在for循环中返回稍后消费，不会影响到list中的其他消息吗？还是每次都是一条消息
                        // 现在是单条消息多次推送
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        }));

        // 启动消费者
        consumer.start();
    }
}
```

> - 疑惑
>   - List<MessageExt> messages 每次接收到消息大小都是1，也就是单条消息逐次发送，什么时候会大于1，批量消息？
>   - 如果 List<MessageExt> messages 的大小大于1时，在for循环中返回稍后消费，不会影响到list中其他的消息吗？还是说本来就是一个整体，只能同时消费完成？

### 3.6 广播模式消费消息

```java
consumer.setMessageModel(MessageModel.BROADCASTING);
```

### 3.7 发送顺序消息

> 消息有序指的是可以按照消息的发送顺序来消费(FIFO)。RocketMQ可以严格的保证消息有序，可以分为分区有序或者全局有序。
>
> 顺序消费的原理解析，在默认的情况下消息发送会采取Round Robin轮询方式把消息发送到不同的queue(分区队列)；而消费消息的时候从多个queue上拉取消息，这种情况发送和消费是不能保证顺序。但是如果控制发送的顺序消息只依次发送到同一个queue中，消费的时候只从这个queue上依次拉取，则就保证了顺序。当发送和消费参与的queue只有一个，则是全局有序；如果多个queue参与，则为分区有序，即相对每个queue，消息都是有序的。

- 比如现在有一个创建订单的请求，那么正常的顺序应该是：创建、付款、推送、完成。但是Broker中会有多个Message queue（默认是4），如果四条消息分别存储到了4个队列中，那么消费的时候怎么保证有序？总不能先消费完成消息再消费创建消息吧？
- 可以采取局部有序，不用每条消息都有序，假如张三创建---张三付款---李四创建.....---张三完成---李四完成，不需要保证全局有序，只需要保证张三有序，李四有序即可。那么利用队列的先进先出，我们可以把同一个人的请求发送到同一个Message Queue，那么对于每个人来说就是有序的，也就实现了局部有序。
- ![image-20201218162539833](https://typora-pics-1255993109.cos.ap-guangzhou.myqcloud.com/image-20201218162539833.png)

```java
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
    }
}
```

```java
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
        List<OrderStep> orderList = new ArrayList<OrderStep>();

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
```

### 3.8 消费顺序消息

> MessageListenerOrderLy保证了集群消费时，一个组中只有一个消费者能拿到带锁的队列，同时也保证了一个消费者下只有一个线程处理，可以保证队列中消息严格按照顺序执行，而不是按时间顺序执行
>
> https://blog.csdn.net/hosaos/article/details/100053480

- 保证同一时刻，每个MessageQueue只有一个消费者，并且消费者内部只有一个线程在消费

```java
package cn.ykf.rocketmq.demo.order.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.io.UnsupportedEncodingException;

/**
 * 顺序消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/18
 */
public class OrderConsumer {

    public static void main(String[] args) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("OrderConsumer");
        consumer.setNamesrvAddr("172.16.61.100:9876;172.16.61.101:9876");
        // 第一次启动从队列头开始消费，包含历史消息，如果非第一次启动，那么从上次消费的位置继续消费
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 订阅Topic
        consumer.subscribe("ORDER_MSG", "*");
        // 注册监听器
        consumer.registerMessageListener((MessageListenerOrderly) (messages, consumeOrderlyContext) -> {
            // 自动提交
            consumeOrderlyContext.setAutoCommit(true);

            for (MessageExt msg : messages) {
                // 消费消息
                try {
                    System.out.printf("%s 【msg】：%s，【body】：%s %n", Thread.currentThread().getName(),
                            msg, new String(msg.getBody(), RemotingHelper.DEFAULT_CHARSET));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }

            }

            return ConsumeOrderlyStatus.SUCCESS;
        });

        // 启动消费者
        consumer.start();
    }
}
```

### 3.9 延迟消息

- 生产者

  > 延迟消息的时间不是任意时间片，而是仅支持18个固定的时间段，默认的配置是messageDelayLevel=1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h，分别代表延迟level1-level18

```java
package cn.ykf.rocketmq.demo.shcedule;

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

```

- 消费者

```java
package cn.ykf.rocketmq.demo.shcedule.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 延迟消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020-12-20
 */
public class ScheduledConsumer {

    public static void main(String[] args) throws MQClientException {
        // 创建消费者
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("ScheduledConsumer");
        // 指定NameServer
        consumer.setNamesrvAddr("192.168.72.200:9876;192.168.72.201:9876");
        // 订阅Topic
        consumer.subscribe("ScheduleTopic", "*");
        // 注册监听
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt msg : messages) {
                System.out.println("Receive message[msgId=" + msg.getMsgId() + "] "
                        + (System.currentTimeMillis() - msg.getStoreTimestamp()) + "ms later");
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        // 启动消费者
        consumer.start();
    }
}
```

### 3.10 发送批量消息

```java
package cn.ykf.rocketmq.demo.batch.producer;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 批量消息生产者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020-12-20
 */
public class BatchProducer {

    public static void main(String[] args) throws MQClientException {
        // 创建生产者，指定生产者组
        DefaultMQProducer producer = new DefaultMQProducer("BatchProducer");
        // 指定NameServer
        producer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 启动生产者
        producer.start();

        // 创建消息，指定Topic、Tag以及消息体
        Message msg1 = new Message("BATCH_MSG", "tag1", "key1", "Hello Batch Message 1".getBytes());
        Message msg2 = new Message("BATCH_MSG", "tag2", "key2", "Hello Batch Message 2".getBytes());
        Message msg3 = new Message("BATCH_MSG", "tag3", "key3", "Hello Batch Message 3".getBytes());
        List<Message> messages = Arrays.asList(msg1, msg2, msg3);

        // 批量发送，批量发送的消息总大小不能超过1M
        ListSplitter splitter = new ListSplitter(messages);
        while (splitter.hasNext()) {
            try {
                // 批量消息只是对于生产者来说是批量发送，但是与消费者是多次收到还是一次收到无关，这个取决于消费者自己设置的推送大小
                // 如果消费者设置了推送大小，那么生产者用同步发送也好，批量发送也好，当提前发送到broker堆积时，消费者一启动就可以收到多条消息了
                SendResult result = producer.send(splitter.next());
                System.out.println("发送结果：" + result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 关闭生产者
        producer.shutdown();
    }

    /**
     * 用于分割批量消息集合的迭代器，避免发送总大小大于1M
     */
    static class ListSplitter implements Iterator<List<Message>> {
        /** 允许的总大小，1MB */
        private final int SIZE_LIMIT = 1024 * 1024;
        /** 待切割的消息集合 */
        private final List<Message> messages;
        /** 当前迭代器索引 */
        private int currIndex;

        ListSplitter(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public boolean hasNext() {
            return currIndex < messages.size();
        }

        @Override
        public List<Message> next() {
            int nextIndex = currIndex;
            int totalSize = 0;
            for (; nextIndex < messages.size(); nextIndex++) {
                Message message = messages.get(nextIndex);
                int tmpSize = message.getTopic().length() + message.getBody().length;
                Map<String, String> properties = message.getProperties();
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    tmpSize += entry.getKey().length() + entry.getValue().length();
                }
                // 增加日志的开销20字节
                tmpSize = tmpSize + 20;
                if (tmpSize > SIZE_LIMIT) {
                    //单个消息超过了最大的限制
                    //忽略,否则会阻塞分裂的进程
                    if (nextIndex - currIndex == 0) {
                        //假如下一个子列表没有元素,则添加这个子列表然后退出循环,否则只是退出循环
                        nextIndex++;
                    }
                    break;
                }
                if (tmpSize + totalSize > SIZE_LIMIT) {
                    break;
                } else {
                    totalSize += tmpSize;
                }

            }
            List<Message> subList = messages.subList(currIndex, nextIndex);
            currIndex = nextIndex;
            return subList;
        }
    }
}
```

### 3.11 消费批量消息

- 严格来说不是消费批量消息，而是批量消费消息，无论生产者是使用什么方式发送的消息，是同步发送，还是异步发送，还是批量发送

```java
package cn.ykf.rocketmq.demo.batch.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 批量消息消费者
 *
 * @author YuKaiFan <1092882580@qq.com>
 * @date 2020/12/21
 */
public class BatchConsumer {

    public static void main(String[] args) throws MQClientException {
        // 创建消费者，使用push模式
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("BaseConsumer");
        // 指定NameServer
        consumer.setNamesrvAddr("172.16.61.100:9876:172.16.61.101:9876");
        // 订阅Topic
        consumer.subscribe("BATCH_MSG", "*");
        /*
         * org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
         * private int consumeMessageBatchMaxSize = 1;
         * broker默认推送1条，所以生产者即使一次性发送批量消息，消费者也会收到多次回调
         */
        consumer.setConsumeMessageBatchMaxSize(10);
        // 注册消费消息的回调函数，使用并发消费模式，还有顺序消费模式 MessageListenerOrderly
        consumer.registerMessageListener(((MessageListenerConcurrently) (messages, consumeConcurrentlyContext) -> {
            System.out.println("Messages Size():" + messages.size());

            for (MessageExt msg : messages) {
                // 消费消息
                System.out.printf("%s 【msg】：%s，【body】：%s %n", Thread.currentThread().getName(),
                        msg, new String(msg.getBody()));
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }));

        // 启动消费者
        consumer.start();
    }
}
```

### 3.12 过滤消息

- 生产者通过添加用户自定义属性，方便消费者过滤

```java
Message msg = new Message("FILTER_MSG", "tag" + i, "Hello Filter Message!".getBytes());
// 添加用户属性，用于消费者过滤消息
msg.putUserProperty("i", String.valueOf(i));
```

- 消费者可以通过tag过滤，但是比较简单，有时候满足不了要求。那么就可以使用第二种方式，通过sql语法和用户自定义属性来过滤**（只在push模式下有效）**

```java
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
```

-  如果消费者启动时报错，那么需要修改broker配置文件并重启

```java
org.apache.rocketmq.client.exception.MQClientException: CODE: 1  DESC: The broker does not support consumer to filter message by SQL92
```

```properties
enablePropertyFilter=true
```

- 支持语法（SQL92）

> ### Grammars
>
> RocketMQ only defines some basic grammars to support this feature. You could also extend it easily.
>
> 1. Numeric comparison, like `>`, `>=`, `<`, `<=`, `BETWEEN`, `=`;
> 2. Character comparison, like `=`, `<>`, `IN`;
> 3. `IS NULL` or `IS NOT NULL`;
> 4. Logical `AND`, `OR`, `NOT`;
>
> Constant types are:
>
> 1. Numeric, like 123, 3.1415;
> 2. Character, like ‘abc’, must be made with single quotes;
> 3. `NULL`, special constant;
> 4. Boolean, `TRUE` or `FALSE`;

### 3.13 事务消息

- 流程
  - ![](https://typora-pics-1255993109.cos.ap-guangzhou.myqcloud.com/事务消息.png)
  - 首先发送half消息到broker，如果broker返回ok，说明broker已经将该准备消息放入准备队列中，生产者就可以开始执行本地事务。
  - 根据本地事务的执行状态返回给Broker，如果返回commit，那么说明本地事务执行成功，准备消息可以放入消费队列，消费者可用于消费。如果返回rollback，那么broker将丢弃这条消息，对消费者不可见。如果返回unknown或者由于本地事务执行超时/网络波动超时，那么broker会经过一段时间后进行回查本地事务的状态，根据回查的结果决定如何处理准备消息。
- 限制
  - **事务消息其实只是保证了生产者发送消息成功与本地执行事务的成功的一致性，与消费者的事务无关**
  - 消息不能是延迟消息或者是批量消息
  - 为了避免一半的消息队列堆积，默认每条消息最多检查15次，可以通过broker的配置文件修改 `transactionCheckMax`，超过最大次数后，Broker会丢弃这条消息，并且打印错误日志。可以重写`AbstractTransactionCheckListener`类来修改这个默认行为。
  - 由于事务消息可能不止一次被消费，所以消费端要保证幂等，比如通过消息的事务id transactionId
- 疑惑
  - 关于`transactionTimeOut`参数和用户自定义参数`CHECK_IMMUNITY_TIME_IN_SECONDS`，按照文档的意思代表的是发送half消息后，第一次回查的时间，如果超过该参数值还未收到响应，就会开始回查事务。而`transactionCheckInterval`参数代表的是接下来每次回查的间隔，也就是第一次回查还是unknown，那么第二次回查就需要经过该参数设定的时间
  - 但是验证不出来，回查一直是`transactionCheckInterval`在起作用

- 生产者

```java
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

// 发送事务消息
TransactionSendResult result = producer.sendMessageInTransaction(msg, i);
```

- 事务监听器

```java
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
     *
     * @param msg 待回查的half消息
     * @return 上述三种状态之一
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        System.out.printf("%s 经过 %s ms回查，回查消息： %s%n", Thread.currentThread().getName(), System.currentTimeMillis() - startTime, msg);
        return LocalTransactionState.COMMIT_MESSAGE;
    }
}
```

- 参考链接
  - [Transaction example](https://rocketmq.apache.org/docs/transaction-example/)
  - [事务消息的设计思想、使用方式、原理过程](https://blog.csdn.net/qq_32092237/article/details/109309928)