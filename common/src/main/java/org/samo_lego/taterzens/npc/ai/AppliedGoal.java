package org.samo_lego.taterzens.npc.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

/**
 * A goal currently attached to a Taterzen, together with how it was configured.
 *
 * <p>The configuration is kept alongside the live {@link Goal} because the goal instance itself is
 * bound to one Taterzen and can't be saved - on reload the goal is rebuilt from these fields.
 */
public class AppliedGoal {
    private final AiGoalType type;
    private final int priority;
    private final String rawArgs;

    /**
     * Ticks left before this goal removes itself, or -1 to stay until removed explicitly.
     *
     * <p>Timed goals are what make quick time events work: a script attaches "panic for 5 seconds"
     * to a crowd and doesn't have to come back to clean up.
     */
    private int remainingTicks;

    @Nullable
    private Goal goal;

    public AppliedGoal(AiGoalType type, int priority, String rawArgs, int remainingTicks) {
        this.type = type;
        this.priority = priority;
        this.rawArgs = rawArgs;
        this.remainingTicks = remainingTicks;
    }

    public AiGoalType type() {
        return this.type;
    }

    public int priority() {
        return this.priority;
    }

    public String rawArgs() {
        return this.rawArgs;
    }

    public boolean isTemporary() {
        return this.remainingTicks >= 0;
    }

    public int remainingTicks() {
        return this.remainingTicks;
    }

    @Nullable
    public Goal goal() {
        return this.goal;
    }

    public void setGoal(@Nullable Goal goal) {
        this.goal = goal;
    }

    /**
     * Counts a tick down.
     *
     * @return true once a temporary goal has run out and should be removed
     */
    public boolean tickExpired() {
        return this.isTemporary() && --this.remainingTicks <= 0;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.type.id().toString());
        tag.putInt("Priority", this.priority);
        tag.putString("Args", this.rawArgs);
        tag.putInt("RemainingTicks", this.remainingTicks);
        return tag;
    }

    /**
     * @param tag tag written by {@link #toTag()}
     * @return the restored goal, or null if its type is no longer registered
     */
    @Nullable
    public static AppliedGoal fromTag(CompoundTag tag) {
        return AiGoalRegistry.get(tag.getString("Id"))
                .map(type -> new AppliedGoal(
                        type,
                        tag.getInt("Priority"),
                        tag.getString("Args"),
                        tag.contains("RemainingTicks") ? tag.getInt("RemainingTicks") : -1
                ))
                .orElse(null);
    }
}
