import json
import string
import pprint

'''
f = open('chesspos.txt' , 'r')

lines = f.readlines()

lines = [line.rstrip().split(",") for line in lines]





for i,p in enumerate(lines):

	for j,x in enumerate(p[1:]):
		
		lines[i][j+1] = float(x)
		
		
'''

js = {}
alpha = list(string.ascii_lowercase)[0:8]

step = 12.5

piece = [-44.0,3.5,44.0]
for i in range(0,8):
	
	for j , coord in enumerate(alpha):
		tmp = [ piece[0]-step*j , piece[1], piece[2]-step*i]
		
		key = "{}{}".format(coord,8-i)
		
		js[key] = tmp;
		
		
			
pp = pprint.PrettyPrinter(indent=4)

pp.pprint(js)			

with open('boxpos.json', 'w') as fp:
    json.dump(js, fp)