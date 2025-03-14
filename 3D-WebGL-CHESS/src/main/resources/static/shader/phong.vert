attribute vec3 aVertexPosition;
attribute vec3 aVertexNormal;
attribute vec2 aTextureCoord;

uniform mat4 uMVMatrix;
uniform mat4 uPMatrix;
uniform mat3 uNMatrix;

uniform vec3 uLightPosition;

varying vec3 vNormal;
varying vec2 vTextureCoord;
varying vec3 vTransformedNormal;
varying vec4 vPosition;

varying vec3 vLightDir;
varying vec3 vEyeVec;

void main(void) {
	vec4 vertex = uMVMatrix * vec4(aVertexPosition, 1.0);


	vPosition = uMVMatrix * vec4(aVertexPosition, 1.0);
	gl_Position = uPMatrix * vPosition;


	//Vector Eye
	vEyeVec = -vec3(vertex.xyz);

	vLightDir =  uLightPosition -  vertex.xyz;

	vTextureCoord = aTextureCoord;
	vNormal = uNMatrix * aVertexNormal;
}