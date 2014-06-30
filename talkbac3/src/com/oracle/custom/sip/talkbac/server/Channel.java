package com.oracle.custom.sip.talkbac.server;

public enum Channel 
{
	ASC("ASC");
	
	private String val;
	
	private Channel(String val)
	{
		this.val = val;
	}

	public String getVal() {
		return val;
	}

	public void setVal(String val) {
		this.val = val;
	}
	
	
}
