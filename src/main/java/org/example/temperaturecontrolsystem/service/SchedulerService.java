package org.example.temperaturecontrolsystem.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.temperaturecontrolsystem.entity.SchedulerRequest;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;
import org.example.temperaturecontrolsystem.entity.Slot;
import org.example.temperaturecontrolsystem.mapper.RoomInfoMapper;
import org.example.temperaturecontrolsystem.mapper.SchedulerMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SchedulerService {


    private final int servingSize = 1;

    // 新增配置：同级抢占的等待秒数
    private final long samePriorityWaitSeconds = 5;

    private final SchedulerMapper schedulerMapper;
    private final RoomInfoMapper roomInfoMapper;

    private final PriorityQueue<Slot> waitingQueue = new PriorityQueue<>();
    private final Map<Integer, Slot> runningSlots = new ConcurrentHashMap<>();
    private final BlockingQueue<SchedulerRequest> msgQueue = new LinkedBlockingQueue<>();
    private final ExecutorService msgProcessor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService schedulerTicker = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock queueLock = new ReentrantLock();

    public SchedulerService(SchedulerMapper schedulerMapper, RoomInfoMapper roomInfoMapper) {
        this.schedulerMapper = schedulerMapper;
        this.roomInfoMapper = roomInfoMapper;
    }

    @PostConstruct
    public void start() {
        // 1. 启动消息处理"引擎"
        msgProcessor.submit(this::processMessages);

        // 2. 启动调度"引擎"，每秒执行一次
        schedulerTicker.scheduleAtFixedRate(this::step, 1, 1, TimeUnit.SECONDS);

        System.out.println("Scheduler started with serving size: " + servingSize);
    }

    // Spring Bean 销毁前执行
    @PreDestroy
    public void stop() {
        msgProcessor.shutdownNow();
        schedulerTicker.shutdownNow();
        System.out.println("Scheduler stopped.");
    }

    // 公开的 API，供其他 Service 调用
    public void submitMsg(SchedulerRequest msg) {
        try {
            // 使用 offer 方法并设置超时，可以避免无限期阻塞
            if (!msgQueue.offer(msg, 5, TimeUnit.SECONDS)) {
                // 如果5秒内无法将消息放入队列（队列已满），则记录错误
                System.out.println("Could not submit message to scheduler queue, it might be full. Message: " + msg);
            }
        } catch (InterruptedException e) {
            // 当线程被中断时，恢复中断状态并记录日志
            Thread.currentThread().interrupt();
            System.out.println("Message submission to scheduler was interrupted. Message: " + msg);
        }
    }

    private void processMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SchedulerRequest msg = msgQueue.take();
                System.out.println("Received message: " + msg);

                queueLock.lock();
                try {
                    if ("update".equals(msg.getType())) {
                        int roomId = msg.getRoomId();
                        int newSpeed = getSpeedInt(msg.getSpeed());

                        Slot slotToUpdate = null;
                        boolean wasRunning = false;

                        // 1. 先从两个队列中找到并移除
                        if (runningSlots.containsKey(roomId)) {
                            slotToUpdate = runningSlots.remove(roomId);
                            wasRunning = true;
                        } else {
                            Optional<Slot> opt = waitingQueue.stream().filter(s -> s.getRoomId() == roomId).findFirst();
                            if (opt.isPresent()) {
                                slotToUpdate = opt.get();
                                waitingQueue.remove(slotToUpdate);
                            }
                        }

                        if (slotToUpdate != null) {
                            // 2. 如果之前在运行，需要结算
                            if (wasRunning) {
                                collectAndSettle(slotToUpdate);
                            }

                            // 3. 更新属性
                            slotToUpdate.setSpeed(newSpeed);

                            // 4. !! 关键修复：重置 creationTime，因为它现在是一个新的优先级的请求 !!
                            slotToUpdate.setCreationTime(LocalDateTime.now());

                            // 5. 重新加入等待队列进行排队
                            waitingQueue.add(slotToUpdate);
                            roomInfoMapper.updateAcState(roomId, 2);

                            System.out.println("Room " + roomId + " updated to speed " + newSpeed + ", re-queued with new creation time.");
                        }
                    }
                    else if ("add".equals(msg.getType())) {
                        Slot existingSlot = findSlot(msg.getRoomId());
                        if (existingSlot == null) {
                            Slot newSlot = new Slot();
                            newSlot.setRoomId(msg.getRoomId());
                            newSlot.setSpeed(getSpeedInt(msg.getSpeed()));
                            waitingQueue.add(newSlot);
                            roomInfoMapper.updateAcState(msg.getRoomId(), 2);
                        }
                    }
                    else if ("delete".equals(msg.getType())) {
                        // ... delete 逻辑不变 ...
                        Slot existingSlot = findSlot(msg.getRoomId());
                        if (existingSlot != null) {
                            if (runningSlots.containsKey(msg.getRoomId())) {
                                collectAndSettle(runningSlots.remove(msg.getRoomId()));
                            } else {
                                waitingQueue.remove(existingSlot);
                            }
                        }
                    }
                } finally {
                    queueLock.unlock();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void step() {
        queueLock.lock();
        try {
            if (waitingQueue.isEmpty()) {
                return;
            }

            if (runningSlots.size() < servingSize) {
                Slot slotToRun = waitingQueue.poll();
                if (slotToRun != null) {
                    slotToRun.setServiceStartTime(LocalDateTime.now());
                    runningSlots.put(slotToRun.getRoomId(), slotToRun);
                    roomInfoMapper.updateAcState(slotToRun.getRoomId(), 1);
                    System.out.println("Room " + slotToRun.getRoomId() + " moved from waiting to running.");
                }
                return;
            }

            // --- 重构后的抢占决策逻辑 ---
            Slot highestWaiting = waitingQueue.peek();
            Slot targetToPreempt = null; // 预备要被抢占的对象
            boolean isSamePriorityPreemption = false; // 标记是否是同级抢占

            Slot lowestRunning = findLowestPriorityRunningSlot();

            if (highestWaiting == null || lowestRunning == null) {
                return;
            }

            // 决策 1: 高优先级抢占
            if (highestWaiting.getSpeed() > lowestRunning.getSpeed()) {
                targetToPreempt = lowestRunning;
                isSamePriorityPreemption = false; // 明确这不是同级抢占
                System.out.println(String.format("High priority preemption determined: Waiting %d (speed %d) will preempt Running %d (speed %d)",
                        highestWaiting.getRoomId(), highestWaiting.getSpeed(), targetToPreempt.getRoomId(), targetToPreempt.getSpeed()));
            }
            // 决策 2: 同级优先级抢占
            else if (highestWaiting.getSpeed() == lowestRunning.getSpeed()) {
                long waitedSeconds = Duration.between(highestWaiting.getCreationTime(), LocalDateTime.now()).getSeconds();
                if (waitedSeconds >= samePriorityWaitSeconds) {
                    Slot longestServedPeer = findLongestServedRunningSlotWithSpeed(highestWaiting.getSpeed());
                    if (longestServedPeer != null) {
                        targetToPreempt = longestServedPeer;
                        isSamePriorityPreemption = true; // 明确这是同级抢占
                        System.out.println(String.format("Same priority preemption determined: Waiting %d (waited %ds) will preempt longest served %d",
                                highestWaiting.getRoomId(), waitedSeconds, targetToPreempt.getRoomId()));
                    }
                }
            }
            if (targetToPreempt != null) {
                // 1. 从运行队列中移除被抢占者
                runningSlots.remove(targetToPreempt.getRoomId());
                // 2. 结算
                collectAndSettle(targetToPreempt);

                // 3. 如果是同级抢占，则重置被抢占者的时间戳
                if (isSamePriorityPreemption) {
                    targetToPreempt.setCreationTime(LocalDateTime.now());
                    System.out.println("Resetting creation time for same-priority preempted room " + targetToPreempt.getRoomId());
                }
                // 4. 将被抢占者重新放回等待队列
                waitingQueue.add(targetToPreempt);

                roomInfoMapper.updateAcState(targetToPreempt.getRoomId(), 2);
                // 5. 让等待者进入服务
                Slot slotToRun = waitingQueue.poll(); // 这个一定是 highestWaiting
                if (slotToRun != null) {
                    slotToRun.setServiceStartTime(LocalDateTime.now());
                    runningSlots.put(slotToRun.getRoomId(), slotToRun);
                    roomInfoMapper.updateAcState(slotToRun.getRoomId(), 1);
                }
            }

        } finally {
            queueLock.unlock();
        }
    }

    private void collectAndSettle(Slot slot) {
        if (slot == null || slot.getServiceStartTime() == null) return;

        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = Duration.between(slot.getServiceStartTime(), endTime).getSeconds();

        // ... (计费逻辑不变) ...
        BigDecimal cost = BigDecimal.valueOf(durationSeconds)
                .multiply(BigDecimal.valueOf(slot.getSpeed()))
                .multiply(new BigDecimal("0.005556"));

        SchedulerBoardRecord record = new SchedulerBoardRecord();
        record.setRoomId(slot.getRoomId());
        record.setStartTime(slot.getServiceStartTime()); // 使用 serviceStartTime
        record.setEndTime(endTime);
        record.setDurationSeconds(durationSeconds);
        record.setSpeed(slot.getSpeed());
        record.setCost(cost);

        schedulerMapper.insertRecord(record);
    }

    // === 新增辅助方法 ===

    /**
     * 从正在服务的队列中，找到指定风速下，服务时间最长的那个 Slot。
     * @param speed 指定的风速等级
     * @return 服务时间最长的 Slot，如果不存在则返回 null
     */
    private Slot findLongestServedRunningSlotWithSpeed(int speed) {
        final LocalDateTime now = LocalDateTime.now();
        return runningSlots.values().stream()
                .filter(s -> s.getSpeed() == speed) // 筛选出所有同级请求
                .max((s1, s2) -> { // 找到服务时间最长的
                    long duration1 = Duration.between(s1.getServiceStartTime(), now).toMillis();
                    long duration2 = Duration.between(s2.getServiceStartTime(), now).toMillis();
                    return Long.compare(duration1, duration2);
                })
                .orElse(null);
    }


    private Slot findLowestPriorityRunningSlot() {
        return runningSlots.values().stream()
                .min(Slot::compareTo) // 因为我们是降序，所以 min 就是优先级最低的
                .orElse(null);
    }

    private Slot findSlot(int roomId) {
        if (runningSlots.containsKey(roomId)) {
            return runningSlots.get(roomId);
        }
        return waitingQueue.stream().filter(s -> s.getRoomId() == roomId).findFirst().orElse(null);
    }

    private int getSpeedInt(String speed) {
        switch (speed) {
            case "low": return 1;
            case "medium": return 2;
            case "high": return 3;
            default: return 1;
        }
    }
}
