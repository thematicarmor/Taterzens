package org.samo_lego.taterzens.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.samo_lego.taterzens.Taterzens;
import org.samo_lego.taterzens.npc.TaterzenNPC;
import org.samo_lego.taterzens.npc.ai.AiGoalArgs;
import org.samo_lego.taterzens.npc.ai.AiGoalRegistry;
import org.samo_lego.taterzens.npc.ai.AiGoalType;
import org.samo_lego.taterzens.npc.ai.AppliedGoal;

import java.util.List;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static org.samo_lego.taterzens.Taterzens.MOD_ID;
import static org.samo_lego.taterzens.Taterzens.config;
import static org.samo_lego.taterzens.util.TextUtil.errorText;
import static org.samo_lego.taterzens.util.TextUtil.successText;

/**
 * Attaches vanilla mob AI goals to Taterzens.
 *
 * <p>Unlike the {@code /npc edit ...} tree, this addresses NPCs through a normal entity selector
 * rather than the executing player's selected Taterzen. That is deliberate: it makes the AI
 * scriptable from the server console, and lets one command reach a whole crowd of NPCs at once -
 * which is what a quick time event needs.
 *
 * <pre>
 * /npc ai @e[type=taterzens:npc,tag=guards] add panic speed=1.3 duration=100
 * /npc ai @e[type=taterzens:npc,distance=..20] add avoid_entity entity=player radius=12
 * /npc ai @e[type=taterzens:npc] clear
 * </pre>
 */
public class AiCommand {

    private static final SuggestionProvider<CommandSourceStack> GOAL_TYPES;

    public static void registerNode(LiteralCommandNode<CommandSourceStack> npcNode) {
        LiteralCommandNode<CommandSourceStack> aiNode = literal("ai")
                .requires(src -> Taterzens.getInstance().getPlatform().checkPermission(src, "taterzens.npc.ai", config.perms.npcCommandPermissionLevel))
                .build();

        LiteralCommandNode<CommandSourceStack> addNode = literal("add")
                .then(argument("goal", word())
                        .suggests(GOAL_TYPES)
                        .executes(context -> addGoal(context, ""))
                        .then(argument("args", greedyString())
                                .executes(context -> addGoal(context, StringArgumentType.getString(context, "args")))
                        )
                )
                .build();

        LiteralCommandNode<CommandSourceStack> removeNode = literal("remove")
                .then(argument("goal", word())
                        .suggests(GOAL_TYPES)
                        .executes(AiCommand::removeGoal)
                )
                .build();

        LiteralCommandNode<CommandSourceStack> clearNode = literal("clear")
                .executes(AiCommand::clearGoals)
                .build();

        LiteralCommandNode<CommandSourceStack> listNode = literal("list")
                .executes(AiCommand::listGoals)
                .build();

        // Targets first, so every action reads as "these NPCs, do this"
        var targetsNode = argument("targets", EntityArgument.entities()).build();
        targetsNode.addChild(addNode);
        targetsNode.addChild(removeNode);
        targetsNode.addChild(clearNode);
        targetsNode.addChild(listNode);

        aiNode.addChild(targetsNode);
        npcNode.addChild(aiNode);
    }

    private static int addGoal(CommandContext<CommandSourceStack> context, String rawArgs) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String goalId = StringArgumentType.getString(context, "goal");

        AiGoalType type = AiGoalRegistry.get(goalId).orElse(null);
        if (type == null) {
            source.sendFailure(errorText("taterzens.command.ai.error.unknown_goal", goalId));
            return 0;
        }

        int priority;
        int duration;
        try {
            AiGoalArgs parsed = AiGoalArgs.parse(rawArgs);
            priority = parsed.getInt("priority", type.defaultPriority());
            duration = parsed.getInt("duration", -1);
        } catch (IllegalArgumentException e) {
            source.sendFailure(errorText("taterzens.command.ai.error.bad_args", e.getMessage()));
            return 0;
        }

        List<TaterzenNPC> targets = taterzensFrom(context);
        if (targets.isEmpty()) {
            source.sendFailure(errorText("taterzens.command.ai.error.no_targets"));
            return 0;
        }

        int applied = 0;
        for (TaterzenNPC npc : targets) {
            try {
                npc.addAiGoal(type, priority, rawArgs, duration);
                ++applied;
            } catch (IllegalArgumentException e) {
                // One bad NPC shouldn't abort the rest of the crowd
                source.sendFailure(errorText("taterzens.command.ai.error.bad_args", e.getMessage()));
                return 0;
            }
        }

        int count = applied;
        source.sendSuccess(() -> successText("taterzens.command.ai.added",
                type.id().getPath(), String.valueOf(count)), false);
        return count;
    }

    private static int removeGoal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String goalId = StringArgumentType.getString(context, "goal");

        AiGoalType type = AiGoalRegistry.get(goalId).orElse(null);
        if (type == null) {
            source.sendFailure(errorText("taterzens.command.ai.error.unknown_goal", goalId));
            return 0;
        }

        int removed = 0;
        for (TaterzenNPC npc : taterzensFrom(context)) {
            if (npc.removeAiGoal(type))
                ++removed;
        }

        int count = removed;
        source.sendSuccess(() -> successText("taterzens.command.ai.removed",
                type.id().getPath(), String.valueOf(count)), false);
        return count;
    }

    private static int clearGoals(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        int cleared = 0;
        for (TaterzenNPC npc : taterzensFrom(context)) {
            cleared += npc.clearAiGoals();
        }

        int count = cleared;
        source.sendSuccess(() -> successText("taterzens.command.ai.cleared", String.valueOf(count)), false);
        return count;
    }

    private static int listGoals(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        List<TaterzenNPC> targets = taterzensFrom(context);

        for (TaterzenNPC npc : targets) {
            source.sendSuccess(() -> Component.literal(npc.getName().getString())
                    .withStyle(ChatFormatting.AQUA), false);

            if (npc.getAiGoals().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  -")
                        .withStyle(ChatFormatting.GRAY), false);
                continue;
            }

            for (AppliedGoal applied : npc.getAiGoals()) {
                String line = "  " + applied.type().id().getPath() +
                        " (priority " + applied.priority() + ")" +
                        (applied.isTemporary() ? " " + applied.remainingTicks() + "t left" : "") +
                        (applied.rawArgs().isBlank() ? "" : " " + applied.rawArgs());

                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            }
        }

        return targets.size();
    }

    /**
     * @return the Taterzens among the selected entities; other entities are ignored rather than
     *         being an error, so {@code @e[distance=..20]} works without a type filter
     */
    private static List<TaterzenNPC> taterzensFrom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getEntities(context, "targets").stream()
                .filter(entity -> entity instanceof TaterzenNPC)
                .map(entity -> (TaterzenNPC) entity)
                .collect(Collectors.toList());
    }

    static {
        GOAL_TYPES = SuggestionProviders.register(
                new ResourceLocation(MOD_ID, "ai_goal_types"),
                (context, builder) -> SharedSuggestionProvider.suggest(
                        AiGoalRegistry.values().stream()
                                .map(type -> type.id().getPath())
                                .collect(Collectors.toList()),
                        builder
                )
        );
    }
}
