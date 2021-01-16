package com.lushprojects.circuitjs1.client;

public class BetterStringTokenizer {

    String delim;
    String text;
    int pos;
    int tlen;
    String token, tokenPreserveCase;
    
    public BetterStringTokenizer(String text_, String delim_) {
	text  = text_;
	delim = delim_;
	pos = 0;
	tlen = text.length();
	while (pos < tlen && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t'))
	    pos++;
    }

    String nextToken() {
	if (pos == tlen) {
	    token = tokenPreserveCase = "";
	    return token;
	}
	int i = pos + 1;
	int c = text.charAt(pos);
	if (delim.indexOf(c) < 0) {
	    while (i < tlen && delim.indexOf(text.charAt(i)) < 0)
		i++;
	}
	tokenPreserveCase = text.substring(pos, i);
	token = tokenPreserveCase.toLowerCase();
	pos = i;
	while (pos < tlen && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t'))
	    pos++;
	return token;
    }

    String nextTokenPreserveCase() {
	nextToken();
	return tokenPreserveCase;
    }
    
    void setDelimiters(String d) {
	delim = d;
    }
    
    boolean hasMoreTokens() {
	return pos < tlen;
    }
}
