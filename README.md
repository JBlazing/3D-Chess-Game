[Link to hosted version](chess.jblazing.com)

This was developed for a computer graphics class back in the fall of 2018.

Originally all logic was done in browser using Chess.js and WebGL, and everything was served statically. 
In an effort to add features that I originally wanted for the project such as online play and match making. 
I decided to adding a Springboot backend to accomplish this and move processing of the game logic to the backend server.
Since the chess engine is javascript based I decided to use GraalVM to execute the javascript code.

Currently, the "AI" for the uses the random chicken method for choosing its next move. 


When it is your turn you can use the A and D keys to cycle through all legal moves and then press enter to make that move.

![Image of the game](https://github.com/JReynoldsUMD/3D-Chess-Game/blob/master/Chess%20Board.JPG?raw=true)

