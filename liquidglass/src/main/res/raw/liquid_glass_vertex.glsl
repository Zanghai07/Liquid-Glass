attribute vec4 a_Position;
attribute vec2 a_TexCoord;

varying vec2 v_TexCoord;
varying vec3 v_ViewPos;

void main() {
    v_TexCoord = a_TexCoord;
    // Approximating view position from the vertex coordinates for basic lighting setup
    v_ViewPos = vec3(a_Position.xy, 1.0);
    gl_Position = a_Position;
}
