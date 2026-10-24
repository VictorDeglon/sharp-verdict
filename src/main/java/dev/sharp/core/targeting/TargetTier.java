package dev.sharp.core.targeting;

/** What kind of thing a candidate is. Earlier constants are preferred; see {@link TargetingSettings#penalty}. */
public enum TargetTier {
    /** Players and player-shaped bodies: mannequins, fake-player bots. */
    PLAYER,
    ARMOR_STAND,
    /** Animals, and every other living entity. */
    OTHER
}
