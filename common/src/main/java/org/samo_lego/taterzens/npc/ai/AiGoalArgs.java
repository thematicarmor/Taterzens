package org.samo_lego.taterzens.npc.ai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Parsed {@code key=value} arguments of an {@link AiGoalType}.
 *
 * <p>Goals are configured with named rather than positional arguments on purpose: the AI system is
 * meant to be driven from a script engine over the console, and a script that emits
 * {@code speed=1.25 duration=100} stays readable (and keeps working when a goal gains another
 * option) where {@code 1.25 100} does not.
 */
public class AiGoalArgs {
    private final Map<String, String> values = new LinkedHashMap<>();

    private AiGoalArgs() {
    }

    /**
     * Parses a whitespace separated list of {@code key=value} pairs.
     *
     * @param raw raw argument string, may be empty
     * @return parsed arguments
     * @throws IllegalArgumentException if a token is not a {@code key=value} pair
     */
    public static AiGoalArgs parse(String raw) {
        AiGoalArgs args = new AiGoalArgs();

        if (raw == null || raw.isBlank())
            return args;

        for (String token : raw.trim().split("\\s+")) {
            int split = token.indexOf('=');
            if (split < 1 || split == token.length() - 1)
                throw new IllegalArgumentException("Expected key=value, got '" + token + "'");

            args.values.put(token.substring(0, split).toLowerCase(), token.substring(split + 1));
        }

        return args;
    }

    public double getDouble(String key, double fallback) {
        String value = this.values.get(key);
        if (value == null)
            return fallback;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' expects a number, got '" + value + "'");
        }
    }

    public float getFloat(String key, float fallback) {
        return (float) this.getDouble(key, fallback);
    }

    public int getInt(String key, int fallback) {
        String value = this.values.get(key);
        if (value == null)
            return fallback;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' expects a whole number, got '" + value + "'");
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = this.values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public String getString(String key, String fallback) {
        return this.values.getOrDefault(key, fallback);
    }

    /**
     * Resolves an entity type id into a predicate matching living entities of that type.
     *
     * <p>A predicate rather than a {@link Class} so that any of the ~100 registered entity types can
     * be named, not just the handful that have a distinct class in the goal constructors.
     *
     * @param key argument name
     * @return predicate matching the configured type, or one matching everything if unset
     */
    public Predicate<LivingEntity> getEntityPredicate(String key) {
        String value = this.values.get(key);
        if (value == null)
            return entity -> true;

        if ("player".equalsIgnoreCase(value))
            return entity -> entity.getType() == EntityType.PLAYER;

        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null)
            throw new IllegalArgumentException("'" + value + "' is not a valid entity id");

        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown entity type '" + value + "'"));

        return entity -> entity.getType() == type;
    }

    /**
     * Resolves an entity class for goals that have no predicate-accepting constructor.
     *
     * @param key argument name
     * @return one of the coarse living-entity classes the vanilla goals accept
     */
    public Class<? extends LivingEntity> getEntityClass(String key) {
        String value = this.values.getOrDefault(key, "player");

        return switch (value.toLowerCase()) {
            case "player" -> net.minecraft.server.level.ServerPlayer.class;
            case "mob" -> net.minecraft.world.entity.Mob.class;
            case "monster" -> net.minecraft.world.entity.monster.Monster.class;
            case "living", "any" -> LivingEntity.class;
            default -> throw new IllegalArgumentException(
                    "'" + key + "' expects one of player, mob, monster, living - got '" + value + "'");
        };
    }

    /**
     * Resolves an item id into an ingredient, for goals that are baited with an item.
     *
     * @param key argument name
     * @return matching ingredient, empty if unset
     */
    public Ingredient getIngredient(String key) {
        String value = this.values.get(key);
        if (value == null)
            return Ingredient.EMPTY;

        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null)
            throw new IllegalArgumentException("'" + value + "' is not a valid item id");

        return Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item '" + value + "'")));
    }

    /**
     * @return the difficulty predicate used by door-breaking goals
     */
    public static Predicate<net.minecraft.world.Difficulty> anyDifficulty() {
        return difficulty -> true;
    }
}
