attribute vec3 aVertexPosition;
attribute vec3 aVertexNormal;
attribute vec2 aVertexTextureCoords;

//matrices
uniform mat4 uMVMatrix;
uniform mat4 uPMatrix;
uniform mat4 uNMatrix;

//varyings
varying vec2 vTextureCoord;
varying vec3 vVertexNormal;

void main(void) {
	//Final vertex position
	gl_Position = uPMatrix * uMVMatrix * vec4(aVertexPosition, 1.0);
	vTextureCoord = aVertexTextureCoords;
	vVertexNormal = (uNMatrix * vec4(-aVertexPosition, 1.0)).xyz;
}	