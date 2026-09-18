package com.kaius.mobpossession.possession;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks who is possessing what.
 *
 * In-memory only (not saved to disk) - if the server restarts, possession
 * ends for everyone. Good enough for now; could upgrade to persistent
 * storage later if we ever need it to survive a restart.
 */
public class PossessionManager {

	// player UUID -> mob (entity) UUID they're possessing
	private static final Map<UUID, UUID> possessing = new HashMap<>();

	// player UUID -> the spot we parked their real body at while possessing
	private static final Map<UUID, Vec3> anchors = new HashMap<>();

	// player UUID -> their position last tick, so we can measure how far they
	// "tried" to walk this tick and hand that movement to the mob instead.
	private static final Map<UUID, Vec3> lastPositions = new HashMap<>();

	private PossessionManager() {
	}

	public static void start(UUID playerId, UUID mobId, Vec3 anchor) {
		possessing.put(playerId, mobId);
		anchors.put(playerId, anchor);
		lastPositions.put(playerId, anchor);
	}

	public static void stop(UUID playerId) {
		possessing.remove(playerId);
		anchors.remove(playerId);
		lastPositions.remove(playerId);
	}

	public static boolean isPossessing(UUID playerId) {
		return possessing.containsKey(playerId);
	}

	public static UUID getPossessedMob(UUID playerId) {
		return possessing.get(playerId);
	}

	public static Vec3 getAnchor(UUID playerId) {
		return anchors.get(playerId);
	}

	public static Vec3 getLastPosition(UUID playerId) {
		return lastPositions.get(playerId);
	}

	public static void setLastPosition(UUID playerId, Vec3 pos) {
		lastPositions.put(playerId, pos);
	}

	/** True if some player, anyone, is already possessing this mob. */
	public static boolean isMobPossessed(UUID mobId) {
		return possessing.containsValue(mobId);
	}
}
