var Scene = {
		
		
		
		objects : [],
		camera : null,
		interactor: null,
		transforms: null,
		picker : null,
		boxs : [],
		init : function(){
			
			Scene.camera = new Camera(1);
			this.camera.goHome([0,0,125]);
			this.interactor = new CameraInteractor(this.camera , canvas);
			this.camera.changeAzimuth(179);
			this.transforms = new SceneTransforms(this.camera);
			this.transforms.init();
			/*
			this.picker = new Picker(canvas);
			picker.processHitsCallback = processHits;
			picker.addHitCallback      = addHit;
			picker.removeHitCallback   = removeHit;
			picker.hitPropertyCallback = hitProperty;
			picker.moveCallback        = movePickedObjects;
			*/
		},
		
		loadCube:
			function(object){
				
				
				let obj = {
					'name' : "SkyBox" , 
					'side' : 3,
					'location' : [0,0,0],
					'normals' : gl.createBuffer() , 
					'vertexBuff': gl.createBuffer() , 
					'IdxBuffer':gl.createBuffer(),
					'textBuff' : gl.createBuffer()
					
				}
				
				gl.bindBuffer(gl.ARRAY_BUFFER, obj.textBuff);
				gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(object.texture_coords), gl.STATIC_DRAW);
				obj.textBuff.itemSize = 2;
				obj.textBuff.numItems = object.texture_coords.length / 2;
				
				
				gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, obj.IdxBuffer);
				gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(object.indices), gl.STATIC_DRAW);
				obj.IdxBuffer.itemSize = 1;
				obj.IdxBuffer.numItems = object.indices.length ;
				
				gl.bindBuffer(gl.ARRAY_BUFFER, obj.vertexBuff);
				gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(object.vertices), gl.STATIC_DRAW);
				obj.vertexBuff.itemSize = 3;
				obj.vertexBuff.numItems = object.vertices.length / 3;
				
				
				let n = new Float32Array(utils.calculateNormals(object.vertices, object.indices))
				
				gl.bindBuffer(gl.ARRAY_BUFFER, obj.normals);
				gl.bufferData(gl.ARRAY_BUFFER, n , gl.STATIC_DRAW);
				obj.normals.itemSize = 3;
				obj.normals.numItems = n.length / 3;
				console.log(obj.name);
				Scene.objects.push(obj);
				
				console.log(boxPos);
				
				Object.keys(boxPos).forEach(function(pos){
					boxPos[pos][0] += 87.5
					let obj = {
						'name': pos,
						'location': boxPos[pos],
						'normals' : gl.createBuffer() , 
						'vertexBuff': gl.createBuffer() , 
						'IdxBuffer':gl.createBuffer(),
						'diffuse' : [1.0,1.0,1.0,.4],
						'isVisible' : false
						
					};
									
					gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, obj.IdxBuffer);
					gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(object.indices), gl.STATIC_DRAW);
					obj.IdxBuffer.itemSize = 1;
					obj.IdxBuffer.numItems = object.indices.length ;
					
					gl.bindBuffer(gl.ARRAY_BUFFER, obj.vertexBuff);
					gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(object.vertices), gl.STATIC_DRAW);
					obj.vertexBuff.itemSize = 3;
					obj.vertexBuff.numItems = object.vertices.length / 3;
					
					
					let n = new Float32Array(utils.calculateNormals(object.vertices, object.indices))
					
					gl.bindBuffer(gl.ARRAY_BUFFER, obj.normals);
					gl.bufferData(gl.ARRAY_BUFFER, n , gl.STATIC_DRAW);
					obj.normals.itemSize = 3;
					obj.normals.numItems = n.length / 3;
					Scene.boxs.push(obj);
				})
				
				console.log(Scene.boxs)
			},
		
		loadModels:
			function(models){
				console.log(models)
				//models = JSON.parse(models)
				models.meshes.forEach(function(model , i)
				{
				
					var vertices = new Float32Array(model.vertices);
					var normal = new Float32Array(model.normals);
					var faces = new Uint16Array([].concat.apply([] , model.faces));
					
					
					var textures = new Float32Array(model.texturecoords[0])
					
					
					let obj = { 'name': models.rootnode.children[i].name ,
								'side' : figureSide(models.rootnode.children[i].name),
								'location' : [0,0,0],
								'Selected' : false ,
								'normals' : gl.createBuffer() , 
								'vertexBuff': gl.createBuffer() , 
								'IdxBuffer':gl.createBuffer(),
								'textBuff' : gl.createBuffer(),
								'isVisible' : true
							  };
					try{

					let keys = Object.keys(Pos[obj.name]);
					var key = null;
					console.log( obj.name ,keys)
					for(let i = 0 ; i < keys.length ; i++){
						let space = Pos[obj.name][keys[i]]
						if(space[0] == 0.0 && space[1] == 0.0 && space[2] ==0.0){
							key = keys[i]
							break;
						}
						
					}
					if(key != null){
						obj.cord = key;
					}
					}catch(err){
						console.log(obj.name);
					}
					
					if(obj.name == "Bishop003"){
							obj.name = "Bishop002";
							obj.cord = "c1";
					}else if(obj.name == "Bishop002"){
							obj.name = "Bishop003";
							obj.cord = "f1"
						
					}
					
					


					gl.bindBuffer(gl.ARRAY_BUFFER, obj.normals);
					gl.bufferData(gl.ARRAY_BUFFER, normal, gl.STATIC_DRAW);
					obj.normals.itemSize = 3;
					obj.normals.numItems = normal.length / 3;

					
					gl.bindBuffer(gl.ARRAY_BUFFER, obj.textBuff);
					gl.bufferData(gl.ARRAY_BUFFER, textures, gl.STATIC_DRAW);
					obj.textBuff.itemSize = 2;
					obj.textBuff.numItems = textures.length / 2;
					

					gl.bindBuffer(gl.ARRAY_BUFFER, obj.vertexBuff);
					gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
					obj.vertexBuff.itemSize = 3;
					obj.vertexBuff.numItems = vertices.length / 3;

					
					gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, obj.IdxBuffer);
					gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, faces, gl.STATIC_DRAW);
					obj.IdxBuffer.itemSize = 1;
					obj.IdxBuffer.numItems = faces.length ;

					Scene.objects.push(obj)
				});
				var mistake = Scene.objects.filter(x => x.cord == "Bishop003")[0];
				var mistake2 = Scene.objects.filter(x=> x.name == "Bishop002")[0];

				
		},

		load: 
			function(url , func){
				new Promise((resolve , reject) => {
					let resource = window.location.href + url;
					let xhr = new XMLHttpRequest();
					xhr.open("GET", url);
					xhr.onload = () => resolve(JSON.parse(xhr.responseText))
					xhr.onerror = () => reject(xhr.statusText);
					xhr.send();
				}).then(func);
			},
			
		loadna:
			function(url , func)
			{
				let xhr = new XMLHttpRequest();
				let resource = window.location.href + url;
				xhr.open("GET" , resource , false);
				xhr.onload = () => func(xhr.responseText)
				xhr.send();
			},

		
		
		Draw : function() {

			gl.viewport(0, 0, gl.viewportWidth, gl.viewportHeight);
			gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
			
			//This determines the depth distance on wether it will render of not
			
			renderSkyBox();
			
			this.transforms.prg = shaderProgram;
			gl.useProgram(shaderProgram);

			gl.depthFunc(gl.LESS);

			initLights();
			
			//console.log(this.transforms.pMatrix);
			gl.uniform1i(shaderProgram.showSpecularHighlightsUniform, true);
			
			
			gl.uniform1i(shaderProgram.useLightingUniform, true);
			gl.uniform3f(shaderProgram.ambientColorUniform,.2,.2,.2);
			
			gl.uniform1i(shaderProgram.useTexturesUniform, true);
			gl.uniform1i(shaderProgram.uUseCube , false);
			//gl.unifom1i(shaderProgram.uUseCube , true);
			
			this.transforms.updatePerspective();
			//updateCamera();

			
			for(let i = 1 ; i < this.objects.length ; i++)
			{
				let obj = this.objects[i];
				if(!obj.isVisible) continue;
				this.transforms.calculateModelView();
				this.transforms.push();


				if(obj.hasOwnProperty("animation")){
				
					obj.location = obj.animation.shift();
					
					if(obj.animation.length == 0 ){
						console.log(obj.location)
						obj.location[1] = 0.0;
						delete obj.animation 
						let animation = anime.shift();
						let _obj = Scene.objects.filter( obj => obj.name == animation.name)[0]
						let end = [_obj.location[0] + animation.trans[0] , 0.0 ,  _obj.location[2] + animation.trans[2]];
						//console.log(end)
						let postions = Animation.createAnimation(_obj.location , end);
						
						postions.pop();
						_obj.animation = postions;
					}
					
				}	
				
				mat4.translate(this.transforms.mvMatrix , obj.location)
			
				if(obj.name == "SkyBox"){
					mat4.scale(this.transforms.mvMatrix , [300.0,300.0,300.0]);
					
				}
				
				this.transforms.setMatrixUniforms();
				this.transforms.pop();
				
				
				

				
				if(obj.hasOwnProperty("side") && obj.name != "SkyBox"){
					gl.bindTexture(gl.TEXTURE_2D , Texture.textures[obj.side]);
				}
				/*
				if(obj.name == "SkyBox")
				{
					
					gl.enableVertexAttribArray(shaderProgram.aTextureCoord);
		
		
					gl.activeTexture(gl.TEXTURE0);
					//gl.bindTexture(gl.TEXTURE_2D, Texture.textures[obj.side]);
					gl.uniform1i(shaderProgram.uSampler, 0);
					
					gl.activeTexture(gl.TEXTURE1);
					gl.bindTexture(gl.TEXTURE_CUBE_MAP, Texture.skybox);
					gl.uniform1i(shaderProgram.uCubeSampler, 1);
					
				*/
				//}else{
					if(!obj.Selected){
						gl.uniform1i(shaderProgram.useTexturesUniform, true);
						gl.activeTexture(gl.TEXTURE0);
					gl.activeTexture(gl.TEXTURE0);
					gl.bindTexture(gl.TEXTURE_2D, Texture.textures[obj.side]);
					gl.uniform1i(shaderProgram.uSampler, 0);
					
					//gl.activeTexture(gl.TEXTURE1);
					//gl.bindTexture(gl.TEXTURE_CUBE_MAP, Texture.skybox);
					//gl.uniform1i(shaderProgram.uCubeSampler, 1);
				
				//}
					
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
					gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
					gl.uniform1i(shaderProgram.samplerUniform, 0);
				}
				else{
					
					gl.uniform1i(shaderProgram.useTexturesUniform, false);
				}
				gl.uniform1f(shaderProgram.materialShininessUniform, 32.0);
				
				//console.log(obj)
				gl.bindBuffer(gl.ARRAY_BUFFER, obj.vertexBuff);
				gl.vertexAttribPointer(shaderProgram.vertexPositionAttribute, obj.vertexBuff.itemSize, gl.FLOAT, false, 0, 0);
				
				gl.bindBuffer(gl.ARRAY_BUFFER, obj.textBuff);
				gl.vertexAttribPointer(shaderProgram.textureCoordAttribute, obj.textBuff.itemSize, gl.FLOAT, false, 0, 0);
				
				gl.bindBuffer(gl.ARRAY_BUFFER, obj.normals);
				gl.vertexAttribPointer(shaderProgram.vertexNormalAttribute, obj.normals.itemSize, gl.FLOAT, false, 0, 0);

				gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, obj.IdxBuffer);
				//console.log(obj.name)
				gl.drawElements(gl.TRIANGLES, obj.IdxBuffer.numItems, gl.UNSIGNED_SHORT, 0);
			//}
				
			}
			gl.uniform1i(shaderProgram.useTexturesUniform, false);
			Scene.boxs.forEach(function(box){
				
				if(box.isVisible){
				
				Scene.transforms.calculateModelView();
				Scene.transforms.push();
				mat4.translate(Scene.transforms.mvMatrix , box.location);
				mat4.scale(Scene.transforms.mvMatrix , [7.5,7.5,7.5])
				Scene.transforms.setMatrixUniforms();
				Scene.transforms.pop();
				
				gl.bindBuffer(gl.ARRAY_BUFFER, box.vertexBuff);
				gl.vertexAttribPointer(shaderProgram.vertexPositionAttribute, box.vertexBuff.itemSize, gl.FLOAT, false, 0, 0);
				
				gl.bindBuffer(gl.ARRAY_BUFFER, box.normals);
				gl.vertexAttribPointer(shaderProgram.vertexNormalAttribute, box.normals.itemSize, gl.FLOAT, false, 0, 0);
				
				gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, box.IdxBuffer);
				//console.log(obj.name)
				gl.drawElements(gl.TRIANGLES, box.IdxBuffer.numItems, gl.UNSIGNED_SHORT, 0);
				
				}
			})
			
			
			Scene.wizard.forEach(function(box){
				
				Scene.transforms.calculateModelView();
				Scene.transforms.push();
				mat4.translate(Scene.transforms.mvMatrix , box.location);
				mat4.translate(Scene.transforms.mvMatrix , [0.0,25.0,75.0]);
				mat4.scale(Scene.transforms.mvMatrix , [100.0,100.0,100.0])
				Scene.transforms.setMatrixUniforms();
				Scene.transforms.pop();
				
				gl.bindBuffer(gl.ARRAY_BUFFER, box.vertexBuff);
				gl.vertexAttribPointer(shaderProgram.vertexPositionAttribute, box.vertexBuff.itemSize, gl.FLOAT, false, 0, 0);
				
				gl.bindBuffer(gl.ARRAY_BUFFER, box.normals);
				gl.vertexAttribPointer(shaderProgram.vertexNormalAttribute, box.normals.itemSize, gl.FLOAT, false, 0, 0);
				
				gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, box.IdxBuffer);
				//console.log(obj.name)
				gl.drawElements(gl.TRIANGLES, box.IdxBuffer.numItems, gl.UNSIGNED_SHORT, 0);
				
			});

		}

}
