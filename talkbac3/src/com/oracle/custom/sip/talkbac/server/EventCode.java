package com.oracle.custom.sip.talkbac.server;

public enum EventCode 
{
	ORIGIN_CONNECTING("202","Connecting originating user"),
	DEST_CONNECTING("222","Connecting destination user"),
	ORIGIN_CONNECTED("203","Connected originating user"),
	DEST_CONNECTED("223","Connected destination user"),
	ORIGIN_DISCONNECTED("220","Originating user terminated"),
	DEST_DISCONNECTED("240","Destination user terminated"),
	ORIGIN_INAVLID("4041","Originating user address Invalid/Not available in the network"),
	DEST_INVALID("4042","Destination user address Invalid/Not available in the network"),
	ORIGIN_BUSY("4861","Originating user Busy can’t take call"),
	DEST_BUSY("4862","Destination user Busy can’t take call"),
	SERVER_ERROR("500", "Internal Server error"),
	SUCCESS("200","Success"),
	AUTHORIZED("201", "User authorized"),
	AUTH_FAIL("401","User authorization failed"),
	DIGIT_SENT("250", "dtmf digits sent"),
	TIME_OUT("4050", "Call duration limit exceeded, disconnecting call");
	
	private String code;
	private String reason;
	
	private EventCode(String code, String reason)
	{
		this.code = code;
		this.reason = reason;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
	
}
