package hideo.miyauchi.android.FlightSimulationWithARCore

import android.util.Log
import hideo.miyauchi.android.FlightSimulationWithARCore.Utility.Companion.dcos
import hideo.miyauchi.android.FlightSimulationWithARCore.Utility.Companion.dsin
import hideo.miyauchi.android.FlightSimulationWithARCore.Utility.TinyMatrixExcetpion
import hideo.miyauchi.android.FlightSimulationWithARCore.Utility.TinyMatrix

class FlightDynamics {

    private val _tick: Float = 0.1f
    private val _h0: Float = 1500f // altitude
    private val _v0: Float = 100f // velocity
    private val _theta0: Float = 7.5f // AOA
    private var _kn = TinyMatrix(12, 1)

    fun reset(): FloatArray {
        _kn = TinyMatrix(12, 1)
        return floatArrayOf(_kn[9], _kn[10], _kn[11] + _h0, // x, y, z
            _kn[6], _kn[7] + _theta0, _kn[8] // phi, theta, psi
        )
    }

    private fun diff_equation(k: TinyMatrix, elevator: Float, aileron: Float, throttle: Float): TinyMatrix {
        val v = k[0]
        val alpha = k[1]
        val beta = k[2]
        val p = k[3]
        val q = k[4]
        val r = k[5]
        val phi = k[6]
        val theta = k[7]
        val psi = k[8]

        // vertical equation
        val v_equation = TinyMatrix(4, 6, -0.0293f, -0.1059f, 0.0f, -0.1696f, 0.0f, 0.0000720f, -0.0961f, -0.7158f, 1.0f, -0.0121f, -0.1225f, -0.0f, -0.0023f, -0.99f, -0.6939f, -0.000289f, -0.8066f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f)
        val v_result = v_equation.dot(TinyMatrix(6, 1, v, alpha, q, theta, elevator, throttle))

        // horizontal equation
        val h_equation = TinyMatrix(4, 5, -0.0487f, 0.1309f, -1.0f, 0.0919f, 0.0f, -4.8504f, -1.6374f, -0.2038f, 0.0f, -2.6708f, 0.8636f, -0.1498f, -0.1919f, 0.0f, 0.1205f, 0.0f, 1.0f, 0.1317f, 0.0f, 0.0f)
        val h_result = h_equation.dot(TinyMatrix(5, 1, beta, p, r, phi, aileron))

        // rotate equation
        val r_equation = TinyMatrix(3, 3, dcos(theta) * dcos(psi), dsin(phi) * dsin(theta) * dcos(psi) - dcos(phi) * dsin(psi), dcos(phi) * dsin(theta) * dcos(psi) + dsin(phi) * dsin(psi), dcos(theta) * dsin(psi), dsin(phi) * dsin(theta) * dsin(psi) + dcos(phi) * dcos(psi), dcos(phi) * dsin(theta) * dsin(psi) - dsin(phi) * dcos(psi), dsin(theta), -dsin(phi) * dcos(theta), -dcos(phi) * dcos(theta))
        val r_param = TinyMatrix(3, 1, (v + _v0) * dcos(beta) * dcos(alpha), (v + _v0) * dsin(beta), (v + _v0) * dcos(beta) * dsin(alpha))
        val r_result = r_equation.dot(r_param)

        return TinyMatrix(12, 1, v_result[0], // v
            v_result[1], h_result[0], // alpha, beta
            h_result[1], v_result[2], h_result[2], // p, q, r
            h_result[3], v_result[3], r / dcos(_theta0), // phi, theta, psi
            r_result[0], r_result[1], r_result[2] // x, y, z
        )
    }

    fun solve(elevator: Float, aileron: Float, throttle: Float): FloatArray {
        try {
            _kn += diff_equation(_kn, elevator, aileron, throttle) * _tick
        } catch (e: TinyMatrixExcetpion) {
            Log.e(LOG_TAG, "TinyMatrixException", e)
        }

        return floatArrayOf(_kn[9], _kn[10], _kn[11] + _h0, // x, y, z
            _kn[6], _kn[7] + _theta0, _kn[8] // phi, theta, psi
        )
    }

    fun keepRollAngle(phi: Float): Float {
        val kphi = 1.0f
        val kp = 3.0f
        val j1 = kphi * (phi - _kn[6])
        val j2 = kp * _kn[3]
        return -j1 + j2
    }

    fun keepPitchAngle(theta: Float): Float {
        val ktheta = 1f
        val kq = 2f
        val j1 = ktheta * (theta - _kn[7])
        return kq * (-j1 + _kn[4])
    }

    fun keepVelocity(): Float {
        val kthrottle = -10000f
        val j1 = kthrottle * _kn[0]
        return j1
    }

    companion object {
        private val LOG_TAG = this::class.java.simpleName
    }
}