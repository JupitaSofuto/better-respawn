package de.maxhenkel.betterrespawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;

public class RespawnManager {

    private static final int FIND_SPAWN_ATTEMPTS = 16;

    private final Random random;

    public RespawnManager() {
        random = new Random();
    }

    public void onPlayerDeath(ServerPlayer player) {
        if (!(player.getAbilities() instanceof RespawnAbilities respawnAbilities)) {
            return;
        }

        respawnAbilities.setRespawnDimension(player.getRespawnDimension());
        respawnAbilities.setRespawnPos(player.getRespawnPosition());
        respawnAbilities.setRespawnAngle(player.getRespawnAngle());
        respawnAbilities.setRespawnForced(player.isRespawnForced());


        ServerLevel respawnDimension = player.getServer().getLevel(player.getRespawnDimension());
        BlockPos respawnLocation = player.getRespawnPosition();
        float respawnAngle = player.getRespawnAngle();

        if (respawnLocation != null) {
            Optional<Vec3> respawnVector = Player.findRespawnPositionAndUseSpawnBlock(respawnDimension == null ? player.getLevel() : respawnDimension, respawnLocation, respawnAngle, false, true);
            if (respawnVector.isPresent()) {
                Vec3 spawn = respawnVector.get();
                if (respawnDimension == player.getLevel() && player.blockPosition().distManhattan(new Vec3i(spawn.x, spawn.y, spawn.z)) <= BetterRespawnMod.SERVER_CONFIG.respawnBlockRange.get()) {
                    BetterRespawnMod.LOGGER.info("Player {} is within the range of its respawn block", player.getName().getString());
                    return;
                }
            }
        }

        if (player.getLevel().dimensionType().hasCeiling() || !player.getLevel().dimensionType().bedWorks()) {
            BetterRespawnMod.LOGGER.info("Can't respawn {} in {}", player.getName().getString(), player.getLevel().dimension().location());
            return;
        }

        BlockPos respawnPos = findValidRespawnLocation(player.getLevel(), player.blockPosition());

        if (respawnPos == null) {
            return;
        }

        player.setRespawnPosition(player.level.dimension(), respawnPos, 0F, true, true);
        BetterRespawnMod.LOGGER.info("Set temporary respawn location to [{}, {}, {}]", respawnPos.getX(), respawnPos.getY(), respawnPos.getZ());
    }

    public void onSetRespawnPosition(ServerPlayer player, ResourceKey<Level> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean showMessage) {
        if (forced) {
            return;
        }

        if (!(player.getAbilities() instanceof RespawnAbilities abilities)) {
            return;
        }

        if (pos == null) {
            dimension = Level.OVERWORLD;
        }

        abilities.setRespawnDimension(dimension);
        abilities.setRespawnPos(pos);
        abilities.setRespawnAngle(angle);
        abilities.setRespawnForced(forced);

        if (pos != null) {
            BetterRespawnMod.LOGGER.info("Updating the respawn location of player {} to [{}, {}, {}] in {}", player.getName().getString(), pos.getX(), pos.getY(), pos.getZ(), dimension.location());
        } else {
            BetterRespawnMod.LOGGER.info("Updating the respawn location of player {} to [NONE]", player.getName().getString());
        }
    }

    @Nullable
    public BlockPos findValidRespawnLocation(ServerLevel world, BlockPos deathLocation) {
        int min = BetterRespawnMod.SERVER_CONFIG.minRespawnDistance.get();
        int max = BetterRespawnMod.SERVER_CONFIG.maxRespawnDistance.get();

        BlockPos pos = null;
        for (int i = 0; i < FIND_SPAWN_ATTEMPTS && pos == null; i++) {
            BetterRespawnMod.LOGGER.info("Searching for respawn location - Attempt {}/{}", i + 1, FIND_SPAWN_ATTEMPTS);
            pos = PlayerRespawnLogic.getSpawnPosInChunk(world, new ChunkPos(new BlockPos(getRandomRange(deathLocation.getX(), min, max), 0, getRandomRange(deathLocation.getZ(), min, max))));
            if (pos != null && !world.getWorldBorder().isWithinBounds(pos)) {
                pos = null;
            }
        }
        if (pos == null) {
            BetterRespawnMod.LOGGER.info("Found no valid respawn location after {} attempts", FIND_SPAWN_ATTEMPTS);
        } else {
            BetterRespawnMod.LOGGER.info("Found valid respawn location: [{}, {}, {}]", pos.getX(), pos.getY(), pos.getZ());
        }
        return pos;
    }

    private int getRandomRange(int actual, int minDistance, int maxDistance) {
        return actual + (random.nextBoolean() ? -1 : 1) * (minDistance + random.nextInt(maxDistance - minDistance));
    }

}
