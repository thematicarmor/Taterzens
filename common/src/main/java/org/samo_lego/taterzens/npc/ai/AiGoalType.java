package org.samo_lego.taterzens.npc.ai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import org.samo_lego.taterzens.npc.TaterzenNPC;

/**
 * A goal that can be attached to a Taterzen by name.
 *
 * @param id           registry id, e.g. {@code taterzens:panic}
 * @param defaultPriority priority used when the caller doesn't specify one; follows vanilla
 *                        convention where lower numbers win
 * @param targetGoal   whether this belongs on the target selector (what to attack) rather than the
 *                     goal selector (what to do)
 * @param factory      builds the goal instance for a given Taterzen
 */
public record AiGoalType(ResourceLocation id, int defaultPriority, boolean targetGoal, Factory factory) {

    @FunctionalInterface
    public interface Factory {
        /**
         * @param npc  Taterzen the goal is being attached to
         * @param args parsed {@code key=value} arguments
         * @return the goal instance
         * @throws IllegalArgumentException if the arguments don't make sense for this goal
         */
        Goal create(TaterzenNPC npc, AiGoalArgs args);
    }
}
