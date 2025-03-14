var Animation = {
	
	ISTEPS : 50,
	
	createAnimation : function(start , end){
		
		let mid = [ (start[0] + end[0]) / 3 , 25, (start[2] + end[2]) / 3 ]
		let mid2 = [ (start[0] + end[0]) / 2 , 50 , (start[2] + end[2]) / 2 ]
		console.log(end);
		return this.doBSplineInterpolation([ start , mid , mid2 ,   end]);
		
	},
	
	
	doBSplineInterpolation : function (CONTROL_POINTS){
    
		var position = [];
		
		var N = CONTROL_POINTS.length - 1;
		var P = 3;  //Degree
		var U = []; //Knot Vector
		var M = N + P + 1; //number of elements in the knot vector
		var deltaKnot = 1/(M-(2*P));
		
		//Creating the knot vector (clamped):
		//http://web.mit.edu/hyperbook/Patrikalakis-Maekawa-Cho/node17.html
		for (var i = 0; i <= P; i++){
			U.push(0);
		}
		
		var v = deltaKnot;
		for (var i = P+1; i< M-P+1; i++){
			U.push(v);
			v += deltaKnot;
		}
		
		for (var i = M-P+1; i<= M; i++){
			U.push(1);
		}
		
		
			
		// Bo function
		function No(u,i){
			if (U[i] <= u && u < U[i+1]){
				return 1;
			}
			else {
				return 0;
			}
		};
		
		// Bp function
		function Np(u,i,p){
			var A = 0;
			var B = 0;
			if (p-1 == 0) { 
				A = No(u,i);
				B = No(u, i+1);
			} else { 
				A = Np(u,i,p-1);
				B = Np(u,i+1, p-1);
			}
			
			var coeffA = 0;
			var coeffB = 0;
			if ( U[i+p] - U[i] != 0 ) 	  {coeffA = (u - U[i])/(U[i+p] -U[i]);}
			if ( U[i+p+1] - U[i+1] != 0 ) {coeffB = (U[i+p+1] - u)/(U[i+p+1] - U[i+1]);}
			return coeffA*A + coeffB*B;
		};
		
		function C(t){
			var result = [];
			for (var j = 0; j <3; j++){         //iterate over axes
				var sum = 0;
				for (var i = 0; i <= N; i++){    //iterate over control points
					sum += CONTROL_POINTS[i][j] * Np(t,i,P);
				}
				result[j] = sum;
			}
			return result;
		};
		
		
		var dT = 1/this.ISTEPS;
		var t = 0;
		do{
			position.push(C(t));
			t += dT;
		} while(t<1.0);
		position.push(CONTROL_POINTS[CONTROL_POINTS.length-1]);
		console.log(position);
		return position;
	}



}