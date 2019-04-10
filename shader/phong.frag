    #ifdef GL_ES
    precision highp float;
    #endif
    
    uniform float uShininess;        //shininess
    uniform vec4 uLightAmbient;      //ambient color
    uniform vec4 uLightDiffuse;      //light color
    uniform vec4 uMaterialDiffuse;   //object color
    
    varying vec3 vNormal;
    varying vec3 vLightDir;
    varying vec3 vEyeVec;
    

    uniform bool uUseTextures;
	uniform bool uUseCube;
    varying vec2 vTextureCoord;
    uniform sampler2D uSampler;
	uniform samplerCube uCubeSampler;

    void main(void)
    {
     vec3 L = normalize(vLightDir);
     vec3 N = normalize(vNormal);
     
     //Lambert's cosine law
     float lambertTerm = dot(N,-L);
     
     //Ambient Term
     vec4 Ia = uLightAmbient;
     
     //Diffuse Term
     vec4 Id = vec4(0.0,0.0,0.0,1.0);
     
     //Specular Term
     vec4 Is = vec4(0.0,0.0,0.0,1.0);
     
     if(lambertTerm > 0.0) //only if lambertTerm is positive
     {
      Id = uMaterialDiffuse * lambertTerm; //add diffuse term
      
      vec3 E = normalize(vEyeVec);
      vec3 R = reflect(L, N);
      float specular = pow( max(dot(R, E), 0.0), uShininess);
      
      Is = uLightDiffuse * specular; //add specular term 
     }
     
     //Final color
     vec4 finalColor = Ia + Id + Is;
     finalColor.a = 1.0;
        
        gl_FragColor = finalColor;
        vec4 fragmentColor;
	
    if(uUseTextures) {

		fragmentColor = texture2D(uSampler, vec2(vTextureCoord.s, vTextureCoord.t));
				//texture2D(uSampler, vTextureCoord) * textureCube(uCubeSampler, vNormal);

        } 

	else {
            fragmentColor = vec4(1.0, 1.0, 1.0, 1.0);
        }
        
    gl_FragColor = vec4(fragmentColor.rgb , fragmentColor.a);
    }