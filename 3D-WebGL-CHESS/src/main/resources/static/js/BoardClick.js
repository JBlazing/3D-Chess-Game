/**
 * Board Click
 *
 * Turns clicks on the canvas into board squares.
 *
 * Pieces are picked exactly: the scene is drawn into an offscreen framebuffer with every object
 * in its own flat colour, restricted by the scissor test to the one pixel under the mouse, and
 * that pixel is read back. When the pixel shows the board instead of a piece, a ray from the
 * camera through the mouse is intersected with the board surface to find the square.
 */
var BoardClick = {

	BOARD_TOP : 3.1,        // y of the playing surface in world space
	SQUARE : 12.5,          // square size; a1 is at x = 43.5, z = -43.5, files run toward -x, ranks toward +z
	CLICK_SLOP : 6,         // pixels a press may move and still count as a click rather than a camera drag

	canvas : null,
	down : null,
	prg : null,
	fbo : null,
	fboSize : [0, 0],
	hoverPending : null,

	init : function(canvas, onSquare, onHover){
		this.canvas = canvas;
		var self = this;

		canvas.addEventListener("pointerdown", function(ev){
			if(ev.button === 0) self.down = { x : ev.clientX, y : ev.clientY };
		});
		// a press interrupted by losing focus is not a click
		window.addEventListener("blur", function(){ self.down = null; });
		document.addEventListener("visibilitychange", function(){ if(document.hidden) self.down = null; });
		canvas.addEventListener("pointerup", function(ev){
			var down = self.down;
			self.down = null;
			if(!down || ev.button !== 0) return;
			if(Math.hypot(ev.clientX - down.x, ev.clientY - down.y) > self.CLICK_SLOP) return;
			onSquare(self.squareAt(ev.clientX, ev.clientY));
		});
		canvas.addEventListener("pointermove", function(ev){
			if(ev.buttons !== 0) return;
			// at most one picking pass per frame
			var pending = self.hoverPending;
			self.hoverPending = { x : ev.clientX, y : ev.clientY };
			if(pending) return;
			requestAnimFrame(function(){
				var p = self.hoverPending;
				self.hoverPending = null;
				onHover(self.squareAt(p.x, p.y));
			});
		});
	},

	squareCenter : function(square){
		var file = square.charCodeAt(0) - 97;
		var rank = parseInt(square[1], 10);
		return [43.5 - file * this.SQUARE, -43.5 + (rank - 1) * this.SQUARE];
	},

	/* ---------- exact piece picking ---------- */

	initProgram : function(){
		var vs = "attribute vec3 aPos; uniform mat4 uP; uniform mat4 uMV;" +
		         "void main(){ gl_Position = uP * uMV * vec4(aPos, 1.0); }";
		var fs = "precision mediump float; uniform vec4 uColor;" +
		         "void main(){ gl_FragColor = uColor; }";
		var prg = gl.createProgram();
		[[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]].forEach(function(s){
			var shader = gl.createShader(s[0]);
			gl.shaderSource(shader, s[1]);
			gl.compileShader(shader);
			gl.attachShader(prg, shader);
		});
		gl.linkProgram(prg);
		prg.aPos = gl.getAttribLocation(prg, "aPos");
		prg.uP = gl.getUniformLocation(prg, "uP");
		prg.uMV = gl.getUniformLocation(prg, "uMV");
		prg.uColor = gl.getUniformLocation(prg, "uColor");
		this.prg = prg;
	},

	ensureFramebuffer : function(w, h){
		if(this.fbo && this.fboSize[0] === w && this.fboSize[1] === h) return;
		if(!this.fbo){
			this.fbo = gl.createFramebuffer();
			this.fboColor = gl.createRenderbuffer();
			this.fboDepth = gl.createRenderbuffer();
		}
		gl.bindRenderbuffer(gl.RENDERBUFFER, this.fboColor);
		gl.renderbufferStorage(gl.RENDERBUFFER, gl.RGBA4, w, h);
		gl.bindRenderbuffer(gl.RENDERBUFFER, this.fboDepth);
		gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT16, w, h);
		gl.bindFramebuffer(gl.FRAMEBUFFER, this.fbo);
		gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.RENDERBUFFER, this.fboColor);
		gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, this.fboDepth);
		gl.bindFramebuffer(gl.FRAMEBUFFER, null);
		gl.bindRenderbuffer(gl.RENDERBUFFER, null);
		this.fboSize = [w, h];
	},

	/** The scene object drawn at a canvas pixel, or null for background. */
	objectAt : function(clientX, clientY){
		if(!this.prg) this.initProgram();
		var w = this.canvas.width, h = this.canvas.height;
		var rect = this.canvas.getBoundingClientRect();
		var px = Math.floor((clientX - rect.left) * w / rect.width);
		var py = Math.floor(h - (clientY - rect.top) * h / rect.height) - 1;
		if(px < 0 || py < 0 || px >= w || py >= h) return null;

		this.ensureFramebuffer(w, h);

		// the main shaders keep their attribute arrays enabled; switch them off for this pass
		var maxAttribs = gl.getParameter(gl.MAX_VERTEX_ATTRIBS), enabled = [];
		for(var i = 0; i < maxAttribs; i++){
			if(gl.getVertexAttrib(i, gl.VERTEX_ATTRIB_ARRAY_ENABLED)){
				enabled.push(i);
				gl.disableVertexAttribArray(i);
			}
		}
		var blending = gl.isEnabled(gl.BLEND);

		gl.bindFramebuffer(gl.FRAMEBUFFER, this.fbo);
		gl.viewport(0, 0, w, h);
		gl.enable(gl.SCISSOR_TEST);
		gl.scissor(px, py, 1, 1);
		gl.disable(gl.BLEND);
		gl.enable(gl.DEPTH_TEST);
		gl.clearColor(0, 0, 0, 0);
		gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

		gl.useProgram(this.prg);
		gl.enableVertexAttribArray(this.prg.aPos);
		gl.uniformMatrix4fv(this.prg.uP, false, Scene.transforms.pMatrix);

		// same transforms as Scene.Draw: view matrix, then the object's offset
		var view = Scene.camera.getViewTransform();
		for(var i = 1; i < Scene.objects.length; i++){
			var obj = Scene.objects[i];
			if(!obj.isVisible || !obj.vertexBuff || obj.name == "SkyBox") continue;
			var mv = mat4.create();
			mat4.set(view, mv);
			mat4.translate(mv, obj.location);
			gl.uniformMatrix4fv(this.prg.uMV, false, mv);
			// object index as a colour; 4 bits per channel in RGBA4 is plenty for < 4096 objects
			gl.uniform4f(this.prg.uColor, (i & 15) / 15, ((i >> 4) & 15) / 15, ((i >> 8) & 15) / 15, 1.0);
			gl.bindBuffer(gl.ARRAY_BUFFER, obj.vertexBuff);
			gl.vertexAttribPointer(this.prg.aPos, 3, gl.FLOAT, false, 0, 0);
			gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, obj.IdxBuffer);
			gl.drawElements(gl.TRIANGLES, obj.IdxBuffer.numItems, gl.UNSIGNED_SHORT, 0);
		}

		var pixel = new Uint8Array(4);
		gl.readPixels(px, py, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel);

		// restore what Scene.Draw relies on; it resets program, viewport and clear colour itself each frame
		gl.disableVertexAttribArray(this.prg.aPos);
		enabled.forEach(function(i){ gl.enableVertexAttribArray(i); });
		gl.disable(gl.SCISSOR_TEST);
		if(blending) gl.enable(gl.BLEND);
		gl.bindFramebuffer(gl.FRAMEBUFFER, null);
		gl.bindBuffer(gl.ARRAY_BUFFER, null);
		gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, null);
		gl.viewport(0, 0, gl.viewportWidth, gl.viewportHeight);
		gl.clearColor(0.0, 0.0, 0.0, 1.0);

		if(pixel[3] === 0) return null;
		var q = function(v){ return Math.round(v / 17); }; // 8-bit readback of a 4-bit channel
		var index = q(pixel[0]) | (q(pixel[1]) << 4) | (q(pixel[2]) << 8);
		return Scene.objects[index] || null;
	},

	/* ---------- board surface ---------- */

	/** World-space ray through a point on the canvas, as {o: origin, d: direction}. */
	ray : function(clientX, clientY){
		var rect = this.canvas.getBoundingClientRect();
		var nx = ((clientX - rect.left) / rect.width) * 2 - 1;
		var ny = 1 - ((clientY - rect.top) / rect.height) * 2;

		var inv = mat4.create();
		mat4.multiply(Scene.transforms.pMatrix, Scene.camera.getViewTransform(), inv);
		mat4.inverse(inv);

		var near = mat4.multiplyVec4(inv, [nx, ny, -1, 1], [0, 0, 0, 0]);
		var far  = mat4.multiplyVec4(inv, [nx, ny,  1, 1], [0, 0, 0, 0]);
		var o = [near[0] / near[3], near[1] / near[3], near[2] / near[3]];
		var f = [far[0] / far[3], far[1] / far[3], far[2] / far[3]];
		return { o : o, d : [f[0] - o[0], f[1] - o[1], f[2] - o[2]] };
	},

	boardSquareAt : function(clientX, clientY){
		var ray = this.ray(clientX, clientY);
		if(Math.abs(ray.d[1]) < 1e-12) return null;
		var t = (this.BOARD_TOP - ray.o[1]) / ray.d[1];
		if(t <= 0) return null;
		var x = ray.o[0] + t * ray.d[0], z = ray.o[2] + t * ray.d[2];
		var file = Math.round((43.5 - x) / this.SQUARE);
		var rank = Math.round((z + 43.5) / this.SQUARE) + 1;
		if(file < 0 || file > 7 || rank < 1 || rank > 8) return null;
		return String.fromCharCode(97 + file) + rank;
	},

	/** The square under a point on the canvas, or null when the click misses the board. */
	squareAt : function(clientX, clientY){
		if(!gl || !Scene.camera || !Scene.transforms) return null;
		var obj = this.objectAt(clientX, clientY);
		if(obj && obj.cord) return obj.cord;   // a piece
		if(!obj) return null;                  // sky
		return this.boardSquareAt(clientX, clientY);
	}
}
