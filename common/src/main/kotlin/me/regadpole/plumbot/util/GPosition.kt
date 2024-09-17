package me.regadpole.plumbot.util

class GPosition(var world: String, var x: Double, var y: Double, var z: Double, var yaw: Float, var pitch: Float) {
    fun toXYZ(split: String): String {
        return "$x$split$y$split$z"
    }
}