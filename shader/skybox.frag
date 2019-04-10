#ifdef GL_ES
precision highp float;
#endif

//sampler
uniform sampler2D uSampler;
uniform samplerCube uCubeSampler;

//varying
varying vec2 vTextureCoord;
varying vec3 vVertexNormal;

void main(void)
{	
	gl_FragColor = textureCube(uCubeSampler, vVertexNormal);
}