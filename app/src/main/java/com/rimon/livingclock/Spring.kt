package com.rimon.depthlock

/**
 * Critically damped spring for one axis. Integrates with the real frame delta,
 * so motion timing is identical at 60, 90 or 120 Hz.
 */
class Spring(private val stiffness: Float = 180f) {
    var value = 0f
        private set
    private var velocity = 0f
    var target = 0f

    /** Advances by dtSeconds. Returns true while the spring is still moving. */
    fun step(dtSeconds: Float): Boolean {
        val dt = dtSeconds.coerceIn(0f, 0.05f)   // clamp so a stall never explodes
        val damping = 2f * kotlin.math.sqrt(stiffness)
        val force = -stiffness * (value - target) - damping * velocity
        velocity += force * dt
        value += velocity * dt
        val settled = kotlin.math.abs(value - target) < 0.0005f && kotlin.math.abs(velocity) < 0.0005f
        if (settled) {
            value = target
            velocity = 0f
        }
        return !settled
    }

    fun snapTo(v: Float) {
        value = v; target = v; velocity = 0f
    }
}
