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
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SchedulerRequest msg = msgQueue.take();
                System.out.println("Received message: " + msg);

                queueLock.lock();
                try {
                    if ("update".equals(msg.getType())) {
                        int roomId = msg.getRoomId();
                        int newSpeed = getSpeedInt(msg.getSpeed());

                        // --- 全新的、正确的 Update 逻辑 (这部分逻辑原本是正确的，无需修改) ---
                        // Case 1: 任务正在运行
                        if (runningSlots.containsKey(roomId)) {
                            Slot slotToUpdate = runningSlots.get(roomId);
                            if (slotToUpdate.getSpeed() != newSpeed) {
                                System.out.printf("Room %d is running, updating speed from %d to %d in place.%n",
                                        roomId, slotToUpdate.getSpeed(), newSpeed);
                                slotToUpdate.setSpeed(newSpeed);
                            }
                        }
                        // Case 2: 任务正在等待
                        else {
                            Optional<Slot> opt = waitingQueue.stream().filter(s -> s.getRoomId() == roomId).findFirst();
                            if (opt.isPresent()) {
                                Slot slotToUpdate = opt.get();
                                // 从等待队列中移除，更新后重新加入，以确保优先级正确
                                waitingQueue.remove(slotToUpdate);
                                slotToUpdate.setSpeed(newSpeed);
                                // 重置其排序时间戳，因为它现在是一个新的请求
                                // FIX: 更新lastServiceTime，确保公平性。你原有的逻辑这里是正确的。
                                slotToUpdate.setLastServiceTime(LocalDateTime.now());
                                waitingQueue.add(slotToUpdate);
                                System.out.println("Room " + roomId + " was waiting, updated and re-queued with new speed " + newSpeed);
                            }
                        }
                    }
                    else if ("add".equals(msg.getType())) {
                        if (findSlot(msg.getRoomId()) == null) {
                            Slot newSlot = new Slot();
                            newSlot.setRoomId(msg.getRoomId());
                            newSlot.setSpeed(getSpeedInt(msg.getSpeed()));
                            waitingQueue.add(newSlot);
                            roomInfoMapper.updateAcState(msg.getRoomId(), 2);
                            System.out.println("Added new request for Room " + msg.getRoomId() + " with speed " + msg.getSpeed());
                        }
                    }
                    else if ("delete".equals(msg.getType())) {
                        int roomId = msg.getRoomId();
                        // FIX: 正确地处理删除逻辑
                        if (runningSlots.containsKey(roomId)) {
                            // 如果正在运行，则停止服务并结算
                            stopService(roomId); // stopService 内部已经包含了 remove 和 collectAndSettle
                        } else {
                            // 如果正在等待，则从队列中移除
                            Optional<Slot> slotToRemove = waitingQueue.stream().filter(s -> s.getRoomId() == roomId).findFirst();
                            slotToRemove.ifPresent(waitingQueue::remove);
                        }
                        // 无论在哪，最后都更新为空闲状态
                        roomInfoMapper.updateAcState(roomId, 0); // 假设 0 是空闲/关闭
                        System.out.println("Deleted request for Room " + roomId);
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
            // 1. 如果没有等待的任务，或服务槽有空闲，则按最简逻辑处理
            if (waitingQueue.isEmpty()) {
                return;
            }
            if (runningSlots.size() < servingSize) {
                System.out.printf("决策：填充空闲槽！Room %d (speed %d) 进入服务%n",
                        waitingQueue.peek().getRoomId(), waitingQueue.peek().getSpeed());
                startService(waitingQueue.poll());
                return;
            }

            // --- 服务槽已满，进入复杂的替换决策 ---

            Slot highestWaiting = waitingQueue.peek();

            // 2. 高优先级抢占决策
            // 寻找一个正在运行的，且风速低于等待任务的Slot。
            // 为了公平，我们应该抢占这些低风速任务中，优先级最低的那个（服务时间最长的）。
            Optional<Slot> targetForPreemption = runningSlots.values().stream()
                    .filter(running -> running.getSpeed() < highestWaiting.getSpeed())
                    .min(Comparator.naturalOrder()); // naturalOrder() 就是我们定义的 compareTo

            if (targetForPreemption.isPresent()) {
                Slot victim = targetForPreemption.get();
                System.out.printf("决策：高优先级抢占！等待中的 Room %d (speed %d) 将替换运行中的 Room %d (speed %d)%n",
                        highestWaiting.getRoomId(), highestWaiting.getSpeed(),
                        victim.getRoomId(), victim.getSpeed());

                // 执行抢占
                performSwap(victim);
                return; // 完成本次调度
            }


            // 3. 同级时间片轮转决策
            // 仅当没有发生高优抢占时，才考虑同级轮转。
            // 寻找一个正在运行的、与等待任务风速相同、且服务时间超时的任务。
            // 如果有多个这样的任务，我们应该轮换掉那个优先级最低的（即服务时间最长的）。
            Optional<Slot> targetForRotation = runningSlots.values().stream()
                    .filter(running -> running.getSpeed() == highestWaiting.getSpeed())
                    .filter(running -> Duration.between(running.getServiceStartTime(), LocalDateTime.now()).getSeconds() >= timeSliceSeconds)
                    .min(Comparator.naturalOrder()); // 在所有超时的同级任务中，找到服务开始时间最早的那个

            if (targetForRotation.isPresent()) {
                Slot victim = targetForRotation.get();
                // 确认一下等待队列的最高优先级者确实是同级的
                if (highestWaiting.getSpeed() == victim.getSpeed()) {
                    System.out.printf("决策：同级时间片轮转！等待中的 Room %d 将替换服务超时的 Room %d (同为 speed %d)%n",
                            highestWaiting.getRoomId(), victim.getRoomId(), victim.getSpeed());

                    // 执行轮转
                    performSwap(victim);
                }
            }

        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 辅助方法，执行一个完整的替换操作
     * @param victim 要被从服务队列中移除的Slot
     */
    private void performSwap(Slot victim) {
        // 1. 从等待队列取出新的服务者
        Slot replacement = waitingQueue.poll();
        if (replacement == null) return; // 安全检查

        // 2. 停止旧的服务
        Slot stoppedSlot = stopService(victim.getRoomId());

        // 3. 将被换下的任务重新放入等待队列，并更新其时间戳以保证公平
        if (stoppedSlot != null) {
            stoppedSlot.setLastServiceTime(LocalDateTime.now());
            waitingQueue.add(stoppedSlot);
        }

        // 4. 开始新的服务
        startService(replacement);
    }
    /**
     * 找到一个因为时间片用完而需要被轮换的任务。
     * @return 需要被轮换的任务，如果不存在则返回 null
     */
    private Slot findPeerToRotate() {
        return runningSlots.values().stream()
                .filter(running -> Duration.between(running.getServiceStartTime(), LocalDateTime.now()).getSeconds() >= timeSliceSeconds)
                // 在所有超时的任务中，找到优先级最低的那个（这确保了我们总是先轮换掉最不重要的超时任务）
                .min(Comparator.naturalOrder())
                .orElse(null);
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