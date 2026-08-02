package com.zanghai.liquidglass.lib

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class for loading, compiling, and linking OpenGL ES 2.0 shaders.
 */
internal object ShaderUtils {
    private const val TAG = "LiquidGlass:Shader"

    /**
     * Reads a raw resource file as a string.
     */
    fun readRawResource(context: Context, resourceId: Int): String {
        val inputStream = context.resources.openRawResource(resourceId)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.appendLine(line)
        }
        reader.close()
        return sb.toString()
    }

    /**
     * Compiles a shader from source code.
     * @param type GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
     * @param source The GLSL source code
     * @return The shader handle, or 0 on failure
     */
    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type $type")
            return 0
        }

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    /**
     * Links vertex and fragment shaders into a program.
     * @return The program handle, or 0 on failure
     */
    fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create program")
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program linking failed: $log")
            GLES20.glDeleteProgram(program)
            return 0
        }

        return program
    }

    /**
     * Convenience: loads shaders from raw resources, compiles, and links.
     */
    fun loadProgram(context: Context, vertexResId: Int, fragmentResId: Int): Int {
        val vertexSource = readRawResource(context, vertexResId)
        val fragmentSource = readRawResource(context, fragmentResId)

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = createProgram(vertexShader, fragmentShader)

        // Shaders can be deleted after linking into a program
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    /**
     * Checks for GL errors and logs them.
     */
    fun checkGlError(operation: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$operation: GL error 0x${Integer.toHexString(error)}")
        }
    }
}
