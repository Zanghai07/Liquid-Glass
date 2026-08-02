precision mediump float;

uniform sampler2D u_BackgroundTexture;
uniform vec2 u_Resolution;

uniform float u_IOR;                   // Refraction index (e.g. 1.2)
uniform float u_ChromaticAberration;   // Dispersion strength (e.g. 0.05)
uniform vec3 u_LightDir;               // For the specular highlight
uniform float u_Shininess;
uniform float u_SpecularIntensity;
uniform float u_LiquidAmplitude;       // Used here as "lens thickness" or bulge amount

varying vec2 v_TexCoord;

void main() {
    vec2 uv = v_TexCoord;
    
    // We want to create a spherical lens / liquid drop in the center of the view.
    // Convert UV to -1.0 to +1.0 space
    vec2 p = uv * 2.0 - 1.0;
    
    // Correct for aspect ratio so the drop is perfectly round, not stretched
    float aspect = u_Resolution.x / u_Resolution.y;
    vec2 p_aspect = p;
    p_aspect.x *= aspect;
    
    float r2 = dot(p_aspect, p_aspect);
    
    // Anti-aliased circle edge
    float alpha = smoothstep(1.0, 0.95, r2);
    
    // If we are completely outside the circle, discard/make transparent
    if (alpha <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }
    
    // Calculate the normal of the hemisphere (the 3D shape of the water drop)
    // z = sqrt(1 - x^2 - y^2)
    float z = sqrt(max(1.0 - r2, 0.0));
    vec3 N = normalize(vec3(p_aspect.x, p_aspect.y, z));
    
    // View vector (looking straight at the screen)
    vec3 V = vec3(0.0, 0.0, 1.0);
    
    // Base Index of Refraction
    float ior = max(u_IOR, 1.001);
    float eta = 1.0 / ior;
    
    // Chromatic Aberration: red refracts less, blue refracts more
    float dispersion = u_ChromaticAberration * 0.1;
    float etaR = 1.0 / (ior - dispersion);
    float etaG = eta;
    float etaB = 1.0 / (ior + dispersion);
    
    // Calculate refracted rays
    vec3 refrR = refract(-V, N, etaR);
    vec3 refrG = refract(-V, N, etaG);
    vec3 refrB = refract(-V, N, etaB);
    
    // Fallback for Total Internal Reflection
    if (length(refrR) < 0.001) refrR = reflect(-V, N);
    if (length(refrG) < 0.001) refrG = reflect(-V, N);
    if (length(refrB) < 0.001) refrB = reflect(-V, N);
    
    // Map the refracted rays back to UV space to sample the background.
    // u_LiquidAmplitude acts as the thickness/depth of the lens.
    float thickness = max(u_LiquidAmplitude * 10.0, 0.1);
    
    // Important: we sample from the original UV center, but offset by the refraction
    vec2 uvR = clamp(uv + refrR.xy * thickness, 0.0, 1.0);
    vec2 uvG = clamp(uv + refrG.xy * thickness, 0.0, 1.0);
    vec2 uvB = clamp(uv + refrB.xy * thickness, 0.0, 1.0);
    
    // Sample the background texture for each color channel
    float r = texture2D(u_BackgroundTexture, uvR).r;
    float g = texture2D(u_BackgroundTexture, uvG).g;
    float b = texture2D(u_BackgroundTexture, uvB).b;
    
    vec3 color = vec3(r, g, b);
    
    // Darken the edges slightly to give a sense of volume/depth (inner shadow)
    float edgeDarkening = smoothstep(0.6, 1.0, r2);
    color *= mix(1.0, 0.6, edgeDarkening);
    
    // Specular Highlight (the glossy reflection on the top of the liquid drop)
    vec3 L = normalize(vec3(-1.0, 1.0, 1.0)); // Light from top-left
    
    // If sensor is enabled, we can blend the static light with the gyro light
    if (length(u_LightDir) > 0.1) {
        L = normalize(mix(L, u_LightDir, 0.5));
    }
    
    vec3 H = normalize(L + V);
    float NdotH = max(dot(N, H), 0.0);
    float spec = pow(NdotH, max(u_Shininess, 10.0)) * u_SpecularIntensity;
    
    // Add the specular highlight to the color
    color += vec3(spec);
    
    // Edge highlight (subtle glow on the opposite side of the light)
    float edgeGlow = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.3;
    color += vec3(edgeGlow);
    
    gl_FragColor = vec4(color, alpha);
}
