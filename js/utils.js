 function figureSide(name){

		var matches = name.match(/\d+$/);
		if(name.includes("Pawn")){
			
			if(matches == null || parseInt(matches[0]) < 8){
				return 1;
			}
			else return 2;
		}
		else if(name.includes("King")){
			
			if(matches == null)
				return 1
			return 2;
		}
		else if(name.includes("Queen")){
			if(matches == null){
				return 1;

			}
			return 2;
		}
		else if(name.includes("Rook"))
		{
			if(matches == null || 1 == parseInt(matches[0]) )
				return 1
			return 2;
		}
		else if(name.includes("Bishop")){
			if(matches == null || 1 == parseInt(matches[0]) )
				return 1
			return 2;
		}
		else if(name.includes("Knight")){
			if(matches == null || 1 == parseInt(matches[0]) )
				return 1
			return 2;
		}
		return 0;
	}

var lastTime;
var utils = {

	getShader : function (gl, id) {
		var shaderScript = document.getElementById(id);
		if (!shaderScript) {
			console.log("here");
			return null;
		}

		var str = "";
		var k = shaderScript.firstChild;
		while (k) {
			if (k.nodeType == 3) {
				str += k.textContent;
			}
			k = k.nextSibling;
		}

		var shader;
		if (shaderScript.type == "x-shader/x-fragment") {
			shader = gl.createShader(gl.FRAGMENT_SHADER);
		} else if (shaderScript.type == "x-shader/x-vertex") {
			shader = gl.createShader(gl.VERTEX_SHADER);
		} else {
			return null;
		}

		gl.shaderSource(shader, str);
		gl.compileShader(shader);

		if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
			alert(gl.getShaderInfoLog(shader));
			return null;
		}

		return shader;
	},

	initShaders : function () {
		
		gl.enable(gl.DEPTH_TEST);
		
		var fragmentShader //= this.getShader(gl, "per-fragment-lighting-fs");
		var vertexShader //= this.getShader(gl, "per-fragment-lighting-vs");
		
	Scene.loadna("/shader/phong.vert" , function(text,url){
	
		
		let shader = gl.createShader(gl.VERTEX_SHADER)
		gl.shaderSource(shader , text);
		gl.compileShader(shader);
		vertexShader = shader;
		
	});
	
	Scene.loadna("/shader/phong.frag", function(text , url){
		
		let shader = gl.createShader(gl.FRAGMENT_SHADER);
		gl.shaderSource(shader , text);
		gl.compileShader(shader)
		fragmentShader = shader;
		
	
	});
		
		
		shaderProgram = gl.createProgram();
		gl.attachShader(shaderProgram, vertexShader);
		gl.attachShader(shaderProgram, fragmentShader);
		
		gl.linkProgram(shaderProgram);
		
		if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
			

			alert("Could not initialise shaders");
		}

		gl.useProgram(shaderProgram);

		shaderProgram.vertexPositionAttribute = gl.getAttribLocation(shaderProgram, "aVertexPosition");
		gl.enableVertexAttribArray(shaderProgram.vertexPositionAttribute);

		shaderProgram.vertexNormalAttribute = gl.getAttribLocation(shaderProgram, "aVertexNormal");
		gl.enableVertexAttribArray(shaderProgram.vertexNormalAttribute);

		shaderProgram.textureCoordAttribute = gl.getAttribLocation(shaderProgram, "aTextureCoord");
		gl.enableVertexAttribArray(shaderProgram.textureCoordAttribute);

		shaderProgram.pMatrixUniform = gl.getUniformLocation(shaderProgram, "uPMatrix");
		shaderProgram.mvMatrixUniform = gl.getUniformLocation(shaderProgram, "uMVMatrix");
		shaderProgram.nMatrixUniform = gl.getUniformLocation(shaderProgram, "uNMatrix");
		shaderProgram.samplerUniform = gl.getUniformLocation(shaderProgram, "uSampler");
		


		shaderProgram.uMaterialAmbient   = gl.getUniformLocation(shaderProgram, "uMaterialAmbient"); 
		shaderProgram.uMaterialDiffuse   = gl.getUniformLocation(shaderProgram, "uMaterialDiffuse");
		shaderProgram.uMaterialSpecular  = gl.getUniformLocation(shaderProgram, "uMaterialSpecular");
		shaderProgram.uUseCube			 = 	 gl.getUniformLocation(shaderProgram, "uUseCube");
		shaderProgram.uShininess         = gl.getUniformLocation(shaderProgram, "uShininess");

		shaderProgram.uLightAmbient      = gl.getUniformLocation(shaderProgram, "uLightAmbient");
		shaderProgram.uLightDiffuse      = gl.getUniformLocation(shaderProgram, "uLightDiffuse");
		shaderProgram.uLightSpecular     = gl.getUniformLocation(shaderProgram, "uLightSpecular");

		shaderProgram.uLightPosition    = gl.getUniformLocation(shaderProgram, "uLightPosition");
		shaderProgram.useTexturesUniform = gl.getUniformLocation(shaderProgram, "uUseTextures");
		

	},
	initGL : function (canvas) {
        try {
            gl = canvas.getContext("experimental-webgl");
            gl.viewportWidth = canvas.width;
            gl.viewportHeight = canvas.height;
        } catch (e) {
        }
        if (!gl) {
            alert("Could not initialise WebGL, sorry :-(");
        }
    },

	animate : function () {
		var timeNow = new Date().getTime();
		if (lastTime != 0) {
			var elapsed = timeNow - lastTime;

		}
		lastTime = timeNow;
	},

	calculateNormals : function(vs, ind){
        var x=0; 
        var y=1;
        var z=2;
        
        var ns = [];
        for(var i=0;i<vs.length;i=i+3){ //for each vertex, initialize normal x, normal y, normal z
            ns[i+x]=0.0;
            ns[i+y]=0.0;
            ns[i+z]=0.0;
        }
        
        for(var i=0;i<ind.length;i=i+3){ //we work on triads of vertices to calculate normals so i = i+3 (i = indices index)
            var v1 = [];
            var v2 = [];
            var normal = [];	
            //p2 - p1
            v1[x] = vs[3*ind[i+2]+x] - vs[3*ind[i+1]+x];
            v1[y] = vs[3*ind[i+2]+y] - vs[3*ind[i+1]+y];
            v1[z] = vs[3*ind[i+2]+z] - vs[3*ind[i+1]+z];
            //p0 - p1
            v2[x] = vs[3*ind[i]+x] - vs[3*ind[i+1]+x];
            v2[y] = vs[3*ind[i]+y] - vs[3*ind[i+1]+y];
            v2[z] = vs[3*ind[i]+z] - vs[3*ind[i+1]+z];
            //cross product by Sarrus Rule
            normal[x] = v1[y]*v2[z] - v1[z]*v2[y];
            normal[y] = v1[z]*v2[x] - v1[x]*v2[z];
            normal[z] = v1[x]*v2[y] - v1[y]*v2[x];
            for(j=0;j<3;j++){ //update the normals of that triangle: sum of vectors
                ns[3*ind[i+j]+x] =  ns[3*ind[i+j]+x] + normal[x];
                ns[3*ind[i+j]+y] =  ns[3*ind[i+j]+y] + normal[y];
                ns[3*ind[i+j]+z] =  ns[3*ind[i+j]+z] + normal[z];
            }
        }
        //normalize the result
        for(var i=0;i<vs.length;i=i+3){ //the increment here is because each vertex occurs with an offset of 3 in the array (due to x, y, z contiguous values)
        
            var nn=[];
            nn[x] = ns[i+x];
            nn[y] = ns[i+y];
            nn[z] = ns[i+z];
            
            var len = Math.sqrt((nn[x]*nn[x])+(nn[y]*nn[y])+(nn[z]*nn[z]));
            if (len == 0) len = 1.0;
            
            nn[x] = nn[x]/len;
            nn[y] = nn[y]/len;
            nn[z] = nn[z]/len;
            
            ns[i+x] = nn[x];
            ns[i+y] = nn[y];
            ns[i+z] = nn[z];
        }
        
        return ns;
    },
 

    calculateTangents : function(vertices, normals)
    {
        var vs = vertices;
        var ts = [];
        for(var i=0;i<vs.length; i++){
            ts[i]=0.0;
        }
        return ts;
    }

}

	function tick () {
		
		requestAnimFrame(tick);
		if(chess.game_over()){
			window.location.replace("https://www.youtube.com/watch?v=oyFQVZ2h0V8");
			
		}
		Scene.Draw();
		utils.animate();
		
	}

	
	




