package com.kaius.mobpossession.possession;

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

	private PossessionManager() {
	}

	public static void start(UUID playerId, UUID mobId) {
		possessing.put(playerId, mobId);
	}

	public static void stop(UUID playerId) {
		possessing.remove(playerId);
	}

	public static boolean isPossessing(UUID playerId) {
		return possessing.containsKey(playerId);
	}

	public static UUID getPossessedMob(UUID playerId) {
		return possessing.get(playerId);
	}

	/** True if some player, anyone, is already possessing this mob. */
	public static boolean isMobPossessed(UUID mobId) {
		return possessing.containsValue(mobId);
	}

	/** If this mob is possessed, who's doing it? Returns null if nobody is. */
	public static UUID findPossessorOf(UUID mobId) {
		for (Map.Entry<UUID, UUID> entry : possessing.entrySet()) {
			if (entry.getValue().equals(mobId)) {
				return entry.getKey();
			}
		}
		return null;
	}
}
