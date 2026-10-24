package dev.sharp.core.debug;

import dev.sharp.core.activation.ActivationTracker;
import dev.sharp.core.camera.AimStep;
import dev.sharp.core.targeting.AimPointPicker;
import dev.sharp.core.targeting.Exposure;
import dev.sharp.core.targeting.Selection;

/**
 * Everything the debug panel and the log show about one frame. Plain data, no behaviour.
 *
 * @param active            whether the hold-to-lock feature is currently on
 * @param keyDown           whether the key is reported as held
 * @param armed             whether the next press would activate
 * @param lastOffReason     why the feature last turned off
 * @param activeSeconds     how long it has been on, 0 when off
 * @param gate              short text describing the single-player practice gate
 * @param selection         result of the latest selection pass, empty when off
 * @param targetLabel       name of the locked target, empty when none
 * @param switches          number of target switches since activation
 * @param trackingEnabled   whether camera tracking is switched on in the settings
 * @param aim               latest camera step, null when camera is not tracking
 * @param angularError      degrees between crosshair and aim point, 0 without a target
 * @param yawRate           camera yaw speed in degrees per second
 * @param pitchRate         camera pitch speed in degrees per second
 * @param inReach           whether the held weapon can reach the locked target from where the player stands
 * @param exposure          what is in the way of the locked target
 * @param zone              part of the body the aim point is on
 * @param shieldBlocksYou   the target holds a shield up and the player stands in front of it
 * @param othersInRange     other practice entities within the lock range
 * @param othersInReach     how many of those the held weapon can reach
 * @param keyHeldSeconds    how long the key has been down, 0 when it is up, whether or not the feature could start
 */
public record DebugSnapshot(
        boolean active,
        boolean keyDown,
        boolean armed,
        ActivationTracker.Reason lastOffReason,
        double activeSeconds,
        String gate,
        Selection selection,
        String targetLabel,
        int switches,
        boolean trackingEnabled,
        AimStep aim,
        double angularError,
        double yawRate,
        double pitchRate,
        boolean inReach,
        Exposure exposure,
        AimPointPicker.Zone zone,
        boolean shieldBlocksYou,
        int othersInRange,
        int othersInReach,
        double keyHeldSeconds) {
}
