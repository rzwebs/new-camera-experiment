package com.aicreatorlens.app.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.ui.screens.DebugLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ShaderPipeline {

    companion object {
        private const val OES_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    }

    // Full-screen quad vertices (position + texcoord)
    private val quadVertices = floatArrayOf(
        // x,    y,    u,    v
        -1.0f, -1.0f, 0.0f, 0.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 1.0f,
         1.0f,  1.0f, 1.0f, 1.0f,
    )
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadVertices)
            position(0)
        }

    // Shader programs
    private var passThroughProgram = 0
    private var toneMappingProgram = 0
    private var colorProfileProgram = 0
    private var skinEnhanceProgram = 0
    private var comparisonProgram = 0
    private var outputProgram = 0

    // FBOs for ping-pong rendering
    private var fboIds = IntArray(3)
    private var fboTextureIds = IntArray(3)
    private var fboWidth = 0
    private var fboHeight = 0

    // Camera resolution — FBOs are created at this size, not surface size
    private var cameraWidth = 0
    private var cameraHeight = 0

    // Attribute / Uniform locations
    private val locations = mutableMapOf<String, Int>()

    fun getProgramCount(): Int {
        var count = 0
        if (passThroughProgram != 0) count++
        if (toneMappingProgram != 0) count++
        if (colorProfileProgram != 0) count++
        if (skinEnhanceProgram != 0) count++
        if (comparisonProgram != 0) count++
        if (outputProgram != 0) count++
        return count
    }

    fun setCameraSize(width: Int, height: Int) {
        if (cameraWidth == width && cameraHeight == height) return
        DebugLog.log("PIPE", "setCameraSize: ${width}x${height}")
        cameraWidth = width
        cameraHeight = height
        // Force FBO recreation at new size
        fboWidth = 0
        fboHeight = 0
    }

    fun init() {
        DebugLog.log("SHADER", ">>> compiling shaders...")
        passThroughProgram = createProgram(Shaders.VERTEX_SHADER, Shaders.PASS_THROUGH_FRAG)
        DebugLog.log("SHADER", "  passThrough = $passThroughProgram")
        toneMappingProgram = createProgram(Shaders.VERTEX_SHADER, Shaders.TONE_MAPPING_FRAG)
        DebugLog.log("SHADER", "  toneMapping = $toneMappingProgram")
        colorProfileProgram = createProgram(Shaders.VERTEX_SHADER, Shaders.COLOR_PROFILE_FRAG)
        DebugLog.log("SHADER", "  colorProfile = $colorProfileProgram")
        skinEnhanceProgram = createProgram(Shaders.VERTEX_SHADER, Shaders.SKIN_ENHANCE_FRAG)
        DebugLog.log("SHADER", "  skinEnhance = $skinEnhanceProgram")
        comparisonProgram = createProgram(Shaders.VERTEX_SHADER, Shaders.COMPARISON_FRAG)
        DebugLog.log("SHADER", "  comparison = $comparisonProgram")
        outputProgram = createProgram(Shaders.VERTEX_SHADER, Shaders.OUTPUT_FRAG)
        DebugLog.log("SHADER", "  output = $outputProgram")
        DebugLog.log("SHADER", "<<< shader compilation done")
    }

    fun setupFramebuffers(width: Int, height: Int) {
        if (fboWidth == width && fboHeight == height) return
        fboWidth = width
        fboHeight = height

        // Delete old FBOs
        if (fboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(3, fboIds, 0)
            GLES30.glDeleteTextures(3, fboTextureIds, 0)
        }

        DebugLog.log("FBO", "creating FBOs at ${width}x${height}")
        // Create 3 FBOs for the 3-pass pipeline
        for (i in 0 until 3) {
            fboTextureIds[i] = createTexture(width, height)
            fboIds[i] = createFBO(fboTextureIds[i], width, height)
        }
    }

    fun render(
        cameraTextureId: Int,
        textureMatrix: FloatArray,
        width: Int,
        height: Int,
        params: CreatorEngine,
        comparisonMode: Boolean,
        comparisonSplit: Float,
        flipX: Int
    ) {
        // FBOs use camera resolution (not surface) to avoid stretching
        val fbW = if (cameraWidth > 0 && cameraHeight > 0) cameraWidth else width
        val fbH = if (cameraWidth > 0 && cameraHeight > 0) cameraHeight else height
        setupFramebuffers(fbW, fbH)

        // All FBO passes render at camera resolution
        GLES30.glViewport(0, 0, fbW, fbH)

        if (comparisonMode) {
            renderComparison(cameraTextureId, textureMatrix, fbW, fbH, width, height, params, comparisonSplit, flipX)
        } else {
            renderProcessed(cameraTextureId, textureMatrix, fbW, fbH, width, height, params, flipX)
        }
    }

    private fun renderProcessed(
        cameraTextureId: Int,
        textureMatrix: FloatArray,
        fbW: Int,
        fbH: Int,
        surfW: Int,
        surfH: Int,
        params: CreatorEngine,
        flipX: Int
    ) {
        val flipXf = if (flipX == 1) 1.0f else 0.0f

        // Pass 1: Tone Mapping (camera OES -> FBO 0) — includes flipX
        renderToFBO(
            program = toneMappingProgram,
            fboIndex = 0,
            inputTexture = cameraTextureId,
            inputTextureTarget = OES_TARGET,
            textureMatrix = textureMatrix
        ) {
            GLES30.glUniform1f(getLoc(toneMappingProgram, "uExposure"), params.exposure)
            GLES30.glUniform1f(getLoc(toneMappingProgram, "uToneMappingStrength"), params.toneMappingStrength)
            GLES30.glUniform1f(getLoc(toneMappingProgram, "uFlipX"), flipXf)
        }

        // Pass 2: Color Profile (FBO 0 -> FBO 1)
        renderToFBO(
            program = colorProfileProgram,
            fboIndex = 1,
            inputTexture = fboTextureIds[0],
            inputTextureTarget = GLES30.GL_TEXTURE_2D
        ) {
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uColorTemperature"), params.colorTemperature)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uSaturation"), params.saturation)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uContrastCurve"), params.contrastCurve)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uHighlightRolloff"), params.highlightRolloff)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uShadowRecovery"), params.shadowRecovery)
        }

        // Pass 3: Skin + Enhancement (FBO 1 -> FBO 2)
        val time = System.nanoTime().toFloat() / 1_000_000_000f
        renderToFBO(
            program = skinEnhanceProgram,
            fboIndex = 2,
            inputTexture = fboTextureIds[1],
            inputTextureTarget = GLES30.GL_TEXTURE_2D
        ) {
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uSkinProtection"), params.skinProtection)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uFilmGrain"), params.filmGrain)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uVignette"), params.vignette)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uGlow"), params.glow)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uSharpness"), params.sharpness)
            GLES30.glUniform2f(getLoc(skinEnhanceProgram, "uResolution"), fbW.toFloat(), fbH.toFloat())
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uTime"), time)
        }

        // Final blit: FBO 2 -> screen with aspect-ratio-corrected viewport
        blitToScreen(surfW, surfH, fbW, fbH, outputProgram, fboTextureIds[2])
    }

    private fun renderComparison(
        cameraTextureId: Int,
        textureMatrix: FloatArray,
        fbW: Int,
        fbH: Int,
        surfW: Int,
        surfH: Int,
        params: CreatorEngine,
        comparisonSplit: Float,
        flipX: Int
    ) {
        val flipXf = if (flipX == 1) 1.0f else 0.0f

        // Pass 1: Tone Mapping -> FBO 0 (includes flipX)
        renderToFBO(
            program = toneMappingProgram,
            fboIndex = 0,
            inputTexture = cameraTextureId,
            inputTextureTarget = OES_TARGET,
            textureMatrix = textureMatrix
        ) {
            GLES30.glUniform1f(getLoc(toneMappingProgram, "uExposure"), params.exposure)
            GLES30.glUniform1f(getLoc(toneMappingProgram, "uToneMappingStrength"), params.toneMappingStrength)
            GLES30.glUniform1f(getLoc(toneMappingProgram, "uFlipX"), flipXf)
        }

        // Pass 2: Color Profile -> FBO 1
        renderToFBO(
            program = colorProfileProgram,
            fboIndex = 1,
            inputTexture = fboTextureIds[0],
            inputTextureTarget = GLES30.GL_TEXTURE_2D
        ) {
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uColorTemperature"), params.colorTemperature)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uSaturation"), params.saturation)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uContrastCurve"), params.contrastCurve)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uHighlightRolloff"), params.highlightRolloff)
            GLES30.glUniform1f(getLoc(colorProfileProgram, "uShadowRecovery"), params.shadowRecovery)
        }

        // Pass 3: Skin + Enhancement -> FBO 2
        val time = System.nanoTime().toFloat() / 1_000_000_000f
        renderToFBO(
            program = skinEnhanceProgram,
            fboIndex = 2,
            inputTexture = fboTextureIds[1],
            inputTextureTarget = GLES30.GL_TEXTURE_2D
        ) {
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uSkinProtection"), params.skinProtection)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uFilmGrain"), params.filmGrain)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uVignette"), params.vignette)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uGlow"), params.glow)
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uSharpness"), params.sharpness)
            GLES30.glUniform2f(getLoc(skinEnhanceProgram, "uResolution"), fbW.toFloat(), fbH.toFloat())
            GLES30.glUniform1f(getLoc(skinEnhanceProgram, "uTime"), time)
        }

        // Final blit: comparison pass to screen with aspect-ratio-corrected viewport
        blitComparisonToScreen(surfW, surfH, fbW, fbH, cameraTextureId, textureMatrix, comparisonSplit, flipXf)
    }

    /**
     * Blit a single 2D texture to screen with aspect-ratio-corrected viewport.
     * Maintains camera's aspect ratio — adds letterbox/pillarbox as needed.
     */
    private fun blitToScreen(
        surfaceW: Int, surfaceH: Int,
        contentW: Int, contentH: Int,
        program: Int, textureId: Int
    ) {
        val vp = calculateViewport(surfaceW, surfaceH, contentW, contentH)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(vp[0], vp[1], vp[2], vp[3])
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        drawQuadSimple(program, textureId)
    }

    /**
     * Blit comparison (original OES + processed 2D) to screen with corrected viewport.
     */
    private fun blitComparisonToScreen(
        surfaceW: Int, surfaceH: Int,
        contentW: Int, contentH: Int,
        cameraTextureId: Int,
        textureMatrix: FloatArray,
        comparisonSplit: Float,
        flipXf: Float
    ) {
        val vp = calculateViewport(surfaceW, surfaceH, contentW, contentH)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(vp[0], vp[1], vp[2], vp[3])
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(comparisonProgram)

        val posLoc = GLES30.glGetAttribLocation(comparisonProgram, "aPosition")
        val texLoc = GLES30.glGetAttribLocation(comparisonProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glEnableVertexAttribArray(texLoc)
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(0)

        // Bind OES texture to unit 0 (original) with flipX
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(OES_TARGET, cameraTextureId)
        GLES30.glUniform1i(getLoc(comparisonProgram, "uOriginalTexture"), 0)
        GLES30.glUniformMatrix4fv(getLoc(comparisonProgram, "uTextureMatrix"), 1, false, textureMatrix, 0)

        // Bind processed texture to unit 1 (2D, already flipped in pass 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextureIds[2])
        GLES30.glUniform1i(getLoc(comparisonProgram, "uProcessedTexture"), 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        GLES30.glUniform1f(getLoc(comparisonProgram, "uSplitPosition"), comparisonSplit)
        GLES30.glUniform1f(getLoc(comparisonProgram, "uFlipX"), flipXf)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    /**
     * Calculate viewport that fits content (camera) into surface without stretching.
     * Returns [x, y, width, height].
     */
    private fun calculateViewport(surfaceW: Int, surfaceH: Int, contentW: Int, contentH: Int): IntArray {
        val contentAspect = contentW.toFloat() / contentH.toFloat()
        val surfaceAspect = surfaceW.toFloat() / surfaceH.toFloat()

        val vpW: Int
        val vpH: Int
        val vpX: Int
        val vpY: Int

        if (surfaceAspect > contentAspect) {
            // Surface is wider than content — pillarbox (black bars left/right)
            vpW = (surfaceH * contentAspect).toInt()
            vpH = surfaceH
            vpX = (surfaceW - vpW) / 2
            vpY = 0
        } else {
            // Surface is taller than content — letterbox (black bars top/bottom)
            vpW = surfaceW
            vpH = (surfaceW / contentAspect).toInt()
            vpX = 0
            vpY = (surfaceH - vpH) / 2
        }

        return intArrayOf(vpX, vpY, vpW, vpH)
    }

    /**
     * Simple quad draw for a single 2D texture (no texture matrix, no extra uniforms).
     */
    private fun drawQuadSimple(program: Int, textureId: Int) {
        GLES30.glUseProgram(program)

        val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glEnableVertexAttribArray(texLoc)
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(getLoc(program, "uTexture"), 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    private fun renderToFBO(
        program: Int,
        fboIndex: Int,
        inputTexture: Int,
        inputTextureTarget: Int = GLES30.GL_TEXTURE_2D,
        textureMatrix: FloatArray? = null,
        uniformSetup: () -> Unit
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[fboIndex])
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        drawQuad(program, inputTexture, inputTextureTarget, textureMatrix, uniformSetup)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun drawQuad(
        program: Int,
        textureId: Int,
        textureTarget: Int,
        textureMatrix: FloatArray?,
        uniformSetup: () -> Unit
    ) {
        GLES30.glUseProgram(program)

        val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glEnableVertexAttribArray(texLoc)
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(0)

        val texUniform = getLoc(program, "uTexture")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(textureTarget, textureId)
        GLES30.glUniform1i(texUniform, 0)

        if (textureMatrix != null) {
            val tmLoc = getLoc(program, "uTextureMatrix")
            if (tmLoc >= 0) {
                GLES30.glUniformMatrix4fv(tmLoc, 1, false, textureMatrix, 0)
            }
        }

        uniformSetup()

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    private fun getLoc(program: Int, name: String): Int {
        val key = "$program:$name"
        return locations.getOrPut(key) {
            GLES30.glGetUniformLocation(program, name)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            GLES30.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createTexture(width: Int, height: Int): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return texId
    }

    private fun createFBO(textureId: Int, width: Int, height: Int): Int {
        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        val fboId = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textureId, 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            DebugLog.log("FBO", "FBO $fboId NOT COMPLETE! status=0x${status.toString(16)}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return fboId
    }

    fun release() {
        if (fboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(3, fboIds, 0)
            GLES30.glDeleteTextures(3, fboTextureIds, 0)
        }
        GLES30.glDeleteProgram(passThroughProgram)
        GLES30.glDeleteProgram(toneMappingProgram)
        GLES30.glDeleteProgram(colorProfileProgram)
        GLES30.glDeleteProgram(skinEnhanceProgram)
        GLES30.glDeleteProgram(comparisonProgram)
        GLES30.glDeleteProgram(outputProgram)
    }
}