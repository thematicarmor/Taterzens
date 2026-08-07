package org.samo_lego.taterzens.npc.ai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.samo_lego.taterzens.Taterzens.MOD_ID;

/**
 * Registry of goals that can be attached to a Taterzen by name.
 *
 * <p>Only goals that work on any {@link net.minecraft.world.entity.PathfinderMob} live here. Goals
 * bound to a concrete mob class (a zombie's {@code ZombieAttackGoal}, a villager's trading goals)
 * cannot be re-pointed at another entity, because vanilla goals capture their owner in the
 * constructor - so "copy a real mob's AI wholesale" isn't on the table. Composing from this set is.
 *
 * <p>Third parties can add their own with {@link #register(AiGoalType)}.
 */
public final class AiGoalRegistry {
    private static final Map<ResourceLocation, AiGoalType> GOALS = new LinkedHashMap<>();

    private AiGoalRegistry() {
    }

    /**
     * Registers a goal type, replacing any previous registration under the same id.
     *
     * @param type goal type to register
     * @return the registered type, for convenience
     */
    public static AiGoalType register(AiGoalType type) {
        GOALS.put(type.id(), type);
        return type;
    }

    /**
     * @param id goal id; a bare path such as {@code panic} is resolved in the Taterzens namespace
     * @return the goal type, if registered
     */
    public static Optional<AiGoalType> get(String id) {
        ResourceLocation location = id.indexOf(':') == -1 ?
                new ResourceLocation(MOD_ID, id) :
                ResourceLocation.tryParse(id);

        return location == null ? Optional.empty() : Optional.ofNullable(GOALS.get(location));
    }

    /**
     * @return all registered goal types
     */
    public static Collection<AiGoalType> values() {
        return GOALS.values();
    }

    private static void register(String path, int priority, boolean targetGoal, AiGoalType.Factory factory) {
        register(new AiGoalType(new ResourceLocation(MOD_ID, path), priority, targetGoal, factory));
    }

    static {
        // --- Movement / self preservation -------------------------------------------------------
        register("float", 0, false, (npc, args) -> new FloatGoal(npc));
        register("breath_air", 0, false, (npc, args) -> new BreathAirGoal(npc));
        register("find_water", 0, false, (npc, args) -> new TryFindWaterGoal(npc));
        register("panic", 1, false, (npc, args) ->
                new PanicGoal(npc, args.getDouble("speed", 1.25D)));
        register("flee_sun", 3, false, (npc, args) ->
                new FleeSunGoal(npc, args.getDouble("speed", 1.0D)));
        register("restrict_sun", 3, false, (npc, args) -> new RestrictSunGoal(npc));

        // --- Reacting to other entities ---------------------------------------------------------
        register("avoid_entity", 3, false, (npc, args) ->
                new AvoidEntityGoal<>(
                        npc,
                        LivingEntity.class,
                        args.getEntityPredicate("entity"),
                        args.getFloat("radius", 10.0F),
                        args.getDouble("speed", 1.0D),
                        args.getDouble("sprint", 1.2D),
                        living -> true
                ));
        register("tempt", 4, false, (npc, args) ->
                new TemptGoal(
                        npc,
                        args.getDouble("speed", 1.1D),
                        args.getIngredient("item"),
                        args.getBoolean("scared", false)
                ));
        register("follow_mob", 5, false, (npc, args) ->
                new FollowMobGoal(
                        npc,
                        args.getDouble("speed", 1.0D),
                        args.getFloat("stop", 3.0F),
                        args.getFloat("radius", 12.0F)
                ));
        register("look_at", 8, false, (npc, args) ->
                new LookAtPlayerGoal(
                        npc,
                        args.getEntityClass("entity"),
                        args.getFloat("radius", 8.0F)
                ));
        register("random_look", 9, false, (npc, args) -> new RandomLookAroundGoal(npc));

        // --- Wandering --------------------------------------------------------------------------
        register("stroll", 6, false, (npc, args) ->
                new RandomStrollGoal(npc, args.getDouble("speed", 1.0D), args.getInt("interval", 120)));
        register("water_avoiding_stroll", 6, false, (npc, args) ->
                new WaterAvoidingRandomStrollGoal(npc, args.getDouble("speed", 1.0D)));
        register("random_swim", 6, false, (npc, args) ->
                new RandomSwimmingGoal(npc, args.getDouble("speed", 1.0D), args.getInt("interval", 120)));
        register("move_towards_restriction", 5, false, (npc, args) ->
                new MoveTowardsRestrictionGoal(npc, args.getDouble("speed", 1.0D)));

        // --- Villages and doors -----------------------------------------------------------------
        register("move_through_village", 5, false, (npc, args) ->
                new MoveThroughVillageGoal(
                        npc,
                        args.getDouble("speed", 1.0D),
                        args.getBoolean("night", false),
                        args.getInt("distance", 16),
                        () -> false
                ));
        register("stroll_village", 6, false, (npc, args) ->
                new StrollThroughVillageGoal(npc, args.getInt("interval", 60)));
        register("open_door", 2, false, (npc, args) ->
                new OpenDoorGoal(npc, args.getBoolean("close", true)));
        register("break_door", 2, false, (npc, args) ->
                new BreakDoorGoal(npc, args.getInt("time", 240), AiGoalArgs.anyDifficulty()));

        // --- Combat -----------------------------------------------------------------------------
        register("melee_attack", 3, false, (npc, args) ->
                new MeleeAttackGoal(npc, args.getDouble("speed", 1.2D), args.getBoolean("pause", false)));
        register("ranged_attack", 3, false, (npc, args) ->
                new RangedAttackGoal(
                        npc,
                        args.getDouble("speed", 1.2D),
                        args.getInt("interval", 40),
                        args.getFloat("radius", 40.0F)
                ));
        register("leap_at_target", 4, false, (npc, args) ->
                new LeapAtTargetGoal(npc, args.getFloat("height", 0.4F)));
        register("move_towards_target", 4, false, (npc, args) ->
                new MoveTowardsTargetGoal(npc, args.getDouble("speed", 1.0D), args.getFloat("radius", 32.0F)));
        register("eat_block", 7, false, (npc, args) -> new EatBlockGoal(npc));

        // --- Targeting (what to attack) ---------------------------------------------------------
        register("hurt_by", 1, true, (npc, args) -> new HurtByTargetGoal(npc));
        register("attack_nearest", 2, true, (npc, args) ->
                new NearestAttackableTargetGoal<>(
                        npc,
                        LivingEntity.class,
                        args.getInt("interval", 10),
                        args.getBoolean("see", true),
                        args.getBoolean("reach", false),
                        args.getEntityPredicate("entity")
                ));
    }

    /**
     * Forces the static initialiser to run.
     */
    public static void init() {
    }
}
