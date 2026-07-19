package com.aicreatorlens.app.gl

object Shaders {

    // Shared vertex shader for all full-screen quad passes
    const val VERTEX_SHADER = """
        #version 300 es
        in vec2 aPosition;
        in vec2 aTexCoord;
        out vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """

    // Pass-through shader - used for comparison (original side)
    const val PASS_THROUGH_FRAG = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform samplerExternalOES uTexture;
        uniform mat4 uTextureMatrix;
        void main() {
            vec4 coord = uTextureMatrix * vec4(vTexCoord, 0.0, 1.0);
            fragColor = texture(uTexture, coord.xy);
        }
    """

    // Pass 1: Tone mapping (ACES filmic + exposure)
    // Input: OES camera texture
    // Output: FBO
    // uFlipX: 0.0 = no flip, 1.0 = mirror horizontally (front camera)
    const val TONE_MAPPING_FRAG = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform samplerExternalOES uTexture;
        uniform mat4 uTextureMatrix;
        uniform float uExposure;
        uniform float uToneMappingStrength;
        uniform float uFlipX;

        vec3 ACESFilm(vec3 x) {
            float a = 2.51;
            float b = 0.03;
            float c = 2.43;
            float d = 0.59;
            float e = 0.14;
            return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
        }

        void main() {
            vec4 coord = uTextureMatrix * vec4(vTexCoord, 0.0, 1.0);
            vec2 tc = coord.xy;
            // Horizontal flip for front camera (mirror mode)
            tc.x = mix(tc.x, 1.0 - tc.x, uFlipX);
            vec3 color = texture(uTexture, tc).rgb;
            color *= pow(2.0, uExposure);
            vec3 tonemapped = ACESFilm(color);
            color = mix(color, tonemapped, uToneMappingStrength);
            fragColor = vec4(color, 1.0);
        }
    """

    // Pass 2: Color profile (temperature, saturation, contrast, highlight rolloff, shadow recovery)
    // Input: 2D texture from FBO
    const val COLOR_PROFILE_FRAG = """
        #version 300 es
        precision highp float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform sampler2D uTexture;
        uniform float uColorTemperature;
        uniform float uSaturation;
        uniform float uContrastCurve;
        uniform float uHighlightRolloff;
        uniform float uShadowRecovery;

        vec3 applyTemperature(vec3 c, float temp) {
            vec3 warm = vec3(1.05, 0.97, 0.88);
            vec3 cool = vec3(0.92, 0.97, 1.08);
            vec3 shift = mix(cool, warm, temp);
            return c * shift;
        }

        vec3 applySaturation(vec3 c, float sat) {
            float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
            return mix(vec3(lum), c, sat);
        }

        vec3 sCurve(vec3 c, float amount) {
            float a = amount * 2.5;
            vec3 result;
            result.r = 0.5 + 0.5 * tanh(a * (c.r - 0.5));
            result.g = 0.5 + 0.5 * tanh(a * (c.g - 0.5));
            result.b = 0.5 + 0.5 * tanh(a * (c.b - 0.5));
            return mix(c, result, abs(amount));
        }

        vec3 rolloffHighlights(vec3 c, float amount) {
            float maxC = max(c.r, max(c.g, c.b));
            float threshold = mix(0.6, 0.95, amount);
            if (maxC > threshold) {
                float over = (maxC - threshold) / (1.001 - threshold);
                over = 1.0 - pow(over, mix(1.0, 0.3, amount));
                c *= (threshold + over * (1.001 - threshold)) / maxC;
            }
            return c;
        }

        vec3 recoverShadows(vec3 c, float amount) {
            float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
            float shadowMask = 1.0 - smoothstep(0.0, 0.3, lum);
            c += vec3(amount * 0.25) * shadowMask;
            return c;
        }

        void main() {
            vec3 color = texture(uTexture, vTexCoord).rgb;
            color = applyTemperature(color, uColorTemperature);
            color = applySaturation(color, uSaturation);
            color = sCurve(color, uContrastCurve);
            color = rolloffHighlights(color, uHighlightRolloff);
            color = recoverShadows(color, uShadowRecovery);
            fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }
    """

    // Pass 3: Skin processing + cinematic effects
    // Input: 2D texture from FBO
    const val SKIN_ENHANCE_FRAG = """
        #version 300 es
        precision highp float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform sampler2D uTexture;
        uniform float uSkinProtection;
        uniform float uFilmGrain;
        uniform float uVignette;
        uniform float uGlow;
        uniform float uSharpness;
        uniform vec2 uResolution;
        uniform float uTime;

        float isSkinTone(vec3 c) {
            float r = c.r, g = c.g, b = c.b;
            float maxC = max(r, max(g, b));
            float minC = min(r, min(g, b));
            bool rule1 = r > 0.35 && g > 0.15 && b > 0.05;
            bool rule2 = r > g && (r - g) > 0.04;
            bool rule3 = maxC - minC > 0.03 && maxC - minC < 0.5;
            bool rule4 = r < 0.95;
            return (rule1 && rule2 && rule3 && rule4) ? 1.0 : 0.0;
        }

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }

        void main() {
            vec3 color = texture(uTexture, vTexCoord).rgb;
            vec2 texel = 1.0 / uResolution;

            float skin = isSkinTone(color);
            float skinLum = dot(color, vec3(0.2126, 0.7152, 0.0722));
            vec3 neutralSkin = vec3(skinLum) * vec3(1.0, 0.85, 0.75);
            color = mix(color, mix(color, neutralSkin, 0.15), skin * uSkinProtection);

            if (uGlow > 0.01) {
                vec3 glowSample = vec3(0.0);
                float glowRadius = 4.0;
                for (float x = -2.0; x <= 2.0; x += 1.0) {
                    for (float y = -2.0; y <= 2.0; y += 1.0) {
                        float dist = length(vec2(x, y));
                        float weight = exp(-dist * dist / (glowRadius * glowRadius));
                        glowSample += texture(uTexture, vTexCoord + vec2(x, y) * texel * 2.0).rgb * weight;
                    }
                }
                glowSample /= 25.0;
                float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
                float glowMask = smoothstep(0.4, 0.9, lum);
                color = mix(color, color + glowSample * 0.3, uGlow * glowMask);
            }

            if (uFilmGrain > 0.01) {
                float grain = (hash(vTexCoord * uResolution + fract(uTime)) - 0.5) * uFilmGrain * 0.06;
                color += grain;
            }

            if (uVignette > 0.01) {
                vec2 uv = vTexCoord * 2.0 - 1.0;
                uv.x *= uResolution.x / uResolution.y;
                float vig = 1.0 - dot(uv, uv) * uVignette * 0.8;
                vig = clamp(vig, 0.0, 1.0);
                color *= vig;
            }

            fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }
    """

    // Final pass: comparison mode - left=original, right=processed
    // Uses both OES texture (original) and 2D texture (processed)
    // uFlipX is applied ONLY to the original OES texture (processed is already flipped in pass 1)
    const val COMPARISON_FRAG = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform samplerExternalOES uOriginalTexture;
        uniform sampler2D uProcessedTexture;
        uniform mat4 uTextureMatrix;
        uniform float uSplitPosition;
        uniform float uFlipX;

        void main() {
            vec2 coord = vTexCoord;

            vec3 original;
            vec3 processed;

            // Sample original (OES texture) — apply flip for front camera
            vec2 origCoord = coord;
            origCoord.x = mix(origCoord.x, 1.0 - origCoord.x, uFlipX);
            vec4 oesCoord = uTextureMatrix * vec4(origCoord, 0.0, 1.0);
            original = texture(uOriginalTexture, oesCoord.xy).rgb;

            // Sample processed (2D texture) — already flipped in pass 1, no flip needed
            processed = texture(uProcessedTexture, coord).rgb;

            // Split position accounts for mirror
            float splitX = uFlipX > 0.5 ? (1.0 - uSplitPosition) : uSplitPosition;
            if (coord.x < splitX) {
                fragColor = vec4(original, 1.0);
            } else {
                fragColor = vec4(processed, 1.0);
            }

            // Draw divider line
            float dist = abs(coord.x - splitX);
            if (dist < 0.002) {
                fragColor = vec4(1.0, 1.0, 1.0, 1.0);
            }
        }
    """

    // Standard 2D texture output pass (non-comparison, no flip — already applied in pass 1)
    const val OUTPUT_FRAG = """
        #version 300 es
        precision highp float;
        in vec2 vTexCoord;
        out vec4 fragColor;
        uniform sampler2D uTexture;

        void main() {
            vec3 color = texture(uTexture, vTexCoord).rgb;
            fragColor = vec4(color, 1.0);
        }
    """
}