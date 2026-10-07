package com.reggarf.mods.transferables.content.portal;

/**
 * Guards portal auto-placement like Create's portal-shaped tracks:
 * while an exit is being set, {@code connectToPortal} must not run (neighbor updates
 * during {@code setBlock} would otherwise place more exits — especially on wide portals).
 */
public final class PortalPlacement {
	private static final ThreadLocal<Integer> SUPPRESS_DEPTH = ThreadLocal.withInitial(() -> 0);

	private PortalPlacement() {}

	public static boolean isSuppressed() {
		return SUPPRESS_DEPTH.get() > 0;
	}

	public static void runSuppressed(Runnable action) {
		SUPPRESS_DEPTH.set(SUPPRESS_DEPTH.get() + 1);
		try {
			action.run();
		} finally {
			int depth = SUPPRESS_DEPTH.get() - 1;
			if (depth <= 0)
				SUPPRESS_DEPTH.remove();
			else
				SUPPRESS_DEPTH.set(depth);
		}
	}
}
