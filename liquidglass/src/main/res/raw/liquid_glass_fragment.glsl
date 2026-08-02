precision mediump float;

uniform sampler2D u_BackgroundTexture;
uniform vec2 u_Resolution;

uniform float u_IOR;
uniform float u_ChromaticAberration;
uniform vec3 u_LightDir;
uniform float u_Shininess;
uniform float u_SpecularIntensity;
uniform float u_LiquidAmplitude;
uniform float u_CornerRadius; // Corner radius in pixels

varying vec2 v_TexCoord;

// Signed Distance Field for a Rounded Rectangle
// p: coordinates from center, b: half-width and half-height, r: corner radius
float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 uv = v_TexCoord;
    
    // Convert to pixel coordinates relative to the center of the view
    vec2 p = (uv - 0.5) * u_Resolution;
    vec2 b = u_Resolution * 0.5;
    
    // Ensure corner radius is not larger than half of the smallest dimension
    float r = min(u_CornerRadius, min(b.x, b.y));
    
    // Distance to the edge of the rounded rectangle
    // Negative d means we are inside the shape, positive d means outside
    float d = sdRoundRect(p, b, r);
    
    // Anti-aliased edge to hide jagged pixels on the boundary
    float alpha = smoothstep(1.0, -1.0, d);
    
    // If completely outside, discard (transparent)
    if (alpha <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }
    
    // We want the surface to look like a curved glass lens.
    // The curve only happens near the edges. We define a "bevel width".
    // For a circle or pill (where r is large), the entire surface is curved.
    float bevelWidth = max(r, min(b.x, b.y)); 
    if (bevelWidth < 1.0) bevelWidth = min(b.x, b.y);
    
    // distInwards is how deep we are inside the shape
    float distInwards = max(-d, 0.0);
    
    // Numerical differentiation to find the normal vector of the 3D surface.
    // Instead of explicitly calculating the Z height, we sample the SDF slightly offset
    // to get the slope of the surface.
    float eps = 1.0; // 1 pixel delta for normal calculation
    float dX = sdRoundRect(p + vec2(eps, 0.0), b, r);
    float dY = sdRoundRect(p + vec2(0.0, eps), b, r);
    
    // Calculate normalized heights (0 at edge, 1 at bevelWidth inward)
    float h  = clamp(max(-d, 0.0) / bevelWidth, 0.0, 1.0);
    float hX = clamp(max(-dX, 0.0) / bevelWidth, 0.0, 1.0);
    float hY = clamp(max(-dY, 0.0) / bevelWidth, 0.0, 1.0);
    
    // Spherical curve formula: Z = sqrt(1 - (1-h)^2)
    float z  = sqrt(max(1.0 - (1.0 - h)*(1.0 - h), 0.0));
    float zX = sqrt(max(1.0 - (1.0 - hX)*(1.0 - hX), 0.0));
    float zY = sqrt(max(1.0 - (1.0 - hY)*(1.0 - hY), 0.0));
    
    // Compute the 3D normal from the height gradients.
    // A standard heightmap normal is vec3(-dZ/dX, -dZ/dY, 1.0)
    // dZ/dX = (zX - z) * bevelWidth / eps
    vec3 N = normalize(vec3((z - zX) * bevelWidth, (z - zY) * bevelWidth, eps));
    
    // View vector pointing straight at the screen
    vec3 V = vec3(0.0, 0.0, 1.0);
    
    // --- REFRACTION (Snell's Law) ---
    float ior = max(u_IOR, 1.001);
    float eta = 1.0 / ior;
    
    // Chromatic dispersion for R, G, B channels
    float dispersion = u_ChromaticAberration * 0.1;
    float etaR = 1.0 / (ior - dispersion);
    float etaG = eta;
    float etaB = 1.0 / (ior + dispersion);
    
    vec3 refrR = refract(-V, N, etaR);
    vec3 refrG = refract(-V, N, etaG);
    vec3 refrB = refract(-V, N, etaB);
    
    if (length(refrR) < 0.001) refrR = reflect(-V, N);
    if (length(refrG) < 0.001) refrG = reflect(-V, N);
    if (length(refrB) < 0.001) refrB = reflect(-V, N);
    
    // u_LiquidAmplitude acts as the thickness/intensity of the distortion
    float thickness = max(u_LiquidAmplitude * 10.0, 0.1);
    
    vec2 uvR = clamp(uv + refrR.xy * thickness, 0.0, 1.0);
    vec2 uvG = clamp(uv + refrG.xy * thickness, 0.0, 1.0);
    vec2 uvB = clamp(uv + refrB.xy * thickness, 0.0, 1.0);
    
    float red   = texture2D(u_BackgroundTexture, uvR).r;
    float green = texture2D(u_BackgroundTexture, uvG).g;
    float blue  = texture2D(u_BackgroundTexture, uvB).b;
    vec3 color = vec3(red, green, blue);
    
    // --- LIGHTING & SHADING ---
    // Darken edges slightly (inner shadow) based on height `z`
    color *= mix(0.5, 1.0, z);
    
    // Specular Highlight
    vec3 L = normalize(vec3(-1.0, 1.0, 1.0)); // Default light source
    if (length(u_LightDir) > 0.1) {
        L = normalize(mix(L, u_LightDir, 0.5)); // Blend with gyro
    }
    
    vec3 H = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), max(u_Shininess, 10.0)) * u_SpecularIntensity;
    
    // Edge glow on the opposite side
    float edgeGlow = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.3;
    
    color += vec3(spec);
    color += vec3(edgeGlow);
    
    gl_FragColor = vec4(color, alpha);
}
