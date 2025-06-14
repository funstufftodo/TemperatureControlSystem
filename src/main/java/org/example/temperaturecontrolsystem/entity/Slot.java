package org.example.temperaturecontrolsystem.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Slot implements Comparable<Slot> {
    private int roomId;
    private int speed;

    /**
     * 本次服务开始的时间，专门用于计费。
     * 只有当 Slot 在 runningSlots 中时，这个字段才有意义。
     */
    private LocalDateTime serviceStartTime;

    /**
     * 上一次服务的“标记”时间，专门用于优先级排序。
     * 它可以是上次服务的开始时间，也可以是创建时间。
     * 我们统一用它来比较，实现公平轮询。
     */
    private LocalDateTime lastServiceTime;

    /**
     * 任务的创建时间，作为最终的平局决胜者。
     */
    private LocalDateTime creationTime = LocalDateTime.now();

    public Slot() {
        // 当一个 Slot 被创建时，它的“上次服务时间”就是它的创建时间。
        // 这确保了新任务可以和从未服务过的老任务在同一起跑线比较。
        this.lastServiceTime = this.creationTime;
    }

    @Override
    public int compareTo(Slot other) {
        // 规则1：比较风速 (speed)，风速高的优先级更高 (降序)
        if (this.speed != other.speed) {
            return Integer.compare(other.speed, this.speed);
        }

        // 规则2：如果风速相同，则比较上一次服务标记时间 (lastServiceTime)
        // 上次服务时间越早的，优先级越高 (升序)。
        // 因为构造函数保证了 lastServiceTime 不为 null，所以比较逻辑非常简单。
        if (!this.lastServiceTime.equals(other.lastServiceTime)) {
            return this.lastServiceTime.compareTo(other.lastServiceTime);
        }

        // 规则3：如果连 lastServiceTime 都一样（理论上不太可能，除非在同一毫秒创建），
        // 则按 creationTime 作为最终决胜局，保证排序的稳定性。
        return this.creationTime.compareTo(other.creationTime);
    }
}