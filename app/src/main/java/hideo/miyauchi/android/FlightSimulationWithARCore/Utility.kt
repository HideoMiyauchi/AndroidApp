package hideo.miyauchi.android.FlightSimulationWithARCore

import kotlin.math.*

class Utility {

    class TinyMatrixExcetpion(msg: String) : RuntimeException(msg)

    class TinyMatrix(row: Int, col: Int, vararg e: Float) {
        private var _row: Int = row
        private var _col: Int = col
        private var _e: FloatArray = FloatArray(_row * _col)

        init {
            for (i in e.indices) {
                _e[i] = e[i]
            }
            for (i in e.size until (_row * _col)) {
                _e[i] = 0.0f
            }
        }

        operator fun get(row: Int): Float {
            if (row !in 0 until _row) {
                throw TinyMatrixExcetpion("index out of range")
            }
            return this._e[row * _col]
        }

        operator fun times(v: Float): TinyMatrix {
            val newarray = FloatArray(_row * _col)
            for (i in newarray.indices) {
                newarray[i] = _e[i] * v
            }
            return TinyMatrix(_row, _col, *newarray)
        }

        operator fun div(v: Float): TinyMatrix {
            if (v == 0.0f) {
                throw TinyMatrixExcetpion("zero division")
            }
            val newarray = FloatArray(_row * _col)
            for (i in newarray.indices) {
                newarray[i] = _e[i] / v
            }
            return TinyMatrix(_row, _col, *newarray)
        }

        operator fun plus(v: TinyMatrix): TinyMatrix {
            if (_row != v._row || _col != v._col) {
                throw TinyMatrixExcetpion("different dimension")
            }
            val newarray = FloatArray(_row * _col)
            for (i in newarray.indices) {
                newarray[i] = _e[i] + v._e[i]
            }
            return TinyMatrix(_row, _col, *newarray)
        }

        fun dot(v: TinyMatrix): TinyMatrix {
            if (_col != v._row) {
                throw TinyMatrixExcetpion("different dimension")
            }
            val newarray = FloatArray(_row * v._col)
            for (i in 0 until _row) {
                for (j in 0 until v._col) {
                    var e = 0.0f
                    for (k in 0 until _col) {
                        e += _e[i * _col + k] * v._e[k * v._col + j]
                    }
                    newarray[i * v._col + j] = e
                }
            }
            return TinyMatrix(_row, v._col, *newarray)
        }
    }

    companion object {

        fun dsin(v: Float): Float {
            return sin(convertDegreeToRad(v))
        }

        private fun dasin(v: Float): Float {
            return convertRadToDegree(asin(v))
        }

        fun dcos(v: Float): Float {
            return cos(convertDegreeToRad(v))
        }

        private fun datan2(y: Float, x: Float): Float {
            return convertRadToDegree(atan2(y, x))
        }

        // radian → degree
        private fun convertRadToDegree(rad: Float): Float {
            return rad * 180f / 3.1415926535f
        }

        // degree → radian
        private fun convertDegreeToRad(degree: Float): Float {
            return degree * 3.1415926535f / 180f
        }

        // Quaternion → Euler (degree) (Y-X-Z)
        fun convertQuaternionToEuler(qw: Float, qx: Float, qy: Float, qz: Float) : Triple<Float, Float, Float> {
            val rotX = dasin(-(2f * qy * qz - 2f * qx * qw))
            return if (dcos(rotX) != 0f) {
                val rotY = datan2(2f * qx * qz + 2f * qy * qw, 2f * qw * qw + 2f * qz * qz - 1f)
                val rotZ = datan2(2f * qx * qy + 2f * qz * qw, 2f * qw * qw + 2f * qy * qy - 1f)
                Triple(rotX, rotY, rotZ)
            } else {
                val rotY = datan2(-(2f * qx * qz - 2f * qy * qw), 2f * qw * qw + 2f * qx * qx - 1f)
                val rotZ = 0f
                Triple(rotX, rotY, rotZ)
            }
        }

        // Euler(degree) → Rotate matrix
        fun convertEulerToRotateMatrix(x: Float, y:Float, z:Float) : FloatArray {

            // Step.1 Euler(degree) → Quaterion (Z-X-Y)
            val cx = dcos(x * 0.5f)
            val cy = dcos(y * 0.5f)
            val cz = dcos(z * 0.5f)
            val sx = dsin(x * 0.5f)
            val sy = dsin(y  * 0.5f)
            val sz = dsin(z * 0.5f)
            var qw = sx * sy * sz + cx * cy * cz
            var qx = cx * sy * sz + cy * cz * sx
            var qy = cx * cz * sy - cy * sx * sz
            var qz = cx * cy * sz - cz * sx * sy

            // Step.2 Normalize
            var norm: Float = sqrt(qw * qw + qx * qx + qy * qy + qz * qz)
            if (norm == 0.0f) {
                norm = 0.0001f // avoid zero division
            }
            qw /= norm
            qx /= norm
            qy /= norm
            qz /= norm

            // Step.3 Quaternion → Rotate matrix
            return floatArrayOf(
                1 - 2 * qy * qy - 2 * qz * qz,
                2 * qx * qy + 2 * qw * qz,
                2 * qx * qz - 2 * qw * qy,
                0.0f,
                2 * qx * qy - 2 * qw * qz,
                1 - 2 * qx * qx - 2 * qz * qz,
                2 * qy * qz + 2 * qw * qx,
                0.0f,
                2 * qx * qz + 2 * qw * qy,
                2 * qy * qz - 2 * qw * qx,
                1 - 2 * qx * qx - 2 * qy * qy,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f
            )
        }

    }
}
