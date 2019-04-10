function loader(filename , call_back){
    var request = new XMLHttpRequest();
    var resource = "http://"+document.domain+ filename
    request.open("GET",filename);
    request.onreadystatechange = function() {
        console.info(request.readyState +' - '+request.status); 
      if (request.readyState == 4) {
        let json  = JSON.parse(request.responseText)
        console.error(json)
	    if(request.status == 200) { //OK
			
			call_back(filename,json);
		 }
		else if (document.domain.length == 0 && request.status == 0){ //OK but local, no web server
            call_back(filename,json);
        }
        else{
            alert ('There was a problem loading the file :' + filename);
            alert ('HTML error code: ' + request.status);
		}
	  }
    }
    request.send();
}

