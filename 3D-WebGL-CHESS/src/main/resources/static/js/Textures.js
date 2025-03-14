Array.prototype.isArray = true;
var Texture = {
	
		textures:  [],
		
		handler:
			function(texture) {
				gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
				gl.bindTexture(gl.TEXTURE_2D, texture);
				gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, texture.image);
				gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
				gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_NEAREST);
				gl.generateMipmap(gl.TEXTURE_2D);

				gl.bindTexture(gl.TEXTURE_2D, null);
			},
		
		loadTexture:
			function(src){
				let tex = gl.createTexture();
				tex.image = new Image();
				tex.image.onload = function () {
					Texture.handler(tex);
				}
				tex.image.src = src;
				Texture.textures.push(tex);
			},
		
		init : 
			function(src)
			{
				if(src.isArray)
					src.forEach(Texture.loadTexture);
				else 
					Texture.loadTexture(src);
				console.log(Texture.textures);
			},
			
		loadCubeFace : function (gl, target, t, url){
			
			var image = new Image();
			image.onload = function(){
				
				gl.bindTexture(gl.TEXTURE_CUBE_MAP , t);
				gl.texImage2D(target, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
				gl.bindTexture(gl.TEXTURE_CUBE_MAP, null);
			}
			image.src = url;
			
			
		},
		cubeMapTexture : function(texs){
				
			this.skybox = gl.createTexture();
			
			gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
			gl.bindTexture(gl.TEXTURE_CUBE_MAP, this.skybox);
			gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
			gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
			gl.texParameteri(gl.TEXTURE_CUBE_MAP , gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
			gl.texParameteri(gl.TEXTURE_CUBE_MAP , gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
			gl.texParameteri(gl.TEXTURE_CUBE_MAP , gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE);
			
			this.loadCubeFace(gl, gl.TEXTURE_CUBE_MAP_POSITIVE_X, this.skybox, texs[2]);
			this.loadCubeFace(gl, gl.TEXTURE_CUBE_MAP_NEGATIVE_X, this.skybox, texs[3]);
			this.loadCubeFace(gl, gl.TEXTURE_CUBE_MAP_POSITIVE_Y, this.skybox, texs[4]);
			this.loadCubeFace(gl, gl.TEXTURE_CUBE_MAP_NEGATIVE_Y, this.skybox, texs[5]);
			this.loadCubeFace(gl, gl.TEXTURE_CUBE_MAP_POSITIVE_Z, this.skybox, texs[0]);
			this.loadCubeFace(gl, gl.TEXTURE_CUBE_MAP_NEGATIVE_Z, this.skybox, texs[1]);
			console.log(this.skybox)
		}
		
	
	
	
}