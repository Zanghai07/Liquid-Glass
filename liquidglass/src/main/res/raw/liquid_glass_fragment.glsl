precision mediump float;

uniform sampler2D u_BackgroundTexture;
uniform vec2 u_Resolution;
uniform float u_Time;

uniform float u_IOR;                   // e.g., 1.5 for glass
uniform float u_ChromaticAberration;   // 0.0 to 1.0
uniform float u_FresnelPower;          // Fresnel multiplier

// Device-driven lighting
uniform vec3 u_LightDir;               // Light direction, driven by gyro
uniform float u_Shininess;             // Specular shininess, e.g. 64.0
uniform float u_SpecularIntensity;     // Specular strength

// Procedural liquid
uniform float u_LiquidSpeed;
uniform float u_LiquidScale;
uniform float u_LiquidAmplitude;

// Edge glow
uniform vec4 u_EdgeGlowColor;
uniform float u_EdgeGlowIntensity;

// Color tint
uniform vec4 u_TintColor;
uniform float u_TintIntensity;

// Gaussian Blur
uniform float u_BlurRadius;

// Touch Ripple (supports up to 5 simultaneous ripples)
uniform vec2 u_RippleCenter0;
uniform float u_RippleTime0;
uniform vec2 u_RippleCenter1;
uniform float u_RippleTime1;
uniform vec2 u_RippleCenter2;
uniform float u_RippleTime2;
uniform vec2 u_RippleCenter3;
uniform float u_RippleTime3;
uniform vec2 u_RippleCenter4;
uniform float u_RippleTime4;
uniform float u_RippleAmplitude;
uniform int u_RippleCount;

varying vec2 v_TexCoord;
varying vec3 v_ViewPos;

// ============================================================================
// 3D Simplex Noise (Ashima Arts / Stefan Gustavson)
// Generates procedural animated normals for the "liquid" surface
// ============================================================================
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v) {
    const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
    const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy;
    vec3 x3 = x0 - D.yyy;

    // Permutations
    i = mod289(i);
    vec4 p = permute(permute(permute(
                i.z + vec4(0.0, i1.z, i2.z, 1.0))
              + i.y + vec4(0.0, i1.y, i2.y, 1.0))
              + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    // Gradients
    float n_ = 0.142857142857; // 1.0/7.0
    vec3 ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0) * 2.0 + 1.0;
    vec4 s1 = floor(b1) * 2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    // Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

// ============================================================================
// Generate dynamic surface normal from procedural noise
// This creates the "liquid" feeling — the surface is constantly deforming
// ============================================================================
vec3 getLiquidNormal(vec2 uv, float t) {
    float eps = 0.005;
    float speed = t * u_LiquidSpeed;
    float scale = u_LiquidScale;
    
    // Multi-octave noise for organic liquid movement
    float n_center = snoise(vec3(uv * scale, speed))
                   + 0.5 * snoise(vec3(uv * scale * 2.0, speed * 1.4))
                   + 0.25 * snoise(vec3(uv * scale * 4.0, speed * 1.8));
    float n_x = snoise(vec3((uv + vec2(eps, 0.0)) * scale, speed))
              + 0.5 * snoise(vec3((uv + vec2(eps, 0.0)) * scale * 2.0, speed * 1.4))
              + 0.25 * snoise(vec3((uv + vec2(eps, 0.0)) * scale * 4.0, speed * 1.8));
    float n_y = snoise(vec3((uv + vec2(0.0, eps)) * scale, speed))
              + 0.5 * snoise(vec3((uv + vec2(0.0, eps)) * scale * 2.0, speed * 1.4))
              + 0.25 * snoise(vec3((uv + vec2(0.0, eps)) * scale * 4.0, speed * 1.8));
    
    // Derive normal from height differences (central differences)
    vec3 normal = vec3(
        (n_x - n_center) / eps,
        (n_y - n_center) / eps,
        1.0
    );
    return normalize(normal);
}

// ============================================================================
// Calculate ripple displacement at a given UV position
// Uses damped wave physics: A * sin(k*r - ω*t) * e^(-γ_t * t) * e^(-γ_r * r)
// ============================================================================
float calculateRipple(vec2 uv, vec2 center, float time) {
    if (time <= 0.0) return 0.0;
    
    float dist = distance(uv, center);
    float frequency = 50.0;      // Spatial frequency (k)
    float speed = 15.0;          // Angular frequency (ω)
    float temporalDecay = 2.5;   // γ_t: how fast ripple dies over time
    float spatialDecay = 6.0;    // γ_r: how fast ripple dies over distance
    
    float wave = sin(dist * frequency - time * speed);
    float decay = exp(-time * temporalDecay) * exp(-dist * spatialDecay);
    
    return wave * decay * u_RippleAmplitude;
}

// ============================================================================
// Multi-tap blur approximation for frosted glass
// Uses a Poisson disc-like sampling pattern for quality
// ============================================================================
vec4 sampleBlurred(sampler2D tex, vec2 uv, float radius) {
    if (radius <= 0.01) return texture2D(tex, uv);
    
    vec2 px = 1.0 / max(u_Resolution, vec2(1.0));
    vec4 col = vec4(0.0);
    float totalWeight = 0.0;
    
    // 13-tap kernel: center + 12 samples in a Poisson-like pattern
    // Weights approximate a 2D Gaussian
    const float w0 = 0.15;  // center
    const float w1 = 0.10;  // inner ring (4 taps)
    const float w2 = 0.05;  // outer ring (8 taps)
    
    col += texture2D(tex, uv) * w0;
    totalWeight += w0;
    
    // Inner ring
    float r1 = radius * 0.5;
    col += texture2D(tex, uv + vec2( r1,  0.0) * px) * w1;
    col += texture2D(tex, uv + vec2(-r1,  0.0) * px) * w1;
    col += texture2D(tex, uv + vec2( 0.0,  r1) * px) * w1;
    col += texture2D(tex, uv + vec2( 0.0, -r1) * px) * w1;
    totalWeight += w1 * 4.0;
    
    // Outer ring
    float r2 = radius;
    float rd = r2 * 0.7071; // r2 / sqrt(2)
    col += texture2D(tex, uv + vec2( r2,  0.0) * px) * w2;
    col += texture2D(tex, uv + vec2(-r2,  0.0) * px) * w2;
    col += texture2D(tex, uv + vec2( 0.0,  r2) * px) * w2;
    col += texture2D(tex, uv + vec2( 0.0, -r2) * px) * w2;
    col += texture2D(tex, uv + vec2( rd,   rd) * px) * w2;
    col += texture2D(tex, uv + vec2(-rd,   rd) * px) * w2;
    col += texture2D(tex, uv + vec2( rd,  -rd) * px) * w2;
    col += texture2D(tex, uv + vec2(-rd,  -rd) * px) * w2;
    totalWeight += w2 * 8.0;
    
    return col / totalWeight;
}

void main() {
    vec2 uv = v_TexCoord;
    
    // ========================================================================
    // 1. RIPPLE EFFECT — Touch-driven damped waves
    // ========================================================================
    float totalRipple = 0.0;
    vec2 rippleNormalOffset = vec2(0.0);
    
    // Unrolled loop for ES 2.0 compatibility (no dynamic indexing of uniforms)
    if (u_RippleCount > 0) {
        float r = calculateRipple(uv, u_RippleCenter0, u_RippleTime0);
        totalRipple += r;
        rippleNormalOffset += r * normalize(uv - u_RippleCenter0 + vec2(0.0001));
    }
    if (u_RippleCount > 1) {
        float r = calculateRipple(uv, u_RippleCenter1, u_RippleTime1);
        totalRipple += r;
        rippleNormalOffset += r * normalize(uv - u_RippleCenter1 + vec2(0.0001));
    }
    if (u_RippleCount > 2) {
        float r = calculateRipple(uv, u_RippleCenter2, u_RippleTime2);
        totalRipple += r;
        rippleNormalOffset += r * normalize(uv - u_RippleCenter2 + vec2(0.0001));
    }
    if (u_RippleCount > 3) {
        float r = calculateRipple(uv, u_RippleCenter3, u_RippleTime3);
        totalRipple += r;
        rippleNormalOffset += r * normalize(uv - u_RippleCenter3 + vec2(0.0001));
    }
    if (u_RippleCount > 4) {
        float r = calculateRipple(uv, u_RippleCenter4, u_RippleTime4);
        totalRipple += r;
        rippleNormalOffset += r * normalize(uv - u_RippleCenter4 + vec2(0.0001));
    }
    
    // ========================================================================
    // 2. LIQUID SURFACE NORMAL — Procedural animated deformation
    // ========================================================================
    vec3 N = getLiquidNormal(uv, u_Time);
    
    // Add ripple perturbation to the liquid normal
    N.xy += rippleNormalOffset * 2.0;
    N = normalize(N);
    
    // Blend between flat surface and liquid normal based on amplitude
    // u_LiquidAmplitude controls how "wavy" the liquid surface is
    float amp = clamp(u_LiquidAmplitude * 40.0, 0.0, 1.0);
    N = normalize(mix(vec3(0.0, 0.0, 1.0), N, amp));
    
    // View vector (orthographic approximation — looking down +Z)
    vec3 V = vec3(0.0, 0.0, 1.0);
    
    // ========================================================================
    // 3. TRUE SNELL'S LAW REFRACTION — Not a fake offset!
    // Uses GLSL refract() which implements Snell's law:
    //   sin(θ_t) / sin(θ_i) = n_i / n_t
    // where θ_i is incidence angle, θ_t is transmission angle
    // ========================================================================
    float ior = max(u_IOR, 1.001);
    float eta = 1.0 / ior; // Air (n=1.0) to Glass (n=IOR)
    
    // ========================================================================
    // 4. CHROMATIC ABERRATION — Different IOR per RGB channel
    // Real glass disperses light: shorter wavelengths (blue) refract more
    // than longer wavelengths (red). This is called "dispersion".
    // We simulate this by using slightly different IOR per channel.
    // ========================================================================
    float dispersion = u_ChromaticAberration * 0.03;
    float etaR = 1.0 / (ior - dispersion);  // Red refracts less
    float etaG = eta;                         // Green is base
    float etaB = 1.0 / (ior + dispersion);  // Blue refracts more
    
    // Calculate refracted ray directions using Snell's law
    vec3 refractedR = refract(-V, N, etaR);
    vec3 refractedG = refract(-V, N, etaG);
    vec3 refractedB = refract(-V, N, etaB);
    
    // Handle total internal reflection (refract returns 0 when TIR occurs)
    if (length(refractedR) < 0.001) refractedR = reflect(-V, N);
    if (length(refractedG) < 0.001) refractedG = reflect(-V, N);
    if (length(refractedB) < 0.001) refractedB = reflect(-V, N);
    
    // Map refracted rays back to UV space
    // The glass has a virtual "thickness" that determines how much the
    // refraction displaces the sampled position
    float glassThickness = 0.15;
    vec2 uvR = clamp(uv + refractedR.xy * glassThickness, 0.0, 1.0);
    vec2 uvG = clamp(uv + refractedG.xy * glassThickness, 0.0, 1.0);
    vec2 uvB = clamp(uv + refractedB.xy * glassThickness, 0.0, 1.0);
    
    // ========================================================================
    // 5. BACKGROUND SAMPLING — With per-channel refraction + blur
    // ========================================================================
    vec4 color;
    color.r = sampleBlurred(u_BackgroundTexture, uvR, u_BlurRadius).r;
    color.g = sampleBlurred(u_BackgroundTexture, uvG, u_BlurRadius).g;
    color.b = sampleBlurred(u_BackgroundTexture, uvB, u_BlurRadius).b;
    color.a = 1.0;
    
    // ========================================================================
    // 6. COLOR TINTING — Glass can be tinted (e.g., blue for water)
    // ========================================================================
    if (u_TintIntensity > 0.001) {
        vec3 tint = u_TintColor.rgb;
        color.rgb = mix(color.rgb, color.rgb * tint, u_TintIntensity);
    }
    
    // ========================================================================
    // 7. FRESNEL EFFECT — Schlick's approximation
    // At normal incidence (looking straight at glass), most light passes through.
    // At grazing angles (edges), most light is reflected.
    // F(θ) = F0 + (1 - F0)(1 - cos θ)^5
    // where F0 = ((n1-n2)/(n1+n2))^2
    // ========================================================================
    float F0 = pow((1.0 - ior) / (1.0 + ior), 2.0);
    float cosTheta = max(dot(N, V), 0.0);
    float fresnel = F0 + (1.0 - F0) * pow(1.0 - cosTheta, u_FresnelPower);
    fresnel = clamp(fresnel, 0.0, 1.0);
    
    // ========================================================================
    // 8. EDGE GLOW — Light scattering at glass edges
    // Based on Fresnel: edges scatter more light, creating a subtle glow
    // ========================================================================
    vec3 edgeGlow = u_EdgeGlowColor.rgb * fresnel * u_EdgeGlowIntensity;
    
    // ========================================================================
    // 9. SPECULAR HIGHLIGHTS — Blinn-Phong model, sensor-driven
    // H = normalize(L + V) — the half-vector
    // spec = (N · H)^shininess
    // The light direction comes from the device's gyroscope/accelerometer,
    // so the specular highlight moves as you tilt the device!
    // ========================================================================
    vec3 L = normalize(u_LightDir);
    vec3 H = normalize(L + V);
    float NdotH = max(dot(N, H), 0.0);
    float spec = pow(NdotH, max(u_Shininess, 1.0));
    
    // Attenuate specular by Fresnel for energy conservation
    vec3 specularColor = vec3(spec) * u_SpecularIntensity * (1.0 + fresnel);
    
    // ========================================================================
    // 10. FINAL COMPOSITING
    // ========================================================================
    vec3 finalColor = color.rgb;
    
    // Add edge glow
    finalColor += edgeGlow;
    
    // Add specular highlights
    finalColor += specularColor;
    
    // Subtle environment reflection (approximated via Fresnel blend to white)
    finalColor = mix(finalColor, finalColor + vec3(0.1), fresnel * 0.3);
    
    // Ensure we don't exceed displayable range
    finalColor = clamp(finalColor, 0.0, 1.0);
    
    gl_FragColor = vec4(finalColor, 1.0);
}
