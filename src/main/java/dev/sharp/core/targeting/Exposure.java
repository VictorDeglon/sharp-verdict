package dev.sharp.core.targeting;

/** How much of the locked target the player can actually hit from where they stand. */
public enum Exposure {
    /** Nothing in the way. */
    OPEN,
    /** The target holds a shield up, so the aim point sits on its back. */
    SHIELDED,
    /** Another hitbox covers part of the target, so the aim point moved to a part that is still exposed. */
    PARTLY_COVERED,
    /** Another hitbox covers every spot that could be aimed at. */
    COVERED,
    /** The player's eye is inside the target's hitbox. */
    INSIDE;

    /** True for the two states with no clean shot: the target is drawn purple. */
    public boolean blocksAim() {
        return this == COVERED || this == INSIDE;
    }
}
