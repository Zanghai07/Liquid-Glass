precision mediump float;

uniform sampler2D u_Texture;
// Direction of blur:
// (1.0/width, 0.0) for horizontal pass
// (0.0, 1.0/height) for vertical pass
uniform vec2 u_Direction;
uniform float u_BlurSize;

varying vec2 v_TexCoord;

void main() {
    vec4 color = vec4(0.0);
    
    // Optimized 9-tap Gaussian blur using linear filtering trick
    // Instead of 9 individual taps, we use 5 taps by leveraging 
    // GPU bilinear filtering — sampling between texels gets us 
    // a weighted average "for free"
    //
    // Kernel weights (sigma ≈ 2.0):
    // [0.0702, 0.3162, 0.2270, 0.3162, 0.0702]
    // 
    // Optimized offsets (bilinear trick):
    vec2 off1 = vec2(1.3846153846) * u_Direction * u_BlurSize;
    vec2 off2 = vec2(3.2307692308) * u_Direction * u_BlurSize;
    
    // Center tap
    color += texture2D(u_Texture, v_TexCoord) * 0.2270270270;
    
    // First pair of taps (offset ±1.38)
    color += texture2D(u_Texture, v_TexCoord + off1) * 0.3162162162;
    color += texture2D(u_Texture, v_TexCoord - off1) * 0.3162162162;
    
    // Second pair of taps (offset ±3.23)
    color += texture2D(u_Texture, v_TexCoord + off2) * 0.0702702703;
    color += texture2D(u_Texture, v_TexCoord - off2) * 0.0702702703;
    
    gl_FragColor = color;
}
