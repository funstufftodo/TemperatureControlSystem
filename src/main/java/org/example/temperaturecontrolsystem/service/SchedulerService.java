package org.example.temperaturecontrolsystem.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;
import org.example.temperaturecontrolsystem.entity.SchedulerRequest;
import org.example.temperaturecontrolsystem.entity.Slot;
import org.example.temperaturecontrolsystem.mapper.RoomInfoMapper;
import org.example.temperaturecontrolsystem.mapper.SchedulerMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SchedulerService {

    private final int servingSize = 2;

    private final long timeSliceSeconds = 5;

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
        msgProcessor.submit(this::processMessages);
        schedulerTicker.scheduleAtFixedRate(this::step, 1, 1, TimeUnit.SECONDS);
        System.out.println("Scheduler started with serving size: " + servingSize);
    }

    @PreDestroy
    public void stop() {
        msgProcessor.shutdownNow();
        schedulerTicker.shutdownNow();
        System.out.println("Scheduler stopped.");
    }

    public void submitMsg(SchedulerRequest msg) {
        try {
            if (!msgQueue.offer(msg, 5, TimeUnit.SECONDS)) {
                System.out.println("Could not submit message to scheduler queue, it might be full. Message: " + msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Message submission to scheduler was interrupted. Message: " + msg);
        }
    }

    private void processMessages() {
        // ... processMessages 方法保持原样，无需修改 ...
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
                            if (wasRunning) {
                                collectAndSettle(slotToUpdate);
                            }
                            slotToUpdate.setSpeed(newSpeed);
                            // 当速度改变时，它相当于一个新的请求，重置其排序时间
                            slotToUpdate.setLastServiceTime(LocalDateTime.now());
                            waitingQueue.add(slotToUpdate);
                            roomInfoMapper.updateAcState(roomId, 2);
                            System.out.println("Room " + roomId + " updated to speed " + newSpeed + ", re-queued with new lastServiceTime.");
                        }
                    }
                    else if ("add".equals(msg.getType())) {
                        if (findSlot(msg.getRoomId()) == null) {
                            Slot newSlot = new Slot();
                            newSlot.setRoomId(msg.getRoomId());
                            newSlot.setSpeed(getSpeedInt(msg.getSpeed()));
                            waitingQueue.add(newSlot);
                            roomInfoMapper.updateAcState(msg.getRoomId(), 2);
                        }
                    }
                    else if ("delete".equals(msg.getType())) {
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

    /**
     * 调度器核心逻辑 - 最终、最简、最正确的版本
     */

    private void step() {
        queueLock.lock();
        try {
            // 1. 如果等待队列为空，则没有抢占或轮转的必要
            if (waitingQueue.isEmpty()) {
                return;
            }

            Slot highestWaiting = waitingQueue.peek();

            // 2. 如果服务槽有空位，优先填充，这是最高优先级操作
            if (runningSlots.size() < servingSize) {
                System.out.printf("决策：填充！Room %d 进入服务%n", highestWaiting.getRoomId());
                startService(waitingQueue.poll());
                return;
                // 注意：填充后立即返回，将决策推迟到下一个 tick，这使得逻辑更简单、更稳定
            }

            // --- 到此，服务槽已满，必须进行抢占或轮转决策 ---

            Slot targetToPreempt = null;

            // 决策分支 A: 高优先级抢占
            // 寻找运行中的、优先级最低的任务
            Slot lowestRunning = findLowestPriorityRunningSlot();
            if (highestWaiting.getSpeed() > lowestRunning.getSpeed()) {
                targetToPreempt = lowestRunning;
                System.out.printf("决策：高优先级抢占！等待中的 Room %d (speed %d) 将抢占运行中的 Room %d (speed %d)%n",
                        highestWaiting.getRoomId(), highestWaiting.getSpeed(),
                        targetToPreempt.getRoomId(), targetToPreempt.getSpeed());
            }
            // 决策分支 B: 同级时间片轮转
            else {
                // 在所有正在运行的同级任务中，找到那个服务时间最长的
                // 注意：我们只关心与等待队列头部任务同级的运行任务
                Slot longestServedPeer = findLongestServedPeer(highestWaiting.getSpeed());

                // 只有当存在一个可以被轮换的同级任务时，才进行下一步判断
                if (longestServedPeer != null) {
                    long runningDuration = Duration.between(longestServedPeer.getServiceStartTime(), LocalDateTime.now()).getSeconds();

                    // 【最终修正】只要时间片到了，就必须轮转！无需任何额外的 compareTo 检查！
                    if (runningDuration >= timeSliceSeconds) {
                        targetToPreempt = longestServedPeer;
                        System.out.printf("决策：时间片轮转！Room %d 已运行 %d 秒，将被等待中最优先的 Room %d 替换%n",
                                targetToPreempt.getRoomId(), runningDuration, highestWaiting.getRoomId());
                    }
                }
            }

            // 执行抢占/轮转
            if (targetToPreempt != null) {
                // 停止目标任务的服务
                Slot preemptedSlot = stopService(targetToPreempt.getRoomId());
                if (preemptedSlot != null) {
                    // 将被换下的任务重新加入等待队列
                    // 它的 lastServiceTime 是它上次开始服务的时间，这保证了它在同级中优先级最低
                    waitingQueue.add(preemptedSlot);
                }

                // 启动等待队列中优先级最高的任务
                // （由于 compareTo 的作用，这一定是那个等待最久的任务）
                startService(waitingQueue.poll());
            }

        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 在运行中的、与指定风速相同的任务里，找到服务时间最长的那个。
     * @param speed 指定的风速
     * @return 服务时间最长的同级任务，如果不存在则返回 null
     */
    private Slot findLongestServedPeer(int speed) {
        return runningSlots.values().stream()
                .filter(slot -> slot.getSpeed() == speed)
                // 按 serviceStartTime 升序排序，第一个就是服务开始时间最早的，即服务时间最长的
                .min(java.util.Comparator.comparing(Slot::getServiceStartTime))
                .orElse(null);
    }

    // findLowestPriorityRunningSlot, startService, stopService 等其他方法保持不变
    // startService, stopService, 和其他所有辅助方法都保持上一版的样子，它们是正确的。
    private void startService(Slot slot) {
        if (slot == null) return;
        LocalDateTime now = LocalDateTime.now();
        slot.setServiceStartTime(now); // 用于计费
        slot.setLastServiceTime(now);  // 用于排序
        runningSlots.put(slot.getRoomId(), slot);
        roomInfoMapper.updateAcState(slot.getRoomId(), 1);
        System.out.println("服务开始: Room " + slot.getRoomId() + ", 时间戳已更新");
    }

    private Slot stopService(int roomId) {
        Slot slot = runningSlots.remove(roomId);
        if (slot != null) {
            collectAndSettle(slot);
            roomInfoMapper.updateAcState(slot.getRoomId(), 2);
            System.out.println("服务停止: Room " + slot.getRoomId());
        }
        return slot;
    }

    /**
     * 计费方法 - 无需修改
     * 它将使用 slot 对象中携带的 serviceStartTime 字段。
     */
    private void collectAndSettle(Slot slot) {
        if (slot == null || slot.getServiceStartTime() == null) {
            System.err.println("无法计费：Slot 或其 serviceStartTime 为 null。Slot: " + slot);
            return;
        }

        LocalDateTime endTime = LocalDateTime.now();
        // durationSeconds 使用的是本次服务的开始时间，这是正确的
        long durationSeconds = Duration.between(slot.getServiceStartTime(), endTime).getSeconds();

        // 如果持续时间太短（例如，立即被抢占），至少算1秒的费用或不计费
        if (durationSeconds <= 0) {
            // return; // 或者按最小单位计费
        }

        BigDecimal cost = BigDecimal.valueOf(durationSeconds)
                .multiply(BigDecimal.valueOf(slot.getSpeed()))
                .multiply(new BigDecimal("0.005556")); // 假设这是每秒每风速单位的费用

        SchedulerBoardRecord record = new SchedulerBoardRecord();
        record.setRoomId(slot.getRoomId());
        record.setStartTime(slot.getServiceStartTime());
        record.setEndTime(endTime);
        record.setDurationSeconds(durationSeconds);
        record.setSpeed(slot.getSpeed());
        record.setCost(cost);

        schedulerMapper.insertRecord(record);
    }

    // --- 辅助方法保持不变 ---

    private Slot findLowestPriorityRunningSlot() {
        return runningSlots.values().stream().min(Slot::compareTo).orElse(null);
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