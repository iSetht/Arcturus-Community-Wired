package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtSetTime;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtTimeLong;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class WiredExecuteTask implements Runnable {
    private final InteractionWiredTrigger task;
    private final Room room;
    private int taskId;

    public WiredExecuteTask(InteractionWiredTrigger trigger, Room room) {
        this.task = trigger;
        this.room = room;

        if (this.task instanceof WiredTriggerAtSetTime)
            this.taskId = ((WiredTriggerAtSetTime) this.task).taskId;

        if (this.task instanceof WiredTriggerAtTimeLong)
            this.taskId = ((WiredTriggerAtTimeLong) this.task).taskId;
    }

    @Override
    public void run() {
        if (!Emulator.isShuttingDown && Emulator.isReady) {
            if (this.task == null) return;
            
            if (this.room != null && this.room.isLoaded() && this.room.getId() == this.task.getRoomId()) {
                if (this.task instanceof WiredTriggerAtSetTime) {
                    if (((WiredTriggerAtSetTime) this.task).taskId != this.taskId)
                        return;
                }
                if (this.task instanceof WiredTriggerAtTimeLong) {
                    if (((WiredTriggerAtTimeLong) this.task).taskId != this.taskId)
                        return;
                }
                try {
                    WiredManager.triggerTimerTick(this.room, this.task);
                } catch (Exception e) {
                    // Prevent task from crashing the thread pool
                }
            }
        }
    }
}
