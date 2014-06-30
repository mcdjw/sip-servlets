package com.oracle.custom.sip.talkbac.server;

public enum EventType 
{
	REGISTRATION("regisrtation"),
	CALL_CONTROL("call-control");
	
	private String value;
	
	private EventType(String str)
	{
		value = str;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
	
	
}
